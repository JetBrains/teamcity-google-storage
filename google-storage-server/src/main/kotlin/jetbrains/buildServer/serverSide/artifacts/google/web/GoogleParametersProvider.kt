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

import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants

class GoogleParametersProvider {

    val credentialsType: String
        get() = GoogleConstants.CREDENTIALS_TYPE

    val credentialsEnvironment: String
        get() = GoogleConstants.CREDENTIALS_ENVIRONMENT

    val credentialsKey: String
        get() = GoogleConstants.CREDENTIALS_KEY

    val accessKey: String
        get() = GoogleConstants.PARAM_ACCESS_KEY

    val bucketName: String
        get() = GoogleConstants.PARAM_BUCKET_NAME

    val containersPath: String
        get() = "/plugins/${GoogleConstants.STORAGE_TYPE}/${GoogleConstants.SETTINGS_PATH}.html"

    val useSignedUrlForUpload: String
        get() = GoogleConstants.USE_SIGNED_URL_FOR_UPLOAD
}