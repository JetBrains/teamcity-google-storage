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

package jetbrains.buildServer.serverSide.artifacts.google.web

import com.google.cloud.storage.HttpMethod
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.BuildAuthUtil
import jetbrains.buildServer.artifacts.ServerArtifactStorageSettingsProvider
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.controllers.interceptors.auth.util.AuthorizationHeader
import jetbrains.buildServer.serverSide.RunningBuildEx
import jetbrains.buildServer.serverSide.RunningBuildsCollection
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.serverSide.artifacts.google.GoogleSignedUrlHelper
import jetbrains.buildServer.serverSide.artifacts.google.signedUrl.GoogleSignedUrlProvider
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.web.servlet.ModelAndView
import java.io.IOException
import java.net.URL
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class GoogleSignedUrlController(
    server: SBuildServer,
    manager: WebControllerManager,
    descriptor: PluginDescriptor,
    private val runningBuildsCollection: RunningBuildsCollection,
    private val storageSettingsProvider: ServerArtifactStorageSettingsProvider,
    private val signedUrlProvider: GoogleSignedUrlProvider
) : BaseController(server) {

    init {
        val path = descriptor.getPluginResourcesPath(GoogleConstants.SIGNED_URL_PATH + ".html")
        manager.registerController(path, this)
    }

    override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
        if (!isPost(request)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST)
            return null
        }

        val runningBuild = getRunningBuild(request)
        if (runningBuild == null) {
            LOG.debug("Failed to provide signed urls for request $request. Can't resolve running build.")
            response.sendError(HttpServletResponse.SC_BAD_REQUEST)
            return null
        }

        val parameters = storageSettingsProvider.getStorageSettings(runningBuild)

        val blobPaths = GoogleSignedUrlHelper.readBlobPaths(request.reader.readText())
        if (blobPaths.isEmpty()) {
            LOG.debug("Failed to provide signed urls for request $request. Blob paths collection is empty.")
            response.sendError(HttpServletResponse.SC_BAD_REQUEST)
            return null
        }

        try {
            val data = blobPaths.entries.associate {
                val params = hashMapOf("contentType" to it.value)
                it.key to URL(signedUrlProvider.getSignedUrl(HttpMethod.POST, it.key, parameters + params).first)
            }
            response.writer.append(GoogleSignedUrlHelper.writeSignedUrlMapping(data))
        } catch (e: IOException) {
            LOG.infoAndDebugDetails(
                "Failed to resolve signed upload urls for artifacts of build " + runningBuild.buildId,
                e
            )
            response.status = HttpServletResponse.SC_BAD_GATEWAY
            response.writer.append(e.message)
        } catch (e: Exception) {
            LOG.warnAndDebugDetails(
                "Unexpected error while resolving signed upload urls for artifacts of build " + runningBuild.buildId,
                e
            )
            response.status = HttpServletResponse.SC_BAD_GATEWAY
            response.writer.append(e.message)
        }

        return null
    }

    private fun getRunningBuild(request: HttpServletRequest): RunningBuildEx? {
        val header = AuthorizationHeader.getFrom(request)
        if (header != null) {
            val cre = header.basicAuthCredentials
            if (cre != null) {
                val buildId = BuildAuthUtil.getBuildId(cre.username)
                return if (buildId == -1L) null else runningBuildsCollection.findRunningBuildById(buildId)
            }
        }

        return null
    }

    companion object {
        private val LOG = Logger.getInstance(GoogleSignedUrlController::class.java.name)
    }
}
