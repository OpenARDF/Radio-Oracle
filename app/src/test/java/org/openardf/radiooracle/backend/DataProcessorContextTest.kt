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

package org.openardf.radiooracle.backend

import android.app.Application
import android.content.Context
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DataProcessorContextTest {
    private lateinit var application: Application

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        DataProcessor.resetForTests()
        ARDFRepository.resetForTests()
        ARDFRepository.initialize(application)
    }

    @After
    fun tearDown() {
        DataProcessor.resetForTests()
        ARDFRepository.resetForTests()
    }

    @Test
    fun activityRecreationKeepsProcessContextAvailableToBackendAndPrinter() {
        val firstActivityContext = activityContext()
        val secondActivityContext = activityContext()

        DataProcessor.initialize(firstActivityContext)
        val dataProcessor = DataProcessor.get()
        DataProcessor.initialize(secondActivityContext)

        assertSame(dataProcessor, DataProcessor.get())
        assertSame(application, dataProcessor.getContext())
        val printerContext = dataProcessor.printProcessor.javaClass
            .getDeclaredField("appContext")
            .run {
                isAccessible = true
                get(dataProcessor.printProcessor)
            }
        assertSame(application, printerContext)
    }

    private fun activityContext(): Context = mock(Context::class.java).also { context ->
        `when`(context.applicationContext).thenReturn(application)
    }
}
