// dnstest.cpp : Defines the entry point for the console application.
//

#include "stdafx.h"
#include "windns/wmi/connection.h"
#include "windns/wmi/resultset.h"
#include "windns/wmi/methodcall.h"
#include "windns/comhelpers.h"

#include <iostream>
#include <algorithm>
#include <vector>
#include <string>
#include <atlsafe.h>
#include "windns/dnsconfig.h"


#include "windns/netconfigeventsink.h"
#include "windns/configmanager.h"
#include "windns/consoletracesink.h"
#include "windns/wmi/eventsink.h"
#include "windns/wmi/notification.h"
#include "windns/dnsreverter.h"

#include "windns/windns.h"

void testDll()
{
	std::wcout << L"Init: " << WinDns_Initialize(nullptr, nullptr) << std::endl;

	const wchar_t *servers[] =
	{
		L"8.8.8.8",
		L"1.1.1.1"
	};

	std::wcout << L"Set: " << WinDns_Set(servers, _countof(servers)) << std::endl;

	std::wcout << L"Press a key to abort DNS monitoring + enforcing..." << std::endl;
	_getwch();

	std::wcout << L"Reset: " << WinDns_Reset() << std::endl;

	std::wcout << L"Set: " << WinDns_Set(servers, _countof(servers)) << std::endl;

	std::wcout << L"Press a key to abort DNS monitoring + enforcing..." << std::endl;
	_getwch();

	std::wcout << L"Reset: " << WinDns_Reset() << std::endl;

	std::wcout << L"Deinit: " << WinDns_Deinitialize() << std::endl;
}

int main()
{
	testDll();

	return 0;



	auto traceSink = std::make_shared<ConsoleTraceSink>();
	auto connection = std::make_shared<wmi::Connection>(wmi::Connection::Namespace::Cimv2);

	std::vector<std::wstring> servers{ L"8.8.8.8", L"1.1.1.1" };

	auto configManager = std::make_shared<ConfigManager>(servers, traceSink);

	//
	// Collect configurations for all active interfaces
	//
	// TODO: There is a small window between when we collect the configurations and when we start to monitor them.
	// This could be remedied by extending the event sink to receive both the old+new configuration, which are both
	// provided by WMI.
	//

	auto resultSet = connection->query(L"SELECT * from Win32_NetworkAdapterConfiguration WHERE IPEnabled = True");

	while (resultSet.advance())
	{
		auto config = DnsConfig(resultSet.result());
		configManager->updateConfig(std::move(config));
	}

	//
	// Register interface configuration monitoring
	//

	auto eventSink = std::make_shared<NetConfigEventSink>(connection, configManager);
	auto eventSinkWrapper = CComPtr<wmi::EventSink>(new wmi::EventSink(eventSink));

	wmi::Notification notification(connection, eventSinkWrapper);

	notification.activate
	(
		L"SELECT * "
		L"FROM __InstanceModificationEvent "
		L"WITHIN 1 "
		L"WHERE TargetInstance ISA 'Win32_NetworkAdapterConfiguration'"
		L"AND TargetInstance.IPEnabled = True"
	);

	//
	// Apply our DNS settings
	// TODO: Package this better, try to reuse the network configuration instances from before.
	//

	{
		ConfigManager::Mutex mutex(*configManager);

		configManager->processConfigs([&](const DnsConfig &config)
		{
			std::wcout << L"Overriding DNS settings for interface = " << config.interfaceIndex() << std::endl;

			std::wstringstream ss;

			ss << L"SELECT * FROM Win32_NetworkAdapterConfiguration "
				<< L"WHERE SettingID = '" << config.id() << L"'";

			auto resultSet = connection->query(ss.str().c_str());

			if (false == resultSet.advance())
			{
				std::wcout << L"Unable to retrieve active configuration" << std::endl;
			}
			else
			{
				auto activeConfig = resultSet.result();
				nchelpers::SetDnsServers(*connection, activeConfig, &servers);
			}

			// Continue with the next interface configuration.
			return true;
		});
	}

	std::wcout << L"Press a key to abort DNS monitoring + enforcing..." << std::endl;
	_getwch();

	notification.deactivate();

	//
	// Revert configs
	// Safe to do without a mutex guarding the config manager
	//

	DnsReverter dnsReverter(traceSink);

	configManager->processConfigs([&](const DnsConfig &config)
	{
		std::wcout << L"Revering DNS settings for interface = " << config.interfaceIndex() << std::endl;

		dnsReverter.revert(*connection, config);

		return true;
	});


	std::wcout << L"=====" << std::endl << L"done" << std::endl;

	return 0;
}

