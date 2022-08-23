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

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.ArtifactsConstants
import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.ArtifactPublishingFailedException
import jetbrains.buildServer.agent.ArtifactsPublisher
import jetbrains.buildServer.agent.CurrentBuildTracker
import jetbrains.buildServer.agent.artifacts.AgentArtifactHelper
import jetbrains.buildServer.artifacts.ArtifactDataInstance
import jetbrains.buildServer.log.LogUtil
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants.PATH_PREFIX_ATTR
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants.PATH_PREFIX_SYSTEM_PROPERTY
import jetbrains.buildServer.serverSide.artifacts.google.GoogleUtils
import jetbrains.buildServer.util.EventDispatcher
import java.io.File
import java.io.IOException

class GoogleArtifactsPublisher(
    dispatcher: EventDispatcher<AgentLifeCycleListener>,
    private val helper: AgentArtifactHelper,
    private val tracker: CurrentBuildTracker
) :
    ArtifactsPublisher {

    private val publishedArtifacts = arrayListOf<ArtifactDataInstance>()
    private var fileUploader: GoogleFileUploader? = null

    init {
        dispatcher.addListener(object : AgentLifeCycleAdapter() {
            override fun buildStarted(build: AgentRunningBuild) {
                publishedArtifacts.clear()
                fileUploader = null
            }
        })
    }

    override fun publishFiles(filePathMap: Map<File, String>): Int {
        val filesToPublish = filePathMap.entries.filter {
            !it.value.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR)
        }.associateTo(hashMapOf()) { entry -> entry.toPair() }

        if (filesToPublish.isNotEmpty()) {
            val build = tracker.currentBuild
            try {
                if (publishedArtifacts.isEmpty()) {
                    setPathPrefixProperty(build)
                }

                val uploader = getFileUploader(build)
                val pathPrefix = getPathPrefixProperty(build)
                val published = uploader.publishFiles(build, pathPrefix, filesToPublish)
                publishedArtifacts.addAll(published)
            } catch (e: ArtifactPublishingFailedException) {
                throw e
            } catch (e: Throwable) {
                val message = "Failed to publish files"
                LOG.warnAndDebugDetails(message, e)
                throw ArtifactPublishingFailedException("$message: ${e.message}", false, e)
            }
            publishArtifactsList(build)
        }

        return filesToPublish.size
    }

    override fun getType() = GoogleConstants.STORAGE_TYPE

    override fun isEnabled() = true

    private fun publishArtifactsList(build: AgentRunningBuild) {
        if (publishedArtifacts.isNotEmpty()) {
            val pathPrefix = getPathPrefixProperty(build)
            val properties = mapOf(PATH_PREFIX_ATTR to pathPrefix)
            try {
                helper.publishArtifactList(publishedArtifacts, properties)
            } catch (e: IOException) {
                build.buildLogger.error(ERROR_PUBLISHING_ARTIFACTS_LIST + ": " + e.message)
                LOG.warnAndDebugDetails(ERROR_PUBLISHING_ARTIFACTS_LIST + "for build " + LogUtil.describe(build), e)
            }
        }
    }

    private fun getFileUploader(build: AgentRunningBuild): GoogleFileUploader {
        val uploader = fileUploader ?: if (GoogleUtils.useSignedUrls(build.artifactStorageSettings)) {
            GoogleSignedUrlFileUploader()
        } else {
            GoogleRegularFileUploader()
        }
        fileUploader = uploader
        return uploader
    }

    private fun setPathPrefixProperty(build: AgentRunningBuild) {
        val pathPrefix = GoogleFileUtils.getPathPrefix(build)
        build.addSharedSystemProperty(PATH_PREFIX_SYSTEM_PROPERTY, pathPrefix)
    }

    private fun getPathPrefixProperty(build: AgentRunningBuild): String {
        return build.sharedBuildParameters.systemProperties[PATH_PREFIX_SYSTEM_PROPERTY]
            ?: throw ArtifactPublishingFailedException(
                "No $PATH_PREFIX_SYSTEM_PROPERTY build system property found",
                false,
                null
            )
    }

    companion object {
        private val LOG = Logger.getInstance(GoogleArtifactsPublisher::class.java.name)
        private const val ERROR_PUBLISHING_ARTIFACTS_LIST = "Error publishing artifacts list"
    }
}
