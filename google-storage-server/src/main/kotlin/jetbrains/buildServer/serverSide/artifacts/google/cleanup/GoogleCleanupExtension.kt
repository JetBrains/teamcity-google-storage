

package jetbrains.buildServer.serverSide.artifacts.google.cleanup

import com.google.cloud.storage.Bucket
import jetbrains.buildServer.artifacts.ServerArtifactStorageSettingsProvider
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.artifacts.ServerArtifactHelper
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.serverSide.artifacts.google.GoogleUtils
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContext
import jetbrains.buildServer.serverSide.cleanup.BuildCleanupContextEx
import jetbrains.buildServer.serverSide.cleanup.CleanupExtension
import jetbrains.buildServer.serverSide.cleanup.CleanupProcessState
import jetbrains.buildServer.serverSide.impl.cleanup.ArtifactPathsEvaluator
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
            val pathPrefix = GoogleUtils.getPathPrefix(artifactsInfo.commonProperties) ?: continue

            val pathsToDelete = ArtifactPathsEvaluator.getPathsToDelete(cleanupContext as BuildCleanupContextEx, build, artifactsInfo)
            if (pathsToDelete.isEmpty()) continue

            val parameters = settingsProvider.getStorageSettings(build)
            val bucket: Bucket
            try {
                bucket = GoogleUtils.getStorageBucket(parameters)
            } catch (e: Throwable) {
                Loggers.CLEANUP.debug("Failed to connect to bucket in Google Storage: ${e.message}")
                continue
            }

            var succeededNum = 0
            val blobs = pathsToDelete.map {
                GoogleUtils.getArtifactPath(artifactsInfo.commonProperties, it)
            }

            bucket.get(blobs)?.filterNotNull()?.forEach {
                try {
                    it.delete()
                    succeededNum++
                } catch (e: Throwable) {
                    Loggers.CLEANUP.debug("Failed to remove ${it.selfLink} from Google Storage: ${e.message}")
                }
            }

            val suffix = " from bucket [${bucket.name}] from path [$pathPrefix]"
            Loggers.CLEANUP.info("Removed [" + succeededNum + "] Google Storage " + StringUtil.pluralize("blob", succeededNum) + suffix)

            helper.removeFromArtifactList(build, pathsToDelete)
        }
    }

    override fun afterCleanup(cleanupState: CleanupProcessState) {
    }

    override fun getConstraint() = PositionConstraint.first()

}