use byteorder::{NetworkEndian, WriteBytesExt};
use rand::Rng;
use socket2::{Domain, Protocol, Socket, Type};
use tokio::net::UdpSocket;

use std::{
    io::{self, Write},
    net::{Ipv4Addr, SocketAddr},
    time::Duration,
};

const SEND_RETRY_ATTEMPTS: u32 = 10;

/// Pinger errors
#[derive(thiserror::Error, Debug)]
pub enum Error {
    /// Failed to open raw socket
    #[error("Failed to open ICMP socket")]
    Open(#[source] io::Error),

    /// Failed to read from raw socket
    #[error("Failed to read ICMP socket")]
    Read(#[source] io::Error),

    /// Failed to set socket options
    #[error("Failed to set socket options")]
    SocketOp(#[source] io::Error),

    /// Failed to write to raw socket
    #[error("Failed to write to socket")]
    Write(#[source] io::Error),

    /// Failed to convert to tokio socket
    #[error("Failed to convert to tokio socket")]
    ConvertSocket(#[source] io::Error),

    /// Failed to get device index
    #[cfg(target_os = "macos")]
    #[error("Failed to obtain device index")]
    DeviceIdx(nix::errno::Errno),

    /// Failed to bind socket to device by index
    #[cfg(target_os = "macos")]
    #[error("Failed to bind socket to device by index")]
    BindSocketByDevice(io::Error),

    /// ICMP buffer too small
    #[error("ICMP message buffer too small")]
    BufferTooSmall,
}

type Result<T> = std::result::Result<T, Error>;

pub struct Pinger {
    sock: UdpSocket,
    addr: SocketAddr,
    id: u16,
    seq: u16,
}

impl Pinger {
    /// Creates a new `Pinger`.
    pub fn new(
        addr: Ipv4Addr,
        #[cfg(any(target_os = "linux", target_os = "macos"))] interface_name: String,
    ) -> Result<Self> {
        let addr = SocketAddr::new(addr.into(), 0);
        let sock = Socket::new(
            Domain::IPV4,
            if cfg!(target_os = "android") {
                Type::DGRAM
            } else {
                Type::RAW
            },
            Some(Protocol::ICMPV4),
        )
        .map_err(Error::Open)?;
        sock.set_nonblocking(true).map_err(Error::Open)?;

        #[cfg(target_os = "linux")]
        sock.bind_device(Some(interface_name.as_bytes()))
            .map_err(Error::SocketOp)?;

        #[cfg(target_os = "macos")]
        Self::set_device_index(&sock, &interface_name)?;

        let sock =
            UdpSocket::from_std(std::net::UdpSocket::from(sock)).map_err(Error::ConvertSocket)?;

        Ok(Self {
            sock,
            addr,
            id: rand::random(),
            seq: 0,
        })
    }

    #[cfg(target_os = "macos")]
    fn set_device_index(socket: &Socket, interface_name: &str) -> Result<()> {
        let index = nix::net::if_::if_nametoindex(interface_name).map_err(Error::DeviceIdx)?;
        // Asserting that `index` is non-zero since otherwise `if_nametoindex` would have return
        // an error
        socket
            .bind_device_by_index_v4(std::num::NonZeroU32::new(index))
            .map_err(Error::BindSocketByDevice)?;

        Ok(())
    }

    async fn send_ping_request(&mut self, message: &[u8], destination: SocketAddr) -> Result<()> {
        let mut tries = 0;
        loop {
            let Err(error) = self.sock.send_to(message, destination).await else {
                return Ok(());
            };
            if tries >= SEND_RETRY_ATTEMPTS || !should_retry_send(&error) {
                return Err(Error::Write(error));
            }

            tokio::time::sleep(Duration::from_secs(1)).await;
            tries += 1;
        }
    }

    fn construct_icmpv4_packet(&mut self, buffer: &mut [u8]) -> Result<()> {
        if !construct_icmpv4_packet_inner(buffer, self) {
            return Err(Error::BufferTooSmall);
        }
        Ok(())
    }
}

#[cfg(windows)]
fn should_retry_send(err: &io::Error) -> bool {
    // Winsock error for when there is no route
    // NOTE: It's unclear if we need to check this on Windows anymore, or why specifically on Windows
    const WSAEHOSTUNREACH: i32 = 10065;
    err.raw_os_error() == Some(WSAEHOSTUNREACH)
}

#[cfg(unix)]
fn should_retry_send(_err: &io::Error) -> bool {
    false
}

#[async_trait::async_trait]
impl super::Pinger for Pinger {
    async fn send_icmp(&mut self) -> Result<()> {
        let mut message = [0u8; 50];
        self.construct_icmpv4_packet(&mut message)?;
        self.send_ping_request(&message, self.addr).await
    }
}

trait PayloadWriter {
    fn packet_id(&mut self) -> u16;
    fn sequence_num(&mut self) -> u16;
    fn write_payload(&mut self, buffer: &mut [u8]);
}

impl PayloadWriter for Pinger {
    fn packet_id(&mut self) -> u16 {
        self.id
    }

    fn sequence_num(&mut self) -> u16 {
        let seq = self.seq;
        self.seq += 1;
        seq
    }

    fn write_payload(&mut self, buffer: &mut [u8]) {
        rand::thread_rng().fill(buffer);
    }
}

fn construct_icmpv4_packet_inner(
    buffer: &mut [u8],
    packet_writer: &mut impl PayloadWriter,
) -> bool {
    const ICMP_CHECKSUM_OFFSET: usize = 2;
    if buffer.len() < 14 {
        return false;
    }

    let mut writer = &mut buffer[..];
    // ICMP type - Echo (ping) request
    writer.write_u8(0x08).unwrap();
    // Code - 0
    writer.write_u8(0x00).unwrap();
    // Checksum -filled in later
    writer.write_u16::<NetworkEndian>(0x000).unwrap();
    // packet ID
    writer
        .write_u16::<NetworkEndian>(packet_writer.packet_id())
        .unwrap();
    // packet sequence number
    writer
        .write_u16::<NetworkEndian>(packet_writer.sequence_num())
        .unwrap();
    // payload
    packet_writer.write_payload(writer);

    let checksum = internet_checksum::checksum(buffer);
    (&mut buffer[ICMP_CHECKSUM_OFFSET..])
        .write_all(&checksum)
        .unwrap();

    true
}

#[cfg(test)]
mod test {
    use super::*;

    struct TestPayload {}

    impl PayloadWriter for TestPayload {
        fn packet_id(&mut self) -> u16 {
            0x1dcd
        }

        fn sequence_num(&mut self) -> u16 {
            0x0001
        }

        fn write_payload(&mut self, mut buffer: &mut [u8]) {
            let _ = buffer.write(&[
                0xb6, 0xe0, 0x87, 0x60, 0x00, 0x00, 0x00, 0x00, 0x97, 0xad, 0x09, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b,
                0x1c, 0x1d, 0x1e, 0x1f, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29,
                0x2a, 0x2b, 0x2c, 0x2d, 0x2e, 0x2f, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
            ]);
        }
    }

    #[test]
    fn test_icmpv4_packet() {
        // captured from a plain `ping -4 127.1`
        let expected_packet = [
            // ICMP type - echo request
            0x08, // Code 0
            0x00, // checksum
            0x3c, 0x70, // packet ID
            0x1d, 0xcd, // sequence number
            0x00, 0x01, // payload
            0xb6, 0xe0, 0x87, 0x60, 0x00, 0x00, 0x00, 0x00, 0x97, 0xad, 0x09, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b,
            0x1c, 0x1d, 0x1e, 0x1f, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29,
            0x2a, 0x2b, 0x2c, 0x2d, 0x2e, 0x2f, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
        ];

        let mut buffer = [0u8; 64];
        assert!(construct_icmpv4_packet_inner(
            &mut buffer[..],
            &mut TestPayload {}
        ));
        assert_eq!(buffer, expected_packet);
    }

    #[test]
    fn test_icmpv4_packet_too_short() {
        assert!(!construct_icmpv4_packet_inner(
            &mut [0u8; 13],
            &mut TestPayload {}
        ));
    }
}
