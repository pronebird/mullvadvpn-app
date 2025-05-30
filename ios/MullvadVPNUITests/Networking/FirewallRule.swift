//
//  FirewallRule.swift
//  MullvadVPNUITests
//
//  Created by Niklas Berglund on 2024-01-18.
//  Copyright © 2025 Mullvad VPN AB. All rights reserved.
//

import Foundation
import XCTest

struct FirewallRule {
    let fromIPAddress: String
    let toIPAddress: String
    let protocols: [TransportProtocol]

    /// - Parameters:
    ///     - fromIPAddress: Block traffic originating from this source IP address.
    ///     - toIPAddress: Block traffic to this destination IP address.
    ///     - protocols: Protocols which should be blocked. If none is specified all will be blocked.
    private init(fromIPAddress: String, toIPAddress: String, protocols: [TransportProtocol]) {
        self.fromIPAddress = fromIPAddress
        self.toIPAddress = toIPAddress
        self.protocols = protocols
    }

    public func protocolsAsStringArray() -> [String] {
        return protocols.map { $0.rawValue }
    }

    /// Make a firewall rule blocking API access for the current device under test
    public static func makeBlockAPIAccessFirewallRule() throws -> FirewallRule {
        let deviceIPAddress = try FirewallClient().getDeviceIPAddress()
        let apiIPAddress = try MullvadAPIWrapper.getAPIIPAddress()
        return FirewallRule(
            fromIPAddress: deviceIPAddress,
            toIPAddress: apiIPAddress,
            protocols: [.transport(.TCP)]
        )
    }

    public static func makeBlockAllTrafficRule(toIPAddress: String) throws -> FirewallRule {
        let deviceIPAddress = try FirewallClient().getDeviceIPAddress()

        return FirewallRule(
            fromIPAddress: deviceIPAddress,
            toIPAddress: toIPAddress,
            protocols: [.transport(.ICMP), .transport(.TCP), .transport(.UDP)]
        )
    }

    public static func makeBlockWireGuardTrafficRule(
        fromIPAddress: String,
        toIPAddress: String
    ) throws -> FirewallRule {
        FirewallRule(
            fromIPAddress: fromIPAddress,
            toIPAddress: toIPAddress,
            protocols: [.application(.wireguard)]
        )
    }

    public static func makeBlockUDPTrafficRule(toIPAddress: String) throws -> FirewallRule {
        let deviceIPAddress = try FirewallClient().getDeviceIPAddress()

        return FirewallRule(
            fromIPAddress: deviceIPAddress,
            toIPAddress: toIPAddress,
            protocols: [.transport(.UDP)]
        )
    }
}
