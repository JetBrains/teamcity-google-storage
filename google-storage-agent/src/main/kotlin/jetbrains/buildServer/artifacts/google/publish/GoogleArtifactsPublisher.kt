/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * See LICENSE in the project root for license information.
 */

package jetbrains.buildServer.artifacts.google.publish

import com.google.cloud.storage.StorageException
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.ArtifactsConstants
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.agent.artifacts.AgentArtifactHelper
import jetbrains.buildServer.artifacts.ArtifactDataInstance
import jetbrains.buildServer.log.LogUtil
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants.PATH_PREFIX_ATTR
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants.PATH_PREFIX_SYSTEM_PROPERTY
import jetbrains.buildServer.serverSide.artifacts.google.GoogleUtils
import jetbrains.buildServer.util.EventDispatcher
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URI

class GoogleArtifactsPublisher(dispatcher: EventDispatcher<AgentLifeCycleListener>,
                               private val helper: AgentArtifactHelper,
                               private val tracker: CurrentBuildTracker)
    : ArtifactsPublisher {

    private val publishedArtifacts = arrayListOf<ArtifactDataInstance>()

    init {
        dispatcher.addListener(object : AgentLifeCycleAdapter() {
            override fun buildStarted(build: AgentRunningBuild) {
                publishedArtifacts.clear()
            }

            override fun afterAtrifactsPublished(build: AgentRunningBuild, status: BuildFinishedStatus) {
                publishArtifactsList(build)
            }
        })
    }

    override fun publishFiles(filePathMap: Map<File, String>): Int {
        val filesToPublish = filePathMap.entries.filter {
            !it.value.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR)
        }

        if (filesToPublish.isNotEmpty()) {
            val build = tracker.currentBuild
            try {
                val parameters = publisherParameters

                if (publishedArtifacts.isEmpty()) {
                    setPathPrefixProperty(build)
                }

                val bucket = GoogleUtils.getStorageBucket(parameters)
                val pathPrefix = getPathPrefixProperty(build)

                filesToPublish.forEach { (file, path) ->
                    val filePath = preparePath(path, file)
                    val blobName = "$pathPrefix$SLASH$filePath"

                    FileInputStream(file).use {
                        bucket.create(blobName, it)
                        val length = file.length()
                        val artifact = ArtifactDataInstance.create(filePath, length)
                        publishedArtifacts.add(artifact)
                    }
                }
            } catch (e: Throwable) {
                val message = "Failed to publish files"
                LOG.warnAndDebugDetails(message, e)

                if (e is StorageException) {
                    LOG.warn(e.message)
                    build.buildLogger.error(e.message)
                }

                throw ArtifactPublishingFailedException("$message: ${e.message}", false, e)
            }
        }

        return filesToPublish.size
    }

    private fun preparePath(path: String, file: File): String {
        if (path.startsWith(".."))
            throw IOException("Attempting to publish artifact outside of build artifacts directory. Specified target path: \"$path\"")

        return if (path.isEmpty()) {
            file.name
        } else {
            URI("$path$SLASH${file.name}").normalize().path
        }
    }

    override fun getType() = GoogleConstants.STORAGE_TYPE

    override fun isEnabled() = true

    private val publisherParameters get() = tracker.currentBuild.artifactStorageSettings

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

    private fun setPathPrefixProperty(build: AgentRunningBuild) {
        val pathPrefix = getPathPrefix(build)
        build.addSharedSystemProperty(PATH_PREFIX_SYSTEM_PROPERTY, pathPrefix)
    }

    private fun getPathPrefixProperty(build: AgentRunningBuild): String {
        return build.sharedBuildParameters.systemProperties[PATH_PREFIX_SYSTEM_PROPERTY] ?:
                throw ArtifactPublishingFailedException("No $PATH_PREFIX_SYSTEM_PROPERTY build system property found", false, null)
    }

    /**
     * Calculates path prefix.
     */
    private fun getPathPrefix(build: AgentRunningBuild): String {
        // Try to get overriden path prefix
        val pathSegments = (build.sharedConfigParameters[PATH_PREFIX_SYSTEM_PROPERTY] ?: "")
                .trim()
                .replace('\\', SLASH)
                .split(SLASH)
                .filter { it.isNotEmpty() }
                .toMutableList()

        // Set default path prefix
        if (pathSegments.isEmpty()) {
            build.sharedConfigParameters[ServerProvidedProperties.TEAMCITY_PROJECT_ID_PARAM]?.let {
                pathSegments.add(it)
            }
            pathSegments.add(build.buildTypeExternalId)
            pathSegments.add(build.buildId.toString())
        }


        return pathSegments.joinToString("$SLASH")
    }

    companion object {
        private val LOG = Logger.getInstance(GoogleArtifactsPublisher::class.java.name)
        private val ERROR_PUBLISHING_ARTIFACTS_LIST = "Error publishing artifacts list"
        private val SLASH = '/'
    }
}
