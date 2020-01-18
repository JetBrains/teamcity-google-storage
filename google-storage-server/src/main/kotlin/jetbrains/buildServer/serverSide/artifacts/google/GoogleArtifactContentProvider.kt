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

package jetbrains.buildServer.serverSide.artifacts.google

import com.google.cloud.storage.StorageException
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.artifacts.ArtifactContentProvider
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo
import java.io.IOException
import java.io.InputStream
import java.nio.channels.Channels

class GoogleArtifactContentProvider : ArtifactContentProvider {

    override fun getContent(artifactInfo: StoredBuildArtifactInfo): InputStream {
        val artifactData = artifactInfo.artifactData
                ?: throw IOException("Can not process artifact download request for a folder")

        val path = GoogleUtils.getArtifactPath(artifactInfo.commonProperties, artifactData.path)

        try {
            val bucket = GoogleUtils.getStorageBucket(artifactInfo.storageSettings)
            val blob = bucket.get(path)
            return Channels.newInputStream(blob.reader())
        } catch (e: StorageException) {
            val errorType = if (e.isRetryable) "intermittent" else ""
            val message = "Failed to access artifact $path due to $errorType Google Cloud Storage error, try to access it later"
            LOG.infoAndDebugDetails(message, e)
            throw IOException("$message: $e.message", e)
        } catch (e: Throwable) {
            val message = "Failed to access artifact $path from Google Cloud Storage due to unexpected error"
            LOG.warnAndDebugDetails(message, e)
            throw IOException("$message: $e.message", e)
        }
    }

    override fun getType() = GoogleConstants.STORAGE_TYPE

    companion object {
        private val LOG = Logger.getInstance(GoogleArtifactContentProvider::class.java.name)
    }
}