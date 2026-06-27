/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop.usb

/** Desktop SPORTident readout boundary shared by UI actions and diagnostics. */
class DesktopSportIdentReadoutService(
    private val portProvider: DesktopSerialPortProvider = JSerialCommDesktopSerialPortProvider,
    private val connectStation: (DesktopSerialPort) -> DesktopSportIdentStationConnection = {
        DesktopSportIdentStationProbe().connectKeepingPortOpen(it)
    },
    private val readCard: (DesktopSerialPort) -> DesktopSportIdentCardBlockDownload = {
        DesktopSportIdentCardBlockReader().readFirstSupportedCardAfterInsertOnOpenPort(it)
    },
    private val waitAfterSuccessfulCard: (DesktopSerialPort, DesktopSportIdentCardBlockDownload) -> Unit = { port, download ->
        DesktopSportIdentCardEventMonitor().waitForRemoveEventOnOpenPort(
            port = port,
            siNumber = download.readout.siNumber,
            deadlineMillis = System.currentTimeMillis() + postReadGuardMs
        )
    }
) {
    fun downloadOne(): DesktopSportIdentCardBlockDownload {
        val port = firstSportIdentPort()
        return withOpenDownloadStation(port) {
            readCard(port)
        }
    }

    fun downloadUntilTimeout(
        maxCards: Int,
        onDownload: (DesktopSportIdentCardBlockDownload) -> Unit,
        onTimeout: () -> Unit = {},
        shouldContinue: () -> Boolean = { true },
        isTimeoutError: (Throwable) -> Boolean = ::isNoCardInsertTimeout,
        continueAfterTimeout: Boolean = false
    ): Int {
        val port = firstSportIdentPort()
        var cardsRead = 0
        withOpenDownloadStation(port) {
            while (cardsRead < maxCards && shouldContinue()) {
                val result = runCatching { readCard(port) }
                val download = result.getOrNull()
                if (download == null) {
                    val error = result.exceptionOrNull()
                    if (error != null && isTimeoutError(error)) {
                        onTimeout()
                        if (!continueAfterTimeout) {
                            break
                        }
                        continue
                    }
                    throw error ?: IllegalStateException("SPORTident card download failed.")
                }
                onDownload(download)
                waitAfterSuccessfulCard(port, download)
                cardsRead += 1
            }
        }
        return cardsRead
    }

    private fun firstSportIdentPort(): DesktopSerialPort =
        portProvider.listPorts().firstOrNull { it.info.matchesSportIdent() }
            ?: error("No SPORTident USB station found.")

    private fun <T> withOpenDownloadStation(port: DesktopSerialPort, action: () -> T): T {
        try {
            val station = connectStation(port)
            if (station.stationInfo.isDownloadCapableMode == false) {
                error(
                    "SI station ${station.stationInfo.serialNumber} is in " +
                        "${station.stationInfo.stationModeLabel} mode instead of READOUT/SI MASTER."
                )
            }
            return action()
        } finally {
            if (port.isOpen) {
                port.close()
            }
        }
    }
}

private fun isNoCardInsertTimeout(error: Throwable): Boolean =
    (error.message ?: "").contains("No SPORTident card insert event")

private val postReadGuardMs: Long =
    System.getenv("RADIO_ORACLE_SI_POST_READ_GUARD_MS")?.toLongOrNull() ?: 2_500L
