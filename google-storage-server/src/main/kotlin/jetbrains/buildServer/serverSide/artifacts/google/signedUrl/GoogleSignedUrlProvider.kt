package jetbrains.buildServer.serverSide.artifacts.google.signedUrl

import com.google.cloud.storage.HttpMethod

interface GoogleSignedUrlProvider {
    val urlLifetimeSec: Int

    fun getSignedUrl(httpMethod: HttpMethod,
                     path: String,
                     parameters: Map<String, String>): Pair<String, Int>
}