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

import org.openardf.radiooracle.shared.event.EventAwardDisplayMode
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.files.SplitResultExports
import org.openardf.radiooracle.shared.files.SplitResultPdfExports
import java.nio.file.Files
import java.nio.file.Path

/** Desktop filesystem adapter for the shared printable split-results PDF. */
object DesktopSplitResultReportPdf {
    fun defaultFileName(projectFile: EventProjectFile): String =
        DesktopProjectFilePaths.defaultPdfFileName(projectFile.raceData.race.name, "split results")

    fun exportPdf(
        path: Path,
        projectFile: EventProjectFile,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ) {
        path.parent?.let(Files::createDirectories)
        val report = SplitResultExports.model(projectFile.raceData, awardDisplayMode)
        Files.write(path, SplitResultPdfExports.pdf(report))
    }
}
