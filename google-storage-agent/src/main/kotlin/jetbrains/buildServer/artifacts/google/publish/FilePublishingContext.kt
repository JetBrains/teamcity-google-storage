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

import com.google.api.client.util.ExponentialBackOff
import com.google.cloud.storage.StorageException
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.ArtifactPublishingFailedException
import jetbrains.buildServer.agent.BuildProgressLogger
import kotlinx.coroutines.delay
import java.io.File

private val LOG = Logger.getInstance(FilePublishingContext::class.java.name)

// Log exception and throw it forward
suspend fun <T> FilePublishingContext.logExceptions(
    buildLogger: BuildProgressLogger,
    block: suspend FilePublishingContext.() -> T
): T {
    try {
        return block()
    } catch (e: Exception) {
        LOG.infoAndDebugDetails("Failed to publish artifact ${this.filePath}: ${e.message}", e)

        if (e is StorageException) {
            if (e.isRetryable) {
                LOG.info(e.message)
            } else {
                LOG.warn(e.message)
                buildLogger.error(e.message)
            }
        }

        throw e
    }
}

suspend fun <T> FilePublishingContext.retry(
    buildLogger: BuildProgressLogger,
    backOff: ExponentialBackOff,
    block: suspend FilePublishingContext.() -> T,
    handleError: (e: Exception) -> Unit
): T {
    var backOffInterval: Long
    do {
        try {
            return block()
        } catch (e: Exception) {
            handleError(e)

            backOffInterval = backOff.nextBackOffMillis()
            buildLogger.message("Failed to publish artifact ${this.filePath}: ${e.message}. Will retry in ${backOffInterval / 1000} seconds.")
            delay(backOffInterval)
        }
    } while (backOffInterval != ExponentialBackOff.STOP)

    throw throw ArtifactPublishingFailedException("Unable to publish artifact ${this.filePath}", false, null)
}

suspend fun <T> FilePublishingContext.exceptionsWrapper(block: suspend FilePublishingContext.() -> T): T {
    try {
        return block()
    } catch (e: ArtifactPublishingFailedException) {
        throw e
    } catch (e: Exception) {
        val message = "Failed to publish artifact ${this.filePath}: ${e.message}"
        throw ArtifactPublishingFailedException(message, false, e)
    }
}

class FilePublishingContext(
    val file: File,
    path: String,
    pathPrefix: String
) {
    val filePath = GoogleFileUtils.normalizePath(path, file.name)
    val blobName = GoogleFileUtils.normalizePath(pathPrefix, filePath)
    val contentType = GoogleFileUtils.getContentType(file)
}
