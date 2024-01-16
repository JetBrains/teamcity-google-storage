

package jetbrains.buildServer.serverSide.artifacts.google.signedUrl

import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.serverSide.artifacts.google.GoogleConstants
import jetbrains.buildServer.serverSide.artifacts.google.GoogleUtils
import java.util.*
import java.util.concurrent.TimeUnit

class GoogleSignedUrlProviderImpl : GoogleSignedUrlProvider {
    private val myLinksCache = CacheBuilder.newBuilder()
        .expireAfterWrite(urlLifetimeSec.toLong(), TimeUnit.SECONDS)
        .maximumSize(200)
        .build<String, String>()

    override val urlLifetimeSec: Int
        get() = TeamCityProperties.getInteger(
            GoogleConstants.URL_LIFETIME_SEC,
            GoogleConstants.DEFAULT_URL_LIFETIME_SEC
        )

    override fun getSignedUrl(
        httpMethod: HttpMethod,
        path: String,
        parameters: Map<String, String>
    ): Pair<String, Int> {
        val lifeTime = urlLifetimeSec
        val resolver = {
            val bucket = GoogleUtils.getStorageBucket(parameters)
            val blobInfo = BlobInfo.newBuilder(bucket, path)
            val urlOptions = arrayListOf<Storage.SignUrlOption>(
                Storage.SignUrlOption.httpMethod(httpMethod),
                Storage.SignUrlOption.withV4Signature()
            )

            if (httpMethod.name() == "POST") {
                urlOptions.add(
                    Storage.SignUrlOption.withExtHeaders(
                        mapOf(
                            "x-goog-resumable" to "start",
                            "Content-Type" to parameters["contentType"]
                        )
                    )
                )
                urlOptions.add(Storage.SignUrlOption.withContentType())
                blobInfo.setContentType(parameters["contentType"])
            }

            bucket.storage.signUrl(blobInfo.build(), lifeTime.toLong(), TimeUnit.SECONDS, *urlOptions.toTypedArray())
                .toString()
                .also {
                    LOG.debug("signedURL: $it")
                    LOG.debug("contentType: ${parameters["contentType"]}")
                }
        }

        if (httpMethod == HttpMethod.GET && TeamCityProperties.getBoolean(GoogleConstants.SIGNED_URL_GET_CACHE_ENABLED)) {
            return myLinksCache.get(getIdentity(parameters, path), resolver) to lifeTime
        }

        return resolver() to lifeTime
    }

    private fun getIdentity(params: Map<String, String>, path: String): String {
        return StringBuilder().apply {
            append(params[GoogleConstants.CREDENTIALS_TYPE])
            append(params[GoogleConstants.PARAM_ACCESS_KEY])
            append(params[GoogleConstants.PARAM_BUCKET_NAME])
            append(path)
        }.toString().lowercase(Locale.getDefault()).hashCode().toString()
    }

    companion object {
        private val LOG = Logger.getInstance(GoogleSignedUrlProviderImpl::class.java.name)
    }
}