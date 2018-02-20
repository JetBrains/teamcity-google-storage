package jetbrains.buildServer.artifacts.google.publish

import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.artifacts.ArtifactDataInstance
import java.io.File

interface GoogleFileUploader {
    fun publishFiles(build: AgentRunningBuild,
                     pathPrefix: String,
                     filesToPublish: Map<File, String>): Collection<ArtifactDataInstance>
}