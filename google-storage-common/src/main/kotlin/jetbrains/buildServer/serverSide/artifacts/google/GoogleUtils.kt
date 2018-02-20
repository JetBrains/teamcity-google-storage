/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * See LICENSE in the project root for license information.
 */

package jetbrains.buildServer.serverSide.artifacts.google

import com.fasterxml.jackson.core.JsonParseException
import com.google.api.client.googleapis.util.Utils
import com.google.api.client.json.GenericJson
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Bucket
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions

object GoogleUtils {

    fun getPathPrefix(properties: Map<String, String>) = properties[GoogleConstants.PATH_PREFIX_ATTR]

    fun getArtifactPath(properties: Map<String, String>, path: String): String {
        return getPathPrefix(properties)?.trimEnd(FORWARD_SLASH) + FORWARD_SLASH + path
    }

    fun getStorage(parameters: Map<String, String>): Storage {
        if (parameters[GoogleConstants.CREDENTIALS_TYPE] == GoogleConstants.CREDENTIALS_ENVIRONMENT) {
            return StorageOptions.getDefaultInstance().service
        }

        val builder = StorageOptions.newBuilder()
        parameters[GoogleConstants.PARAM_ACCESS_KEY]?.trim()?.byteInputStream()?.use {
            val factory = Utils.getDefaultJsonFactory()
            val parser = factory.createJsonParser(it)
            val json = parser.parse(GenericJson::class.java)
            json[PROJECT_ID]?.let {
                builder.setProjectId(it as String)
            }

            it.reset()
            builder.setCredentials(GoogleCredentials.fromStream(it))
        }

        return builder.build().service
    }

    fun getStorageBucket(parameters: Map<String, String>): Bucket {
        val storage = getStorage(parameters)
        val bucketName = parameters[GoogleConstants.PARAM_BUCKET_NAME]?.trim()
        return storage.get(bucketName, Storage.BucketGetOption.fields())
    }

    fun useSignedUrls(parameters: Map<String, String>): Boolean {
        return parameters[GoogleConstants.USE_SIGNED_URL_FOR_UPLOAD]?.trim()?.toBoolean() ?: false
    }

    fun getExceptionMessage(e: Throwable) = when (e) {
        is JsonParseException -> {
            "Invalid key format"
        }
        is IllegalArgumentException -> {
            if (e.message?.contains("project ID is required") == true) {
                "Invalid key: no project id"
            } else {
                "Invalid key: ${e.message}"
            }
        }
        else -> {
            "Invalid credentials: ${e.message}"
        }
    }

    private val PROJECT_ID = "project_id"
    private val FORWARD_SLASH = '/'
}