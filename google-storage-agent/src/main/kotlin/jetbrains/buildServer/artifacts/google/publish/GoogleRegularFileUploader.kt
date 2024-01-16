

package jetbrains.buildServer.artifacts.google.publish

import com.google.api.client.util.ExponentialBackOff
import com.google.cloud.storage.StorageException
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.artifacts.ArtifactDataInstance
import jetbrains.buildServer.serverSide.artifacts.google.GoogleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class GoogleRegularFileUploader : GoogleFileUploader {

    override fun publishFiles(
        build: AgentRunningBuild,
        pathPrefix: String,
        filesToPublish: Map<File, String>
    ) = runBlocking {
        val bucket = GoogleUtils.getStorageBucket(build.artifactStorageSettings)

        filesToPublish.map { (file, path) ->
            FilePublishingContext(file, path, pathPrefix)
        }.map {
            async(Dispatchers.IO) {
                var scopedBucket = bucket
                with(it) {
                    exceptionsWrapper {
                        retry(
                            build.buildLogger,
                            ExponentialBackOff(),
                            {
                                logExceptions(build.buildLogger) {
                                    // lock self for better readability of the next block
                                    val context = this

                                    // ensure correct context
                                    // otherwise there is a possibility of context switch
                                    // Dispatchers.IO provides more threads for parallel work
                                    withContext(Dispatchers.IO) {
                                        FileInputStream(context.file).use { fis ->
                                            scopedBucket.create(context.blobName, fis, context.contentType)
                                            ArtifactDataInstance.create(context.filePath, context.file.length())
                                        }
                                    }
                                }
                            },
                            { err ->
                                // Reconnect to the cloud with fresh token
                                // if current exception isn't retryable storage exception.
                                // In some cases it is vital for correct upload to get fresh cloud token
                                // to avoid com.google.cloud.resourcemanager.ResourceManagerException: Error getting access token for service account
                                if (!(err is StorageException && err.isRetryable)) {
                                    scopedBucket = GoogleUtils.getStorageBucket(build.artifactStorageSettings)
                                }
                            }
                        )
                    }
                }
            }
        }.map { it.await() }
    }
}