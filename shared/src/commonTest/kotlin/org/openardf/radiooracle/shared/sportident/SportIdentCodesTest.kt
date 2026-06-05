package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportIdentCodesTest {
    @Test
    fun validatesSportIdentNumberRange() {
        assertFalse(SportIdentCodes.isSINumberValid(999))
        assertTrue(SportIdentCodes.isSINumberValid(1000))
        assertTrue(SportIdentCodes.isSINumberValid(9999999))
        assertFalse(SportIdentCodes.isSINumberValid(10000000))
    }

    @Test
    fun validatesControlCodeRange() {
        assertFalse(SportIdentCodes.isSICodeValid(0))
        assertTrue(SportIdentCodes.isSICodeValid(1))
        assertTrue(SportIdentCodes.isSICodeValid(255))
        assertTrue(SportIdentCodes.isSICodeValid(256))
        assertTrue(SportIdentCodes.isSICodeValid(511))
        assertFalse(SportIdentCodes.isSICodeValid(512))
        assertTrue(SportIdentCodes.isLegacyCompatibleSICode(255))
        assertFalse(SportIdentCodes.isLegacyCompatibleSICode(256))
    }
}
