/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.serverSide.artifacts.google.web

import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.StorageException
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.BuildPromotion
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.serverSide.artifacts.google.GoogleUtils
import jetbrains.buildServer.serverSide.artifacts.google.signedUrl.GoogleSignedUrlProvider
import jetbrains.buildServer.web.openapi.artifacts.ArtifactDownloadProcessor
import org.springframework.http.HttpHeaders
import java.io.IOException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class GoogleArtifactDownloadProcessor(private val signedUrlProvider: GoogleSignedUrlProvider) : ArtifactDownloadProcessor {

    override fun processDownload(artifactInfo: StoredBuildArtifactInfo,
                                 buildPromotion: BuildPromotion,
                                 request: HttpServletRequest,
                                 response: HttpServletResponse): Boolean {
        val artifactData = artifactInfo.artifactData
                ?: throw IOException("Can not process artifact download request for a folder")

        val path = GoogleUtils.getArtifactPath(artifactInfo.commonProperties, artifactData.path)
        val parameters = artifactInfo.storageSettings

        val result = try {
            signedUrlProvider.getSignedUrl(HttpMethod.GET, path, parameters)
        } catch (e: StorageException) {
            val errorType = if (e.isRetryable) "intermittent" else ""
            val message = "Failed to get signed URL for blob $path due to $errorType Google Cloud Storage error, try to access it later"
            LOG.infoAndDebugDetails(message, e)

            response.status = HttpServletResponse.SC_BAD_GATEWAY
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, e.message)

            return true
        } catch (e: Throwable) {
            val message = "Failed to get signed URL for blob $path from Google Cloud Storage due to unexpected error"
            LOG.warnAndDebugDetails(message, e)
            throw IOException(message + ": " + e.message, e)
        }

        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=" + result.second)
        response.sendRedirect(result.first)

        return true
    }

    override fun getType() = GoogleConstants.STORAGE_TYPE

    companion object {
        private val LOG = Logger.getInstance(GoogleArtifactDownloadProcessor::class.java.name)
    }
}