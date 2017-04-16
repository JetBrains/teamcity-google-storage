/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * See LICENSE in the project root for license information.
 */

package jetbrains.buildServer.serverSide.artifacts.google

import com.google.api.client.googleapis.util.Utils
import com.google.api.client.json.GenericJson
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Bucket
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions

object GoogleUtils {

    fun getPathPrefix(properties: Map<String, String>) = properties[GoogleConstants.PATH_PREFIX_ATTR]

    fun getArtifactPath(properties: Map<String, String>, path: String): String {
        return getPathPrefix(properties) + '/' + path
    }

    fun getStorage(parameters: Map<String, String>): Storage {
        val builder = StorageOptions.newBuilder()
        parameters[GoogleConstants.PARAM_ACCESS_KEY]?.trim()?.byteInputStream()?.use {
            val factory = Utils.getDefaultJsonFactory()
            val parser = factory.createJsonParser(it)
            val json = parser.parse(GenericJson::class.java)
            builder.setProjectId(json[PROJECT_ID] as String)
            it.reset()
            builder.setCredentials(GoogleCredentials.fromStream(it))
        }

        return builder.build().service
    }

    fun getStorageBucket(parameters: Map<String, String>): Bucket {
        val storage = getStorage(parameters)
        val bucketName = parameters[GoogleConstants.PARAM_BUCKET_NAME]?.trim()
        return storage.get(bucketName, Storage.BucketGetOption.fields(*emptyArray()))
    }

    private val PROJECT_ID = "project_id"
}