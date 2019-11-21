pub const CLASSES: &[&str] = &[
    "java/lang/Boolean",
    "java/net/InetAddress",
    "java/net/InetSocketAddress",
    "java/util/ArrayList",
    "net/mullvad/mullvadvpn/model/AccountData",
    "net/mullvad/mullvadvpn/model/AppVersionInfo",
    "net/mullvad/mullvadvpn/model/Constraint$Any",
    "net/mullvad/mullvadvpn/model/Constraint$Only",
    "net/mullvad/mullvadvpn/model/GeoIpLocation",
    "net/mullvad/mullvadvpn/model/GetAccountDataResult$Ok",
    "net/mullvad/mullvadvpn/model/GetAccountDataResult$InvalidAccount",
    "net/mullvad/mullvadvpn/model/GetAccountDataResult$RpcError",
    "net/mullvad/mullvadvpn/model/GetAccountDataResult$OtherError",
    "net/mullvad/mullvadvpn/model/KeygenEvent$NewKey",
    "net/mullvad/mullvadvpn/model/KeygenEvent$Failure",
    "net/mullvad/mullvadvpn/model/KeygenFailure$TooManyKeys",
    "net/mullvad/mullvadvpn/model/KeygenFailure$GenerationFailure",
    "net/mullvad/mullvadvpn/model/LocationConstraint$City",
    "net/mullvad/mullvadvpn/model/LocationConstraint$Country",
    "net/mullvad/mullvadvpn/model/LocationConstraint$Hostname",
    "net/mullvad/mullvadvpn/model/PublicKey",
    "net/mullvad/mullvadvpn/model/Relay",
    "net/mullvad/mullvadvpn/model/RelayList",
    "net/mullvad/mullvadvpn/model/RelayListCity",
    "net/mullvad/mullvadvpn/model/RelayListCountry",
    "net/mullvad/mullvadvpn/model/RelaySettings$CustomTunnelEndpoint",
    "net/mullvad/mullvadvpn/model/RelaySettings$RelayConstraints",
    "net/mullvad/mullvadvpn/model/RelaySettingsUpdate$CustomTunnelEndpoint",
    "net/mullvad/mullvadvpn/model/RelaySettingsUpdate$RelayConstraintsUpdate",
    "net/mullvad/mullvadvpn/model/Settings",
    "net/mullvad/mullvadvpn/model/TunnelState$Blocked",
    "net/mullvad/mullvadvpn/model/TunnelState$Connected",
    "net/mullvad/mullvadvpn/model/TunnelState$Connecting",
    "net/mullvad/mullvadvpn/model/TunnelState$Disconnected",
    "net/mullvad/mullvadvpn/model/TunnelState$Disconnecting",
    "net/mullvad/mullvadvpn/MullvadDaemon",
    "net/mullvad/mullvadvpn/MullvadVpnService",
    "net/mullvad/talpid/net/Endpoint",
    "net/mullvad/talpid/net/TransportProtocol",
    "net/mullvad/talpid/net/TunnelEndpoint",
    "net/mullvad/talpid/tun_provider/InetNetwork",
    "net/mullvad/talpid/tun_provider/TunConfig",
    "net/mullvad/talpid/tunnel/ActionAfterDisconnect$Block",
    "net/mullvad/talpid/tunnel/ActionAfterDisconnect$Nothing",
    "net/mullvad/talpid/tunnel/ActionAfterDisconnect$Reconnect",
    "net/mullvad/talpid/tunnel/BlockReason$AuthFailed",
    "net/mullvad/talpid/tunnel/BlockReason$Ipv6Unavailable",
    "net/mullvad/talpid/tunnel/BlockReason$SetFirewallPolicyError",
    "net/mullvad/talpid/tunnel/BlockReason$SetDnsError",
    "net/mullvad/talpid/tunnel/BlockReason$StartTunnelError",
    "net/mullvad/talpid/tunnel/BlockReason$ParameterGeneration",
    "net/mullvad/talpid/tunnel/BlockReason$IsOffline",
    "net/mullvad/talpid/tunnel/BlockReason$TapAdapterProblem",
    "net/mullvad/talpid/tunnel/ParameterGenerationError$NoMatchingRelay",
    "net/mullvad/talpid/tunnel/ParameterGenerationError$NoMatchingBridgeRelay",
    "net/mullvad/talpid/tunnel/ParameterGenerationError$NoWireguardKey",
    "net/mullvad/talpid/tunnel/ParameterGenerationError$CustomTunnelHostResultionError",
    "net/mullvad/talpid/TalpidVpnService",
];
