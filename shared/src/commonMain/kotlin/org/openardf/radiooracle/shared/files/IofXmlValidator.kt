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

import java.io.File
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.XMLReader

/** One schema-validation error reported for IOF XML input. */
data class IofXmlValidationError(
    val message: String,
    val lineNumber: Int? = null,
    val columnNumber: Int? = null
)

/** Result of validating an IOF XML document against an IOF XSD schema. */
data class IofXmlValidationResult(
    val valid: Boolean,
    val errors: List<IofXmlValidationError>
) {
    val firstMessage: String? = errors.firstOrNull()?.let { error ->
        buildString {
            append(error.message)
            if (error.lineNumber != null || error.columnNumber != null) {
                append(" (line ")
                append(error.lineNumber ?: "?")
                append(", column ")
                append(error.columnNumber ?: "?")
                append(")")
            }
        }
    }
}

/** Shared IOF XML schema validator. Callers provide the IOF 3.0 XSD text. */
object IofXmlValidator {
    private const val IOF_NAMESPACE = "http://www.orienteering.org/datastandard/3.0"

    fun validate(xml: String, xsd: String): IofXmlValidationResult =
        try {
            val schema = w3cSchemaFactory().newSchema(StreamSource(StringReader(xsd)))
            schema.newValidator().validate(StreamSource(StringReader(xml)))
            IofXmlValidationResult(valid = true, errors = emptyList())
        } catch (exception: SAXParseException) {
            validationFailure(exception)
        } catch (exception: IllegalArgumentException) {
            bundledXercesSaxValidation(xml, xsd) ?: schemaUnavailableFailure(exception)
        } catch (exception: LinkageError) {
            bundledXercesSaxValidation(xml, xsd) ?: schemaUnavailableFailure(exception)
        } catch (exception: Exception) {
            validationFailure(exception)
        }

    private fun w3cSchemaFactory(): SchemaFactory =
        try {
            SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        } catch (exception: IllegalArgumentException) {
            bundledXercesSchemaFactory() ?: throw exception
        }

    private fun bundledXercesSchemaFactory(): SchemaFactory? =
        runCatching {
            Class.forName("org.apache.xerces.jaxp.validation.XMLSchemaFactory")
                .getDeclaredConstructor()
                .newInstance() as SchemaFactory
        }.getOrNull()

    private fun bundledXercesSaxValidation(xml: String, xsd: String): IofXmlValidationResult? {
        val parser = runCatching {
            Class.forName("org.apache.xerces.parsers.SAXParser")
                .getDeclaredConstructor()
                .newInstance() as XMLReader
        }.getOrNull() ?: return null

        val xsdFile = File.createTempFile("radiooracle-iof-", ".xsd")
        return try {
            xsdFile.writeText(xsd)
            parser.setFeature("http://xml.org/sax/features/validation", true)
            parser.setFeature("http://apache.org/xml/features/validation/schema", true)
            parser.setFeature("http://apache.org/xml/features/validation/schema-full-checking", true)
            parser.setProperty(
                "http://apache.org/xml/properties/schema/external-schemaLocation",
                "$IOF_NAMESPACE ${xsdFile.toURI().toASCIIString()}"
            )
            parser.errorHandler = object : ErrorHandler {
                override fun warning(exception: SAXParseException) = Unit
                override fun error(exception: SAXParseException) {
                    throw exception
                }

                override fun fatalError(exception: SAXParseException) {
                    throw exception
                }
            }
            parser.parse(InputSource(StringReader(xml)))
            IofXmlValidationResult(valid = true, errors = emptyList())
        } catch (exception: SAXParseException) {
            validationFailure(exception)
        } catch (exception: SAXException) {
            validationFailure(exception)
        } catch (exception: Exception) {
            validationFailure(exception)
        } catch (exception: LinkageError) {
            schemaUnavailableFailure(exception)
        } finally {
            xsdFile.delete()
        }
    }

    private fun validationFailure(exception: SAXParseException): IofXmlValidationResult =
        IofXmlValidationResult(
            valid = false,
            errors = listOf(
                IofXmlValidationError(
                    message = exception.message ?: "IOF XML schema validation failed.",
                    lineNumber = exception.lineNumber.takeIf { it > 0 },
                    columnNumber = exception.columnNumber.takeIf { it > 0 }
                )
            )
        )

    private fun validationFailure(exception: Exception): IofXmlValidationResult =
        IofXmlValidationResult(
            valid = false,
            errors = listOf(
                IofXmlValidationError(
                    message = exception.message ?: "IOF XML schema validation failed."
                )
            )
        )

    private fun schemaUnavailableFailure(exception: Throwable): IofXmlValidationResult =
        IofXmlValidationResult(
            valid = false,
            errors = listOf(
                IofXmlValidationError(
                    message = "W3C XML Schema validation is not available in this runtime: " +
                        (exception.message ?: exception::class.simpleName.orEmpty())
                )
            )
        )

    fun requireValid(xml: String, xsd: String) {
        val result = validate(xml, xsd)
        if (!result.valid) {
            throw IofXmlImportException(
                "Invalid IOF XML: ${result.firstMessage ?: "schema validation failed."}"
            )
        }
    }
}
