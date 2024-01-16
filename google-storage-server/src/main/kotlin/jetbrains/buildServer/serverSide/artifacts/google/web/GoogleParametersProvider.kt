

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