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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package org.openardf.radiooracle.shared.files

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IofXmlValidatorTest {

    @Test
    fun validatesStartListAgainstIof30Schema() {
        val xsd = localIofXsd()

        val result = IofXmlValidator.validate(validStartListXml(), xsd)

        assertTrue(result.valid, result.firstMessage.orEmpty())
    }

    @Test
    fun bundledIof30SchemaValidatesStartList() {
        val xsd = IofXmlSchemaResource.loadBundledSchema()

        val result = IofXmlValidator.validate(validStartListXml(), xsd)

        assertTrue(result.valid, result.firstMessage.orEmpty())
    }

    @Test
    fun rejectsLegacyStartNumberAgainstIof30Schema() {
        val xsd = localIofXsd()

        val result = IofXmlValidator.validate(
            validStartListXml().replace("<BibNumber>1001</BibNumber>", "<StartNumber>1</StartNumber>"),
            xsd
        )

        assertFalse(result.valid)
        assertTrue(result.firstMessage.orEmpty().contains("StartNumber"))
    }

    private fun localIofXsd(): String =
        IofXmlSchemaResource.loadBundledSchema()

    private fun validStartListXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <StartList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0" creator="Radio-Oracle test">
          <Event>
            <Name>Schema Race</Name>
            <StartTime>
              <Date>2026-06-29</Date>
              <Time>09:00:00</Time>
            </StartTime>
          </Event>
          <ClassStart>
            <Class>
              <Name>M21</Name>
            </Class>
            <PersonStart>
              <Person>
                <Name>
                  <Family>Example</Family>
                  <Given>Ada</Given>
                </Name>
              </Person>
              <Start>
                <BibNumber>1001</BibNumber>
                <StartTime>2026-06-29T09:00:00</StartTime>
              </Start>
            </PersonStart>
          </ClassStart>
        </StartList>
    """.trimIndent()

}
