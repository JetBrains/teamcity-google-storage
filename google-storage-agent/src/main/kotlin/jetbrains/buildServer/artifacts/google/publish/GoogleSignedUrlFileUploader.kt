package jetbrains.buildServer.artifacts.google.publish

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.ArtifactPublishingFailedException
import jetbrains.buildServer.artifacts.ArtifactDataInstance
import jetbrains.buildServer.http.HttpUtil
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.serverSide.artifacts.google.GoogleSignedUrlHelper
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.UsernamePasswordCredentials
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.PutMethod
import org.apache.commons.httpclient.methods.StringRequestEntity
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue

class GoogleSignedUrlFileUploader : GoogleFileUploader {

    override fun publishFiles(build: AgentRunningBuild,
                              pathPrefix: String,
                              filesToPublish: Map<File, String>): Collection<ArtifactDataInstance> {
        val publishedArtifacts = ConcurrentLinkedQueue<ArtifactDataInstance>()

        filesToPublish.forEach({ file: File, path: String ->
            val artifact = try {
                publishFile(build, file, path, pathPrefix)
            } catch (e: Throwable) {
                val filePath = GoogleFileUtils.normalizePath(path, file.name)
                val message = "Failed to publish artifact $filePath: ${e.message}"
                LOG.infoAndDebugDetails(message, e)
                throw ArtifactPublishingFailedException(message, false, e)
            }
            publishedArtifacts.add(artifact)
        })

        return publishedArtifacts
    }

    // Upload artifact using resumable method:
    // // https://cloud.google.com/storage/docs/xml-api/resumable-upload
    private fun publishFile(build: AgentRunningBuild, file: File, path: String, pathPrefix: String): ArtifactDataInstance {
        val filePath = GoogleFileUtils.normalizePath(path, file.name)
        val blobName = GoogleFileUtils.normalizePath(pathPrefix, filePath)
        val contentType = GoogleFileUtils.getContentType(file)
        val signedUrl = getSignedUrl(build, blobName, contentType)
        val httpClient = HttpUtil.createHttpClient(build.agentConfiguration.serverConnectionTimeout)
        val locationUrl = getLocationUrl(httpClient, signedUrl, contentType)

        var retryCount = 3
        var range = 0L
        var putMethod: PutMethod
        var responseCode = 0
        var exception: Throwable? = null
        do {
            if (range < 0) {
                range = getCurrentRange(httpClient, locationUrl, file)
            }
            val fileLength = file.length()
            putMethod = PutMethod(locationUrl).apply {
                requestEntity = FileRangeRequestEntity(file, contentType).apply {
                    this.range = range
                }
                if (range > 0) {
                    addRequestHeader("Content-Range", "bytes ${range+1}-${fileLength - 1}/$fileLength")
                }
            }
            try {
                responseCode = httpClient.executeMethod(putMethod)
                if (responseCode == 200 || responseCode == 201) {
                    return ArtifactDataInstance.create(filePath, fileLength)
                }
            } catch (e: Throwable) {
                LOG.debug("Upload was interrupted, will retry again", e)
                exception = e
            }
            range = -1
        } while (retryCount-- > 0)

        if (exception != null) {
            throw exception
        }

        throw IOException("Unable to complete upload, status code: $responseCode, body: ${putMethod.responseBodyAsString}")
    }

    private fun getCurrentRange(httpClient: HttpClient, locationUrl: String, file: File): Long {
        val putMethod = PutMethod(locationUrl)
        putMethod.addRequestHeader("Content-Range", "bytes */${file.length()}")
        val responseCode = httpClient.executeMethod(putMethod)
        if (responseCode == 308) {
            throw IOException("Can't get upload location URL.")
        }
        return putMethod.getResponseHeader("Range").value.substring("bytes=0-".length).toLong()
    }

    private fun getLocationUrl(httpClient: HttpClient, signedUrl: String, contentType: String): String {
        val postMethod = PostMethod(signedUrl)
        postMethod.addRequestHeader("Content-Type", contentType)
        postMethod.addRequestHeader("x-goog-resumable", "start")
        val responseCode = httpClient.executeMethod(postMethod)
        if (responseCode != 201) {
            throw IOException("Can't get upload location URL.")
        }

        return postMethod.getResponseHeader("Location")?.value ?: throw IOException("Can't get upload location URL.")
    }

    private fun getSignedUrl(build: AgentRunningBuild, blobName: String, contentType: String): String {
        val agentConfiguration = build.agentConfiguration
        val targetUrl = "${agentConfiguration.serverUrl}/httpAuth/plugins/${GoogleConstants.STORAGE_TYPE}/${GoogleConstants.SIGNED_URL_PATH}.html"
        val connectionTimeout = agentConfiguration.serverConnectionTimeout
        val credentials = UsernamePasswordCredentials(build.accessUser, build.accessCode)
        val httpClient = HttpUtil.createHttpClient(connectionTimeout, URL(targetUrl), credentials)
        val postMethod = PostMethod(targetUrl)
        val blobPaths = GoogleSignedUrlHelper.writeBlobPaths(mapOf(blobName to contentType))
        postMethod.requestEntity = StringRequestEntity(blobPaths, APPLICATION_XML, UTF_8)
        postMethod.doAuthentication = true
        val responseCode = httpClient.executeMethod(postMethod)
        if (responseCode != 200) {
            throw IOException("Can't get signed upload URL.")
        }

        val mapping = GoogleSignedUrlHelper.readSignedUrlMapping(postMethod.responseBodyAsString)
        return mapping[blobName] ?: throw IOException("Can't get signed upload URL.")
    }

    companion object {
        private val LOG = Logger.getInstance(GoogleSignedUrlFileUploader::class.java.name)
        private val APPLICATION_XML = "application/xml"
        private val UTF_8 = "UTF-8"
    }
}