

package jetbrains.buildServer.artifacts.google.publish

import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.ServerProvidedProperties
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.util.FileUtil
import java.io.File
import java.lang.reflect.Method
import java.net.URLConnection

object GoogleFileUtils {

    fun normalizePath(path: String, fileName: String): String {
        return if (path.isEmpty()) {
            fileName
        } else {
            FileUtil.normalizeRelativePath("$path$SLASH$fileName")
        }
    }

    /**
     * Calculates path prefix.
     */
    fun getPathPrefix(build: AgentRunningBuild): String {
        val pathSegments = arrayListOf<String>()

        // Try to get overridden path prefix
        val pathPrefix = build.sharedConfigParameters[GoogleConstants.PATH_PREFIX_SYSTEM_PROPERTY]
        if (pathPrefix == null) {
            // Set default path prefix
            build.sharedConfigParameters[ServerProvidedProperties.TEAMCITY_PROJECT_ID_PARAM]?.let {
                pathSegments.add(it)
            }
            pathSegments.add(build.buildTypeExternalId)
            pathSegments.add(build.buildId.toString())
        } else {
            pathSegments.addAll(
                pathPrefix
                    .trim()
                    .replace('\\', SLASH)
                    .split(SLASH)
                    .filter { it.isNotEmpty() }
            )
        }

        return pathSegments.joinToString("$SLASH")
    }

    fun getContentType(file: File): String {
        URLConnection.guessContentTypeFromName(file.name)?.let {
            return it
        }

        if (probeContentTypeMethod != null && fileToPathMethod != null) {
            try {
                probeContentTypeMethod.invoke(null, fileToPathMethod.invoke(file))?.let {
                    if (it is String) {
                        return it
                    }
                }
            } catch (ignored: Exception) {
            }
        }

        return DEFAULT_CONTENT_TYPE
    }

    private fun getProbeContentTypeMethod(): Method? {
        try {
            val filesClass = Class.forName("java.nio.file.Files")
            val pathClass = Class.forName("java.nio.file.Path")
            if (filesClass != null && pathClass != null) {
                return filesClass.getMethod("probeContentType", pathClass)
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    private fun getFileToPathMethod(): Method? {
        try {
            return File::class.java.getMethod("toPath")
        } catch (ignored: Exception) {
        }
        return null
    }

    private const val SLASH = '/'
    private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"
    private val probeContentTypeMethod: Method? = getProbeContentTypeMethod()
    private val fileToPathMethod: Method? = getFileToPathMethod()
}