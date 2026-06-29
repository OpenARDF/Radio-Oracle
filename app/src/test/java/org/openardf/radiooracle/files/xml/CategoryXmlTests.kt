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

package org.openardf.radiooracle.files.xml

import android.content.Context
import junit.framework.TestCase.assertEquals
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.DataImportValidator
import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.processors.IofXmlProcessor
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.Race
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CategoryXmlTests {

    @Before
    fun setUp() {
        DataProcessor.resetForTests()
    }

    @After
    fun tearDown() {
        DataProcessor.resetForTests()
    }

    @Test
    fun testCategoryValidImport() = runTest {

        val race = Race()
        val context = mock(Context::class.java)

        val stream =
            this::class.java.classLoader?.getResourceAsStream("xml/xml_category_valid_example.xml")!!

        val wrapper = IofXmlProcessor.importCategories(stream, race, context)
        val catA = wrapper.categories[0]
        val catB = wrapper.categories[1]

        assertEquals(2, wrapper.categories.size)
        assertEquals("A", catA.category.name)
        assertEquals(2960, catA.category.length)
        assertEquals(95, catA.category.climb)
        assertEquals("B", catB.category.name)
        assertEquals(2960, catB.category.length)
        assertEquals(95, catB.category.climb)

        val cpA = catA.controlPoints
        val cpB = catB.controlPoints

        assertEquals(31, cpA[0].siCode)
        assertEquals(32, cpA[1].siCode)
        assertEquals(33, cpA[2].siCode)
        assertEquals(31, cpA[3].siCode)
        assertEquals(34, cpA[4].siCode)
        assertEquals(35, cpA[5].siCode)
        assertEquals(31, cpA[6].siCode)
        assertEquals(100, cpA[7].siCode)

        assertEquals(31, cpB[0].siCode)
        assertEquals(34, cpB[1].siCode)
        assertEquals(35, cpB[2].siCode)
        assertEquals(31, cpB[3].siCode)
        assertEquals(32, cpB[4].siCode)
        assertEquals(33, cpB[5].siCode)
        assertEquals(31, cpB[6].siCode)
        assertEquals(100, cpB[7].siCode)
    }

    @Test
    fun testCategoryValidImportPersistsControls() = runTest {
        val context = RuntimeEnvironment.getApplication()
        ARDFRepository.initialize(context)
        DataProcessor.initialize(context)
        val dataProcessor = DataProcessor.get()
        val race = Race().also { race ->
            race.name = "IOF CourseData Persistence"
        }
        dataProcessor.createRace(race)
        val stream =
            this::class.java.classLoader?.getResourceAsStream("xml/xml_category_valid_example.xml")!!

        val wrapper = IofXmlProcessor.importCategories(stream, race, context)
        DataImportValidator.validateDataImport(wrapper, race.id, DataType.CATEGORIES, dataProcessor, context)
        dataProcessor.saveDataImportWrapper(wrapper)

        val categoryData = dataProcessor.getCategoryDataForRace(race.id)
        assertEquals(2, categoryData.size)
        assertEquals(listOf(31, 32, 33, 31, 34, 35, 31, 100), categoryData.first { it.category.name == "A" }.controlPoints.map { it.siCode })
    }

    @Test
    fun testCategoryNameMissing() = runTest {

        val race = Race()
        val context = mock(Context::class.java)

        `when`(context.getString(any())).thenReturn($$"Category name missing at line: %1$d")

        val stream =
            this::class.java.classLoader?.getResourceAsStream("xml/xml_category_invalid_example.xml")!!

        assertThrows(IllegalArgumentException::class.java) {
            IofXmlProcessor.importCategories(stream, race, context)
        }
    }
}
