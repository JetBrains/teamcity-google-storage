/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * See LICENSE in the project root for license information.
 */

package jetbrains.buildServer.serverSide.artifacts.google.web

import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.BuildPromotion
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.serverSide.artifacts.google.GoogleUtils
import jetbrains.buildServer.web.openapi.artifacts.ArtifactDownloadProcessor
import org.springframework.http.HttpHeaders
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class GoogleArtifactDownloadProcessor : ArtifactDownloadProcessor {
    private val myLinksCache = CacheBuilder.newBuilder()
            .expireAfterWrite(urlLifeTime.toLong(), TimeUnit.SECONDS)
            .maximumSize(100)
            .build<String, String>()

    override fun processDownload(artifactInfo: StoredBuildArtifactInfo,
                                 buildPromotion: BuildPromotion,
                                 request: HttpServletRequest,
                                 response: HttpServletResponse): Boolean {
        val artifactData = artifactInfo.artifactData
                ?: throw IOException("Can not process artifact download request for a folder")

        val path = GoogleUtils.getArtifactPath(artifactInfo.commonProperties, artifactData.path)
        val parameters = artifactInfo.storageSettings
        val lifeTime = urlLifeTime

        val url = try {
            myLinksCache.get(getIdentity(parameters, path), {
                val bucket = GoogleUtils.getStorageBucket(parameters)
                val blob = bucket.get(path)
                val httpMethod = Storage.SignUrlOption.httpMethod(HttpMethod.GET)
                blob.signUrl(lifeTime.toLong(), TimeUnit.SECONDS, httpMethod).toString()
            })
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

        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=" + lifeTime)
        response.sendRedirect(url)

        return true
    }

    override fun getType() = GoogleConstants.STORAGE_TYPE

    private val urlLifeTime
        get() = TeamCityProperties.getInteger(
                GoogleConstants.URL_LIFETIME_SEC,
                GoogleConstants.DEFAULT_URL_LIFETIME_SEC)

    private fun getIdentity(params: Map<String, String>, path: String): String {
        return StringBuilder().apply {
            append(params[GoogleConstants.PARAM_ACCESS_KEY])
            append(params[GoogleConstants.PARAM_BUCKET_NAME])
            append(path)
        }.toString().toLowerCase().hashCode().toString()
    }

    companion object {
        private val LOG = Logger.getInstance(GoogleArtifactDownloadProcessor::class.java.name)
    }
}