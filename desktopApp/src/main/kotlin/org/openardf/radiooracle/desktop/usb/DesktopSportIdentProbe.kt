package org.openardf.radiooracle.desktop.usb

fun main(args: Array<String>) {
    val requestedPort = args.firstOrNull() ?: System.getenv("RADIO_ORACLE_SI_PORT")
    val provider = JSerialCommDesktopSerialPortProvider
    val ports = provider.listPorts()

    println("Radio-Oracle desktop SPORTident serial probe")
    println("Detected serial ports:")
    if (ports.isEmpty()) {
        println("- none")
    } else {
        ports.forEach { port ->
            println("- ${port.info.describe()}")
        }
    }

    val port = if (requestedPort.isNullOrBlank()) {
        ports.firstOrNull { it.info.matchesSportIdent() } ?: error("No SPORTident USB serial port found.")
    } else {
        provider.getPort(requestedPort)
    }

    println("Using serial port: ${port.info.describe()}")
    val connection = DesktopSportIdentStationProbe().connect(port)
    println("SPORTident probe OK at ${connection.baudRate} baud: ${connection.probeReply.toHexString()}")
    println(
        "SPORTident station info: serial=${connection.stationInfo.serialNumber} " +
            "extended=${connection.stationInfo.extendedMode} " +
            "mode=${connection.stationInfo.stationModeLabel ?: "unknown"}"
    )
    if (connection.stationInfo.isReadoutMode == false) {
        println(
            "WARNING: SPORTident station ${connection.stationInfo.serialNumber} is in " +
                "${connection.stationInfo.stationModeLabel} mode instead of READOUT/SI MASTER. " +
                "Reprogram it in a download-capable mode before using it for SI-card downloads."
        )
    }
}

private fun ByteArray.toHexString(): String {
    return joinToString(" ") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
