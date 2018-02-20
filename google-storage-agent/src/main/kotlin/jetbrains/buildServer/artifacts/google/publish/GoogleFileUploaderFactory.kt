package jetbrains.buildServer.artifacts.google.publish

import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.serverSide.artifacts.google.GoogleUtils

object GoogleFileUploaderFactory {
    fun getFileUploader(build: AgentRunningBuild): GoogleFileUploader {
        return if (GoogleUtils.useSignedUrls(build.artifactStorageSettings)) {
            GoogleSignedUrlFileUploader()
        } else {
            GoogleRegularFileUploader()
        }
    }
}