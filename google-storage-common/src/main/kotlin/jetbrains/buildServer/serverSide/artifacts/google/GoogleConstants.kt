/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * See LICENSE in the project root for license information.
 */

package jetbrains.buildServer.serverSide.artifacts.google

import jetbrains.buildServer.agent.Constants

object GoogleConstants {
    const val STORAGE_TYPE = "google-storage"
    const val SETTINGS_PATH = "google-storage-settings"
    const val SIGNED_URL_PATH = "google-signed-urls"
    const val CREDENTIALS_TYPE = "credentials.type"
    const val CREDENTIALS_ENVIRONMENT = "environment"
    const val CREDENTIALS_KEY = "key"
    const val USE_SIGNED_URL_FOR_UPLOAD = "credentials.useSignedUrl"
    const val PARAM_ACCESS_KEY = Constants.SECURE_PROPERTY_PREFIX + "access-key"
    const val PARAM_BUCKET_NAME = "bucket-name"
    const val PATH_PREFIX_ATTR = "google_path_prefix"
    const val PATH_PREFIX_SYSTEM_PROPERTY = "storage.google.path.prefix"
    const val URL_LIFETIME_SEC = "storage.google.url.expiration.time.seconds"
    const val SIGNED_URL_GET_CACHE_ENABLED = "storage.google.signedUrl.get.cache.enabled"

    const val DEFAULT_URL_LIFETIME_SEC = 60
}