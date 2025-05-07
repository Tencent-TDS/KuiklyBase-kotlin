// region Tencent Code
/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.objcexport

import org.w3c.dom.Document
import org.xml.sax.SAXException
import java.io.FileNotFoundException
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

internal class ConfigurationUtils(filePath: String?, problemCollector: ObjCExportProblemCollector? = null) {

    private val fileTagName = "file"
    private val moduleTagName = "module"
    private val doc: Document? = parseDocument(filePath, problemCollector)
    private val fileList: List<String>? = getFileTextContents(fileTagName)
    private val moduleList: List<String>? = getModuleTextContents(moduleTagName)

    private fun parseDocument(filePath: String?, problemCollector: ObjCExportProblemCollector? = null): Document? {
        if (filePath == null) return null
        return try {
            val file = java.io.File(filePath)
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            dBuilder.parse(file)
        } catch (e: IllegalArgumentException) {
            problemCollector?.reportWarning("Invalid argument: ${e.message}")
            null
        } catch (e: FileNotFoundException) {
            problemCollector?.reportWarning("File not found: ${e.message}")
            null
        } catch (e: IOException) {
            problemCollector?.reportWarning("I/O error while accessing the file: ${e.message}")
            null
        } catch (e: SAXException) {
            problemCollector?.reportWarning("SAX error while parsing the document: ${e.message}")
            null
        } catch (e: ParserConfigurationException) {
            problemCollector?.reportWarning("Parser configuration error: ${e.message}")
            null
        } catch (e: Exception) {
            problemCollector?.reportWarning("Unexpected error while reading export file $filePath: ${e.message}")
            null
        }
    }

    private fun getFileTextContents(fileTagName: String): List<String>? = getTextContentsByTagName(fileTagName)

    private fun getModuleTextContents(moduleTagName: String): List<String>? = getTextContentsByTagName(moduleTagName)

    private fun getTextContentsByTagName(tagName: String): List<String>? {
        if (doc == null) {
            return null
        }
        val nodeList = doc.getElementsByTagName(tagName)
        return (0 until nodeList.length).map {
            nodeList.item(it).textContent
        }
    }

    fun shouldBeExportByFqName(fqName: String): Boolean = fileList?.contains(fqName) ?: false

    fun shouldBeDefaultExportByModule(moduleName: String): Boolean = moduleList?.contains(moduleName) == true
}
// endregion