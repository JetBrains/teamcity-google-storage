/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.EmptyContent
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler
import com.google.api.client.http.HttpResponse
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.util.ExponentialBackOff
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.artifacts.ArtifactDataInstance
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.serverSide.artifacts.google.GoogleSignedUrlHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

class GoogleSignedUrlFileUploader : GoogleFileUploader {

    private val requestFactory = HTTP_TRANSPORT.createRequestFactory()

    override fun publishFiles(
        build: AgentRunningBuild,
        pathPrefix: String,
        filesToPublish: Map<File, String>
    ) = runBlocking {
        filesToPublish.map { (file, path) ->
            FilePublishingContext(file, path, pathPrefix)
        }.map {
            var signedUrl = getSignedUrl(build, it.blobName, it.contentType)
            var locationUrl = getLocationUrl(signedUrl, it.contentType)

            async(Dispatchers.IO) {
                with(it) {
                    exceptionsWrapper {
                        var range = 0L
                        var response: HttpResponse

                        retry(
                            build.buildLogger,
                            ExponentialBackOff(),
                            {
                                logExceptions(build.buildLogger) {
                                    if (range < 0) {
                                        range = getCurrentRange(locationUrl, file)
                                    }
                                    val fileLength = file.length()
                                    val content = FileRangeContent(contentType, file).apply {
                                        this.range = range
                                    }
                                    val putRequest =
                                        requestFactory.buildPutRequest(GenericUrl(locationUrl), content).apply {
                                            if (range > 0) {
                                                this.headers.contentRange =
                                                    "bytes ${range + 1}-${fileLength - 1}/$fileLength"
                                            }
                                        }

                                    response = putRequest.execute()
                                    // reset range for non-success cases
                                    range = -1

                                    when {
                                        response.isSuccessStatusCode -> {
                                            ArtifactDataInstance.create(filePath, fileLength)
                                        }

                                        HttpBackOffUnsuccessfulResponseHandler.BackOffRequired.ON_SERVER_ERROR.isRequired(
                                            response
                                        ) -> {
                                            // will throw regular IllegalStateException
                                            // which will allow regular retry mechanism to handle it (see also handler below)
                                            error(getHttpError(response))
                                        }

                                        else -> {
                                            throw IOException("Invalid response code ${response.statusCode}. Response body:\n" + response.parseAsString())
                                        }
                                    }
                                }
                            },
                            { err ->
                                if (err !is IllegalStateException) {
                                    // let's try to recreate signed URL
                                    signedUrl = getSignedUrl(build, it.blobName, it.contentType)
                                    locationUrl = getLocationUrl(signedUrl, it.contentType)
                                }
                            }
                        )
                    }
                }
            }
        }.map { it.await() }
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
        LOG.debug("signed URL: $signedUrl")
        LOG.debug("contentType: $contentType")
        val postRequest = requestFactory.buildPostRequest(GenericUrl(signedUrl), EmptyContent())
        postRequest.unsuccessfulResponseHandler = HttpBackOffUnsuccessfulResponseHandler(ExponentialBackOff())
        postRequest.headers.contentType = contentType
        postRequest.headers["x-goog-resumable"] = "start"

        val response = postRequest.execute()
        if (response.statusCode != 201) {
            throw IOException("Can't get upload location URL: ${getHttpError(response)}")
        }

        return response.headers.location.also {
            LOG.debug("location URL: $it")
        } ?: throw IOException("Can't get upload location URL")
    }

    private fun getSignedUrl(build: AgentRunningBuild, blobName: String, contentType: String): String {
        val agentConfiguration = build.agentConfiguration
        val targetUrl =
            "${agentConfiguration.serverUrl}/httpAuth/plugins/${GoogleConstants.STORAGE_TYPE}/${GoogleConstants.SIGNED_URL_PATH}.html"
        val blobPaths = GoogleSignedUrlHelper.writeBlobPaths(mapOf(blobName to contentType))

        val postRequest = requestFactory.buildPostRequest(
            GenericUrl(targetUrl),
            ByteArrayContent(APPLICATION_XML, blobPaths.toByteArray())
        )
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
        private const val APPLICATION_XML = "application/xml"
        val HTTP_TRANSPORT: HttpTransport = NetHttpTransport()
        private val LOG = Logger.getInstance(GoogleSignedUrlFileUploader::class.java.name)
    }
}
