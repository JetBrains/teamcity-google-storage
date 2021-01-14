/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.serverSide.artifacts.google

import com.intellij.openapi.util.JDOMUtil
import jetbrains.buildServer.util.StringUtil
import org.jdom.Document
import org.jdom.Element
import org.jdom.JDOMException

import java.net.URL
import java.util.*

/**
 * Provide helper methods to exchange Signed URL
 */
object GoogleSignedUrlHelper {
    private val SIGNED_URL_MAPPINGS = "signedUrlMappings"
    private val SIGNED_URL_MAPPING = "signedUrlMapping"
    private val BLOB_PATHS = "blobPaths"
    private val BLOB_PATH = "blobPath"
    private val BLOB_CONTENT_TYPE = "contentType"
    private val SIGNED_URL = "signedUrl"

    fun readSignedUrlMapping(data: String): Map<String, String> {
        val document: Document
        try {
            document = JDOMUtil.loadDocument(data)
        } catch (e: JDOMException) {
            return emptyMap()
        }

        val rootElement = document.rootElement
        if (rootElement.name != SIGNED_URL_MAPPINGS) return emptyMap()
        val result = HashMap<String, String>()
        for (mapEntryElement in rootElement.getChildren(SIGNED_URL_MAPPING)) {
            val mapEntryElementCasted = mapEntryElement as Element
            val s3ObjectKey = mapEntryElementCasted.getChild(BLOB_PATH).value
            val preSignUrlString = mapEntryElementCasted.getChild(SIGNED_URL).value
            result.put(s3ObjectKey, preSignUrlString)
        }
        return result
    }

    fun writeSignedUrlMapping(data: Map<String, URL>): String {
        val rootElement = Element(SIGNED_URL_MAPPINGS)
        for (s3ObjectKey in data.keys) {
            val preSignUrl = data[s3ObjectKey]
            val mapEntry = Element(SIGNED_URL_MAPPING)
            val preSignUrlElement = Element(SIGNED_URL)
            preSignUrlElement.addContent(preSignUrl.toString())
            mapEntry.addContent(preSignUrlElement)
            val s3ObjectKeyElement = Element(BLOB_PATH)
            s3ObjectKeyElement.addContent(s3ObjectKey)
            mapEntry.addContent(s3ObjectKeyElement)
            rootElement.addContent(mapEntry)
        }
        return JDOMUtil.writeDocument(Document(rootElement), System.getProperty("line.separator"))
    }

    fun readBlobPaths(data: String): Map<String, String> {
        val document: Document
        try {
            document = JDOMUtil.loadDocument(data)
        } catch (e: JDOMException) {
            return emptyMap()
        }

        val rootElement = document.rootElement
        if (rootElement.name != BLOB_PATHS) {
            return emptyMap()
        }

        val result = hashMapOf<String, String>()
        for (element in rootElement.getChildren(BLOB_PATH)) {
            val elementCasted = element as Element
            result[elementCasted.value] = elementCasted.getAttributeValue(BLOB_CONTENT_TYPE)
        }
        return result
    }

    fun writeBlobPaths(blobPaths: Map<String, String>): String {
        val rootElement = Element(BLOB_PATHS)
        for ((blobPath, contentType) in blobPaths) {
            if (StringUtil.isEmpty(blobPath)) continue
            val xmlElement = Element(BLOB_PATH)
            xmlElement.addContent(blobPath)
            xmlElement.setAttribute(BLOB_CONTENT_TYPE, contentType)
            rootElement.addContent(xmlElement)
        }
        return JDOMUtil.writeDocument(Document(rootElement), System.getProperty("line.separator"))
    }
}
