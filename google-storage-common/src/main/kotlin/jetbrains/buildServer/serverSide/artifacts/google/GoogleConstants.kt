/*
 * Copyright 2000-2020 JetBrains s.r.o.
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