package jetbrains.buildServer.artifacts.google.publish

import com.google.cloud.storage.Bucket
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.ArtifactPublishingFailedException
import jetbrains.buildServer.artifacts.ArtifactDataInstance
import jetbrains.buildServer.serverSide.artifacts.google.GoogleUtils
import kotlinx.coroutines.experimental.*
import java.io.File
import java.io.FileInputStream

class GoogleRegularFileUploader : GoogleFileUploader {

    override fun publishFiles(build: AgentRunningBuild,
                              pathPrefix: String,
                              filesToPublish: Map<File, String>) = runBlocking {
        val bucket = GoogleUtils.getStorageBucket(build.artifactStorageSettings)
        return@runBlocking filesToPublish.map { (file, path) ->
            publishArtifactAsync(bucket, pathPrefix, file, path)
        }.map { it.await() }
    }

    private fun publishArtifactAsync(bucket: Bucket, pathPrefix: String, file: File, path: String) = async(CommonPool, CoroutineStart.DEFAULT) {
        val filePath = GoogleFileUtils.normalizePath(path, file.name)
        val blobName = GoogleFileUtils.normalizePath(pathPrefix, filePath)
        val contentType = GoogleFileUtils.getContentType(file)

        try {
            FileInputStream(file).use {
                bucket.create(blobName, it, contentType)
                val length = file.length()
                return@async ArtifactDataInstance.create(filePath, length)
            }
        } catch (e: Throwable) {
            val message = "Failed to publish artifact $filePath: ${e.message}"
            LOG.infoAndDebugDetails(message, e)
            throw ArtifactPublishingFailedException(message, false, e)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(GoogleRegularFileUploader::class.java.name)
    }
}