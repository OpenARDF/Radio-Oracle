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

package org.openardf.radiooracle.shared.files

import java.io.File

/** Loads the packaged IOF XML 3.0 schema used for production import validation. */
object IofXmlSchemaResource {
    private const val RESOURCE_PATH = "iof/IOF.xsd"

    fun loadBundledSchema(): String {
        val classLoaders = listOfNotNull(
            Thread.currentThread().contextClassLoader,
            IofXmlSchemaResource::class.java.classLoader,
            ClassLoader.getSystemClassLoader()
        ).distinct()
        classLoaders.forEach { classLoader ->
            classLoader.getResourceAsStream(RESOURCE_PATH)
                ?.bufferedReader()
                ?.use { return it.readText() }
        }
        schemaFileCandidates().firstOrNull(File::isFile)?.let { return it.readText() }
        throw IllegalArgumentException("Bundled IOF XML 3.0 schema resource not found: $RESOURCE_PATH")
    }

    private fun schemaFileCandidates(): List<File> {
        val workingDirectory = File(System.getProperty("user.dir") ?: ".")
        return listOf(
            File(workingDirectory, "shared/src/commonMain/resources/$RESOURCE_PATH"),
            File(workingDirectory, "../shared/src/commonMain/resources/$RESOURCE_PATH"),
            File(workingDirectory, "src/commonMain/resources/$RESOURCE_PATH"),
            File(workingDirectory, "app/src/main/assets/$RESOURCE_PATH"),
            File(workingDirectory, "../app/src/main/assets/$RESOURCE_PATH")
        )
    }
}
