use super::{TunnelObfuscatorHandle, TunnelObfuscatorRuntime};
use crate::ProxyHandle;
use std::{net::SocketAddr, sync::Once};

use crate::api_client::helpers::parse_ip_addr;

static INIT_LOGGING: Once = Once::new();

/// SAFETY: `TunnelObfuscatorProtocol` values must either be `0` or `1`
#[repr(u8)]
pub enum TunnelObfuscatorProtocol {
    UdpOverTcp = 0,
    Shadowsocks,
    Quic,
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn start_tunnel_obfuscator_proxy(
    peer_address: *const u8,
    peer_address_len: usize,
    peer_port: u16,
    obfuscation_protocol: TunnelObfuscatorProtocol,
    proxy_handle: *mut ProxyHandle,
) -> i32 {
    INIT_LOGGING.call_once(|| {
        let _ = oslog::OsLogger::new("net.mullvad.MullvadVPN.TunnelObfuscatorProxy")
            .level_filter(log::LevelFilter::Info)
            .init();
    });

    let peer_sock_addr: SocketAddr =
        if let Some(ip_address) = parse_ip_addr(peer_address, peer_address_len) {
            SocketAddr::new(ip_address, peer_port)
        } else {
            return -1;
        };

    let result = TunnelObfuscatorRuntime::new(peer_sock_addr, obfuscation_protocol).run();

    match result {
        Ok((local_endpoint, obfuscator_handle)) => {
            let boxed_handle = Box::new(obfuscator_handle);
            std::ptr::write(
                proxy_handle,
                ProxyHandle {
                    context: Box::into_raw(boxed_handle) as *mut _,
                    port: local_endpoint.port(),
                },
            );
            0
        }
        Err(err) => {
            log::error!("Failed to run tunnel obfuscator proxy {}", err);
            err.raw_os_error().unwrap_or(-1)
        }
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn stop_tunnel_obfuscator_proxy(proxy_handle: *mut ProxyHandle) -> i32 {
    // SAFETY: `proxy_config` is guaranteed to be a valid pointer
    let context_ptr = unsafe { (*proxy_handle).context };
    if context_ptr.is_null() {
        return -1;
    }

    // SAFETY: `context_ptr` is guaranteed to be a valid, non-null pointer
    let obfuscator_handle: Box<TunnelObfuscatorHandle> =
        unsafe { Box::from_raw(context_ptr as *mut _) };
    obfuscator_handle.stop();
    // SAFETY: `proxy_config` is guaranteed to be a valid pointer
    unsafe { (*proxy_handle).context = std::ptr::null_mut() };
    0
}
