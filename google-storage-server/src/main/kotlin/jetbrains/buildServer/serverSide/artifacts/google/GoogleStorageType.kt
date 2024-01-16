

package jetbrains.buildServer.serverSide.artifacts.google

import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.artifacts.ArtifactStorageType
import jetbrains.buildServer.serverSide.artifacts.ArtifactStorageTypeRegistry
import jetbrains.buildServer.web.openapi.PluginDescriptor

class GoogleStorageType(registry: ArtifactStorageTypeRegistry,
                        private val descriptor: PluginDescriptor) : ArtifactStorageType() {
    init {
        registry.registerStorageType(this)
    }

    override fun getName() = "Google Storage"

    override fun getDescription() = "Provides Google Cloud Storage support for TeamCity artifacts"

    override fun getType() = GoogleConstants.STORAGE_TYPE

    override fun getEditStorageParametersPath() = descriptor.getPluginResourcesPath(GoogleConstants.SETTINGS_PATH + ".jsp")

    override fun getParametersProcessor(): PropertiesProcessor? {
        return PropertiesProcessor {
            val invalidProperties = arrayListOf<InvalidProperty>()

            GoogleConstants.PARAM_ACCESS_KEY.apply {
                if (it[GoogleConstants.CREDENTIALS_TYPE] != GoogleConstants.CREDENTIALS_ENVIRONMENT &&
                        it[this].isNullOrEmpty()) {
                    invalidProperties.add(InvalidProperty(this, EMPTY_VALUE))
                } else {
                    try {
                        GoogleUtils.getStorage(it)
                    } catch (e: Throwable) {
                        val message = GoogleUtils.getExceptionMessage(e)
                        invalidProperties.add(InvalidProperty(this, message))
                    }
                }
            }

            GoogleConstants.PARAM_BUCKET_NAME.apply {
                if (it[this].isNullOrEmpty()) {
                    invalidProperties.add(InvalidProperty(this, EMPTY_VALUE))
                }
            }

            invalidProperties
        }
    }

    override fun getDefaultParameters(): MutableMap<String, String> {
        return mutableMapOf(
            GoogleConstants.CREDENTIALS_TYPE to GoogleConstants.CREDENTIALS_ENVIRONMENT,
            GoogleConstants.USE_SIGNED_URL_FOR_UPLOAD to "true"
        )
    }

    override fun getSettingsPreprocessor() = SettingsPreprocessor { input ->
        return@SettingsPreprocessor HashMap<String, String>(input).apply {
            if (GoogleUtils.useSignedUrls(input)) {
                this.remove(GoogleConstants.PARAM_ACCESS_KEY)
            }
        }
    }

    companion object {
        private val EMPTY_VALUE = "Should not be empty"
    }
}