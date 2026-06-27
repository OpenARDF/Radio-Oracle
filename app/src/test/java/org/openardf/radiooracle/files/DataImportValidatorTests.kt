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

package org.openardf.radiooracle.files

import android.content.Context
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.DataImportValidator
import org.openardf.radiooracle.shared.files.DataType
import org.openardf.radiooracle.backend.files.wrappers.DataImportWrapper
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.anyVararg
import java.util.UUID

// Test class for DataImportValidator
class DataImportValidatorTests {

    private lateinit var context: Context
    private lateinit var dataProcessor: DataProcessor
    private val raceId = UUID.randomUUID()

    @Before
    fun setup() {
        context = mock(Context::class.java)
        dataProcessor = mock(DataProcessor::class.java)

        // Stub getString(id, vararg) to return a simple readable message
        `when`(context.getString(anyInt())).thenAnswer { invocation ->
            "msg_${invocation.arguments[0]}"
        }
        `when`(context.getString(anyInt(), anyVararg())).thenAnswer { invocation ->
            val id = invocation.arguments[0] as Int
            val args = invocation.arguments.drop(1).joinToString(",")
            "msg_${id}($args)"
        }
    }

    @Test
    fun testValidateThrowsOnDuplicateCategoryNames() {

        val categories = listOf(
            CategoryData(category = Category("A"), emptyList(), emptyList()),
            CategoryData(category = Category("A"), emptyList(), emptyList())
        )

        assertThrows(IllegalArgumentException::class.java) {
            DataImportValidator.validateCategories(categories, context)
        }
    }

    @Test
    fun testValidateThrowsOnDuplicateCompetitorSI() {
        val competitor1 = Competitor()
        competitor1.siNumber = 10000

        val competitor2 = Competitor()
        competitor2.siNumber = 10000

        val wrapper = DataImportWrapper(
            competitorCategories = listOf(
                CompetitorCategory(competitor1, Category("")),
                CompetitorCategory(competitor2, Category(""))
            ), categories = emptyList(), invalidLines = arrayListOf()
        )

        assertThrows(IllegalArgumentException::class.java) {
            DataImportValidator.validateDataImport(
                wrapper,
                raceId,
                DataType.COMPETITORS,
                dataProcessor,
                context
            )
        }
    }

    @Test
    fun testValidateAllowsDuplicateCompetitorStartNumber() {
        val competitor1 = Competitor()
        competitor1.startNumber = 1
        competitor1.siNumber = 10001

        val competitor2 = Competitor()
        competitor2.startNumber = 1
        competitor2.siNumber = 10002

        val wrapper = DataImportWrapper(
            competitorCategories = listOf(
                CompetitorCategory(competitor1, Category("")),
                CompetitorCategory(competitor2, Category(""))
            ), categories = emptyList(), invalidLines = arrayListOf()
        )

        DataImportValidator.validateDataImport(
            wrapper,
            raceId,
            DataType.COMPETITORS,
            dataProcessor,
            context
        )
    }

    @Test
    fun testValidateThrowsOnDuplicateReadoutStarts() {
        val punch1 = Punch()
        punch1.punchType = SIRecordType.START

        val punches = listOf(AliasPunch(punch1, null), AliasPunch(punch1, null))
        val readoutData = ReadoutData(Result(), punches)


        assertThrows(IllegalArgumentException::class.java) {
            DataImportValidator.validateRaceDataReadoutData(
                readoutData,
                raceId,
                context
            )
        }
    }
}
