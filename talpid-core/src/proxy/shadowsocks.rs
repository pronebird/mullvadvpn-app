pub use std::io;

use async_trait::async_trait;
use futures::future::{abortable, AbortHandle, Aborted};
use socket2::{Domain, SockAddr, Socket};
use std::net::{Ipv4Addr, SocketAddr, SocketAddrV4};
use tokio::task::JoinHandle;

use shadowsocks_service::{
    config::{Config, ConfigType, LocalConfig, ProtocolType},
    local,
    shadowsocks::{
        config::{Mode, ServerConfig},
        ServerAddr,
    },
};

use super::{ProxyMonitor, ProxyMonitorCloseHandle, ProxyResourceData, WaitResult};
use talpid_types::{net::openvpn::ShadowsocksProxySettings, ErrorExt};

pub struct ShadowsocksProxyMonitor {
    port: u16,
    server_join_handle: JoinHandle<Result<io::Result<()>, Aborted>>,
    server_abort_handle: AbortHandle,
}

impl ShadowsocksProxyMonitor {
    pub async fn start(
        settings: &ShadowsocksProxySettings,
        _resource_data: &ProxyResourceData,
    ) -> io::Result<Self> {
        // TODO: Patch shadowsocks so the bound address can be obtained afterwards.
        let addr = SocketAddrV4::new(Ipv4Addr::LOCALHOST, 0);
        let sock = Socket::new(
            Domain::IPV4,
            socket2::Type::STREAM,
            Some(socket2::Protocol::TCP),
        )?;
        sock.set_reuse_address(true)?;
        sock.bind(&SockAddr::from(addr))?;

        let bound_addr = sock
            .local_addr()?
            .as_socket_ipv4()
            .ok_or_else(|| io::Error::new(io::ErrorKind::Other, "missing IPv4 address"))?;

        let mut config = Config::new(ConfigType::Local);

        config.fast_open = true;

        let mut local = LocalConfig::new(ProtocolType::Socks);
        local.mode = Mode::TcpOnly;
        local.addr = Some(ServerAddr::SocketAddr(SocketAddr::from(bound_addr)));

        config.local.push(local);

        let server = ServerConfig::new(
            settings.peer,
            settings.password.clone(),
            settings.cipher.parse().map_err(|_| {
                io::Error::new(
                    io::ErrorKind::Other,
                    format!("Invalid cipher: {}", settings.cipher),
                )
            })?,
        );

        config.server.push(server);

        #[cfg(target_os = "linux")]
        {
            config.outbound_fwmark = Some(crate::linux::TUNNEL_FW_MARK);
        }

        let srv = local::create(config).await?;

        let (fut, server_abort_handle) = abortable(async move {
            let _ = sock;
            let result = srv.run().await;
            if let Err(error) = &result {
                log::error!(
                    "{}",
                    error.display_chain_with_msg("sslocal stopped with an error")
                );
            }
            result
        });
        let server_join_handle = tokio::spawn(fut);

        Ok(Self {
            port: bound_addr.port(),
            server_join_handle,
            server_abort_handle,
        })
    }
}

#[async_trait]
impl ProxyMonitor for ShadowsocksProxyMonitor {
    fn close_handle(&mut self) -> Box<dyn ProxyMonitorCloseHandle> {
        Box::new(ShadowsocksProxyMonitorCloseHandle {
            server_abort_handle: self.server_abort_handle.clone(),
        })
    }

    async fn wait(mut self: Box<Self>) -> io::Result<WaitResult> {
        match self.server_join_handle.await {
            Ok(Err(Aborted)) => Ok(WaitResult::ProperShutdown),

            Err(join_err) if join_err.is_cancelled() => Ok(WaitResult::ProperShutdown),
            Err(_) => Ok(WaitResult::UnexpectedExit(
                "Shadowsocks task panicked".to_string(),
            )),

            Ok(Ok(result)) => match result {
                Ok(()) => Ok(WaitResult::UnexpectedExit(
                    "Exited without error".to_string(),
                )),
                Err(error) => Ok(WaitResult::UnexpectedExit(format!(
                    "Error: {}",
                    error.display_chain()
                ))),
            },
        }
    }

    fn port(&self) -> u16 {
        self.port
    }
}

struct ShadowsocksProxyMonitorCloseHandle {
    server_abort_handle: AbortHandle,
}

impl ProxyMonitorCloseHandle for ShadowsocksProxyMonitorCloseHandle {
    fn close(self: Box<Self>) -> io::Result<()> {
        self.server_abort_handle.abort();
        Ok(())
    }
}
