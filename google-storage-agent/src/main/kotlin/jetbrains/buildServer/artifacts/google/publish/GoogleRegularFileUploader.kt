package jetbrains.buildServer.artifacts.google.publish

import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.artifacts.ArtifactDataInstance
import jetbrains.buildServer.serverSide.artifacts.google.GoogleUtils
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentLinkedQueue

class GoogleRegularFileUploader : GoogleFileUploader {

    override fun publishFiles(build: AgentRunningBuild,
                              pathPrefix: String,
                              filesToPublish: Map<File, String>): Collection<ArtifactDataInstance> {
        val bucket = GoogleUtils.getStorageBucket(build.artifactStorageSettings)
        val publishedArtifacts = ConcurrentLinkedQueue<ArtifactDataInstance>()

        filesToPublish.forEach { (file, path) ->
            val filePath = GoogleFileUtils.normalizePath(path, file.name)
            val blobName = GoogleFileUtils.normalizePath(pathPrefix, filePath)
            val contentType = GoogleFileUtils.getContentType(file)

            FileInputStream(file).use {
                bucket.create(blobName, it, contentType)
                val length = file.length()
                val artifact = ArtifactDataInstance.create(filePath, length)
                publishedArtifacts.add(artifact)
            }
        }

        return publishedArtifacts
    }
}