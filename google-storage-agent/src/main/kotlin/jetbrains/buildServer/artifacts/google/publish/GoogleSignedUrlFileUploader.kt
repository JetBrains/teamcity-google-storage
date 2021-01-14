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

package jetbrains.buildServer.artifacts.google.publish

import com.google.api.client.http.*
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.util.ExponentialBackOff
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.ArtifactPublishingFailedException
import jetbrains.buildServer.artifacts.ArtifactDataInstance
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.serverSide.artifacts.google.GoogleSignedUrlHelper
import kotlinx.coroutines.experimental.*
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class GoogleSignedUrlFileUploader : GoogleFileUploader {

    private val requestFactory = HTTP_TRANSPORT.createRequestFactory()

    override fun publishFiles(build: AgentRunningBuild,
                              pathPrefix: String,
                              filesToPublish: Map<File, String>) = runBlocking {
        return@runBlocking filesToPublish.map({ (file, path) ->
            publishFileAsync(build, file, path, pathPrefix)
        }).map { it.await() }
    }

    private fun publishFileAsync(build: AgentRunningBuild, file: File, path: String, pathPrefix: String) = async(CommonPool, CoroutineStart.DEFAULT) {
        try {
            publishFile(build, file, path, pathPrefix).await()
        } catch (e: Throwable) {
            val filePath = GoogleFileUtils.normalizePath(path, file.name)
            val message = "Failed to publish artifact $filePath: ${e.message}"
            LOG.infoAndDebugDetails(message, e)
            throw ArtifactPublishingFailedException(message, false, e)
        }
    }

    // Upload artifact using resumable method:
    // https://cloud.google.com/storage/docs/xml-api/resumable-upload
    private fun publishFile(build: AgentRunningBuild, file: File, path: String, pathPrefix: String) = async(CommonPool, CoroutineStart.DEFAULT) {
        val filePath = GoogleFileUtils.normalizePath(path, file.name)
        val blobName = GoogleFileUtils.normalizePath(pathPrefix, filePath)
        val contentType = GoogleFileUtils.getContentType(file)
        val signedUrl = getSignedUrl(build, blobName, contentType)
        val locationUrl = getLocationUrl(signedUrl, contentType)

        var range = 0L
        var response: HttpResponse
        val backOff = ExponentialBackOff()
        var backOffInterval: Long
        do {
            if (range < 0) {
                range = getCurrentRange(locationUrl, file)
            }
            val fileLength = file.length()
            val content = FileRangeContent(contentType, file).apply {
                this.range = range
            }
            val putRequest = requestFactory.buildPutRequest(GenericUrl(locationUrl), content).apply {
                if (range > 0) {
                    this.headers.contentRange = "bytes ${range + 1}-${fileLength - 1}/$fileLength"
                }
            }

            response = putRequest.execute()
            if (response.isSuccessStatusCode) {
                return@async ArtifactDataInstance.create(filePath, fileLength)
            } else if (HttpBackOffUnsuccessfulResponseHandler.BackOffRequired.ON_SERVER_ERROR.isRequired(response)) {
                backOffInterval = backOff.nextBackOffMillis()
                val error = getHttpError(response)
                if (backOffInterval != ExponentialBackOff.STOP) {
                    build.buildLogger.message("Failed to publish artifact $filePath: $error. Will retry in ${backOffInterval / 1000} seconds.")
                    delay(backOffInterval, TimeUnit.MILLISECONDS)
                } else {
                    throw IOException(error)
                }
            } else {
                LOG.debug("Response body:\n" + response.parseAsString())
                throw IOException("Invalid response code ${response.statusCode}.")
            }
            range = -1
        } while (backOffInterval != ExponentialBackOff.STOP)

        throw IOException("Unable to complete upload: ${getHttpError(response)}, body: ${response.parseAsString()}")
    }

    private fun getCurrentRange(locationUrl: String, file: File): Long {
        val putRequest = requestFactory.buildPutRequest(GenericUrl(locationUrl), EmptyContent())
        putRequest.headers.contentRange = "bytes */${file.length()}"

        val response = putRequest.execute()
        if (response.statusCode != 308) {
            throw IOException("Can't get current bytes range for upload: ${getHttpError(response)}")
        }

        return response.headers.range.substring("bytes=0-".length).toLong()
    }

    private fun getLocationUrl(signedUrl: String, contentType: String): String {
        val postRequest = requestFactory.buildPostRequest(GenericUrl(signedUrl), EmptyContent())
        postRequest.unsuccessfulResponseHandler = HttpBackOffUnsuccessfulResponseHandler(ExponentialBackOff())
        postRequest.headers.contentType = contentType
        postRequest.headers["x-goog-resumable"] = "start"

        val response = postRequest.execute()
        if (response.statusCode != 201) {
            throw IOException("Can't get upload location URL: ${getHttpError(response)}")
        }

        return response.headers.location ?: throw IOException("Can't get upload location URL")
    }

    private fun getSignedUrl(build: AgentRunningBuild, blobName: String, contentType: String): String {
        val agentConfiguration = build.agentConfiguration
        val targetUrl = "${agentConfiguration.serverUrl}/httpAuth/plugins/${GoogleConstants.STORAGE_TYPE}/${GoogleConstants.SIGNED_URL_PATH}.html"
        val blobPaths = GoogleSignedUrlHelper.writeBlobPaths(mapOf(blobName to contentType))

        val postRequest = requestFactory.buildPostRequest(GenericUrl(targetUrl), ByteArrayContent(APPLICATION_XML, blobPaths.toByteArray()))
        postRequest.unsuccessfulResponseHandler = HttpBackOffUnsuccessfulResponseHandler(ExponentialBackOff())
        postRequest.headers.setBasicAuthentication(build.accessUser, build.accessCode)

        val response = postRequest.execute()
        if (!response.isSuccessStatusCode) {
            throw IOException("Could not get signed upload URL: ${getHttpError(response)}")
        }

        val mapping = GoogleSignedUrlHelper.readSignedUrlMapping(response.parseAsString())
        return mapping[blobName] ?: throw IOException("Could not get signed upload URL: no info for blob $blobName")
    }

    private fun getHttpError(response: HttpResponse) = "${response.statusMessage} (HTTP ${response.statusCode})"

    companion object {
        private val LOG = Logger.getInstance(GoogleSignedUrlFileUploader::class.java.name)
        private const val APPLICATION_XML = "application/xml"
        val HTTP_TRANSPORT: HttpTransport = NetHttpTransport()
    }
}