/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * See LICENSE in the project root for license information.
 */

package jetbrains.buildServer.serverSide.artifacts.google.cleanup

import com.google.cloud.storage.Bucket
import jetbrains.buildServer.artifacts.ArtifactListData
import jetbrains.buildServer.artifacts.ServerArtifactStorageSettingsProvider
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.artifacts.ServerArtifactHelper
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.serverSide.artifacts.google.GoogleUtils
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContext
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContextEx
import jetbrains.buildServer.serverSide.cleanup.CleanupExtension
import jetbrains.buildServer.serverSide.cleanup.CleanupProcessState
import jetbrains.buildServer.serverSide.impl.cleanup.HistoryRetentionPolicy
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.util.positioning.PositionAware
import jetbrains.buildServer.util.positioning.PositionConstraint

class GoogleCleanupExtension(private val helper: ServerArtifactHelper,
                             private val settingsProvider: ServerArtifactStorageSettingsProvider)
    : CleanupExtension, PositionAware {

    override fun getOrderId() = GoogleConstants.STORAGE_TYPE

    override fun cleanupBuildsData(cleanupContext: BuildCleanupContext) {
        for (build in cleanupContext.builds) {
            val artifactsInfo = helper.getArtifactList(build) ?: continue
            val properties = artifactsInfo.commonProperties
            val pathPrefix = GoogleUtils.getPathPrefix(properties) ?: continue

            val patterns = getPatternsForBuild(cleanupContext as BuildCleanupContextEx, build)
            val toDelete = getPathsToDelete(artifactsInfo, patterns).map {
                GoogleUtils.getArtifactPath(properties, it)
            }

            if (toDelete.isEmpty()) continue

            val parameters = settingsProvider.getStorageSettings(build)
            val bucket: Bucket
            try {
                bucket = GoogleUtils.getStorageBucket(parameters)
            } catch (e: Throwable) {
                Loggers.CLEANUP.debug("Failed to connect to bucket in Google Storage: ${e.message}")
                continue
            }

            var succeededNum = 0
            bucket.get(toDelete)?.filterNotNull()?.forEach {
                try {
                    it.delete()
                    succeededNum++
                } catch (e: Throwable) {
                    Loggers.CLEANUP.debug("Failed to remove ${it.selfLink} from Google Storage: ${e.message}")
                }
            }

            val suffix = " from bucket [${bucket.name}] from path [$pathPrefix]"
            Loggers.CLEANUP.info("Removed [" + succeededNum + "] Google Storage " + StringUtil.pluralize("blob", succeededNum) + suffix)

            helper.removeFromArtifactList(build, toDelete)
        }
    }

    override fun afterCleanup(cleanupState: CleanupProcessState) {
    }

    override fun getConstraint() = PositionConstraint.first()

    private fun getPatternsForBuild(cleanupContext: BuildCleanupContextEx, build: SBuild): String {
        val policy = cleanupContext.getCleanupPolicyForBuild(build.buildId)
        return StringUtil.emptyIfNull(policy.parameters[HistoryRetentionPolicy.ARTIFACT_PATTERNS_PARAM])
    }

    private fun getPathsToDelete(artifactsInfo: ArtifactListData, patterns: String): List<String> {
        val keys = artifactsInfo.artifactList.map { it.path }
        return PathPatternFilter(patterns).filterPaths(keys)
    }
}