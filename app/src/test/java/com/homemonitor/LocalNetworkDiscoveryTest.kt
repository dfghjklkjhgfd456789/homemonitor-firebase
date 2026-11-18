package com.google.android.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkDiscoveryTest {

    @Test
    fun testDefaultPortsList() {
        val ports = LocalNetworkDiscoveryEngine.DEFAULT_PORTS
        assertTrue(ports.contains(80))
        assertTrue(ports.contains(443))
        assertTrue(ports.contains(22))
        assertTrue(ports.contains(5555))
        assertTrue(ports.contains(5467))
    }

    @Test
    fun testSubnetScanReportFormatting() {
        val hosts = listOf(
            LocalNetworkDiscoveryEngine.DiscoveredHost(
                ip = "192.168.1.1",
                macAddress = "AA:BB:CC:DD:EE:FF",
                openPorts = listOf(80, 443),
                isGateway = true,
                isSelf = false
            ),
            LocalNetworkDiscoveryEngine.DiscoveredHost(
                ip = "192.168.1.100",
                macAddress = "11:22:33:44:55:66",
                openPorts = listOf(5467),
                isGateway = false,
                isSelf = true
            )
        )

        val report = LocalNetworkDiscoveryEngine.SubnetScanReport(
            localIp = "192.168.1.100",
            subnetPrefix = "192.168.1.",
            totalScanned = 254,
            activeHosts = hosts,
            durationMs = 1250L
        )

        val formattedMap = LocalNetworkDiscoveryEngine.formatAsciiNetworkMap(report)
        assertNotNull(formattedMap)
        assertTrue(formattedMap.contains("Local Network Subnet Map"))
        assertTrue(formattedMap.contains("192.168.1.1"))
        assertTrue(formattedMap.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(formattedMap.contains("[Gateway/Router]"))
        assertTrue(formattedMap.contains("[This Device]"))
    }

    @Test
    fun testHostPortReportDataClass() {
        val report = LocalNetworkDiscoveryEngine.HostPortReport(
            ip = "127.0.0.1",
            openPorts = listOf(80, 5467),
            scannedPortCount = 8
        )

        assertEquals("127.0.0.1", report.ip)
        assertEquals(2, report.openPorts.size)
        assertEquals(8, report.scannedPortCount)
    }

    @Test
    fun testArpTableParsingSafety() {
        val arpMap = LocalNetworkDiscoveryEngine.parseArpTable()
        assertNotNull(arpMap)
    }
}
