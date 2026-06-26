package org.openardf.radiooracle.desktop.usb

fun main() {
    val requestedPort = System.getenv("RADIO_ORACLE_SI_PORT")?.trim()?.takeIf { it.isNotEmpty() }
    val provider = JSerialCommDesktopSerialPortProvider
    val service = DesktopSportIdentTimeSyncService(
        portProvider = if (requestedPort == null) {
            provider
        } else {
            object : DesktopSerialPortProvider {
                override fun listPorts(): List<DesktopSerialPort> = listOf(provider.getPort(requestedPort))
                override fun getPort(systemPortPath: String): DesktopSerialPort = provider.getPort(systemPortPath)
            }
        }
    )

    println("Radio-Oracle desktop SPORTident time sync inspection")
    val inspection = service.inspectDownloadStation()
    println("Status: ${inspection.statusText}")
    inspection.portInfo?.let { println("Port: ${it.describe()}") }
    inspection.baudRate?.let { println("Baud: $it") }
    inspection.stationInfo?.let { station ->
        println("Station serial: ${station.serialNumber}")
        println("Station code: ${station.stationCodeNumber ?: "unknown"}")
        println("Station mode: ${station.stationModeLabel ?: "unknown"}")
        println("Extended mode: ${station.extendedMode}")
    }
    println("Can sync time: ${inspection.canSyncTime}")
    inspection.disabledReason?.let { println("Disabled reason: $it") }
}
