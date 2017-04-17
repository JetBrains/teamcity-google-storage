/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * See LICENSE in the project root for license information.
 */

package jetbrains.buildServer.serverSide.artifacts.google.web

import jetbrains.buildServer.controllers.ActionErrors
import jetbrains.buildServer.controllers.BaseFormXmlController
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.serverSide.artifacts.google.GoogleUtils
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.jdom.Element
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class GoogleSettingsController(server: SBuildServer,
                               manager: WebControllerManager,
                               descriptor: PluginDescriptor)
    : BaseFormXmlController(server) {

    init {
        manager.registerController(descriptor.getPluginResourcesPath(GoogleConstants.SETTINGS_PATH + ".html"), this)
    }

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) = null

    override fun doPost(request: HttpServletRequest, response: HttpServletResponse, xmlResponse: Element) {
        val errors = ActionErrors()
        val parameters = getProperties(request)

        try {
            val storage = GoogleUtils.getStorage(parameters)
            val buckets = storage.list().iterateAll().asSequence().toList()
            val bucketsElement = Element("buckets")
            buckets.forEach {
                bucketsElement.addContent(Element("bucket").apply {
                    text = it.name
                })
            }
            xmlResponse.addContent(bucketsElement)
        } catch (e: Throwable) {
            val message = GoogleUtils.getExceptionMessage(e)
            errors.addError(GoogleConstants.PARAM_ACCESS_KEY, message)
        }

        if (errors.hasErrors()) {
            errors.serialize(xmlResponse)
        }
    }

    private fun getProperties(request: HttpServletRequest): Map<String, String> {
        val propsBean = BasePropertiesBean(null)
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true)
        return propsBean.properties
    }
}