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

package org.openardf.radiooracle.desktop

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

/** Narrow, read-only entry point used to prove exports from an installed desktop package. */
object DesktopInstalledPackageSmoke {
    const val COMMAND = "--installed-package-smoke"

    /** Returns null for ordinary GUI arguments so installed file-opening behavior remains unchanged. */
    fun runIfRequested(
        args: Array<String>,
        out: PrintStream = System.out,
        err: PrintStream = System.err
    ): Int? {
        if (args.firstOrNull() != COMMAND) return null
        if (args.size != 3) {
            err.println("Usage: $COMMAND <race-file> <new-output-directory>")
            return 64
        }

        return runCatching {
            val source = Path.of(args[1]).toAbsolutePath().normalize()
            val output = Path.of(args[2]).toAbsolutePath().normalize()
            require(Files.isRegularFile(source)) { "Race File does not exist: $source" }
            require(!Files.exists(output)) { "Output directory must not already exist: $output" }

            val projectFile = DesktopProjectFiles.read(source)
            Files.createDirectories(output)

            // Exercise the same desktop adapters and shared format writers used by the UI.
            val resultsCsv = output.resolve("results.csv")
            val finalResultsJson = output.resolve("final-results.json")
            val startListXml = output.resolve("start-list.xml")
            DesktopProjectFiles.exportResultsCsv(resultsCsv, projectFile)
            DesktopProjectFiles.exportFinalResultsJson(finalResultsJson, projectFile)
            DesktopProjectFiles.exportIofStartListXml(startListXml, projectFile)

            val artifacts = listOf(resultsCsv, finalResultsJson, startListXml)
            require(artifacts.all { Files.isRegularFile(it) && Files.size(it) > 0L }) {
                "One or more installed-package export artifacts are missing or empty."
            }
            out.println("Radio-Oracle installed-package exports OK: ${artifacts.joinToString { it.fileName.toString() }}")
            0
        }.getOrElse { failure ->
            err.println("Installed-package export smoke failed: ${failure.message ?: failure.javaClass.simpleName}")
            1
        }
    }
}
