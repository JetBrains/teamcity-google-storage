# TeamCity Google Cloud Storage

[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![plugin status]( 
https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamCityPluginsByJetBrains_TeamcityGoogleStorage_Build)/statusIcon.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_TeamcityGoogleStorage_Build&guest=1)

This plugin allows replacing the TeamCity built-in artifacts storage by [Google Cloud Storage](https://cloud.google.com/storage/). The artifacts storage can be changed at the project level. After changing the storage, new artifacts produced by the builds of this project will be published to the Google Cloud Storage bucket. Besides publishing, the plugin also implements resolving of artifact dependencies and clean-up of build artifacts.

# State

The plugin has complete baseline functionality, but it is not yet ready to be used in production and further plugin updates may break backward compatibility (regarding settings, data storage, etc.).
 
# Features

When installed and configured, the plugin:
* allows uploading artifacts to Google Cloud Storage
* allows downloading artifacts from Google Cloud Storage
* handles resolution of artifact dependencies
* handles clean-up of artifacts 
* displays artifacts located in Google Cloud Storage in the TeamCity UI.
 
# Download

You can [download the plugin](https://plugins.jetbrains.com/plugin/9634-google-artifact-storage) and install it as [an additional TeamCity plugin](https://confluence.jetbrains.com/display/TCDL/Installing+Additional+Plugins).

# Compatibility

The plugin is compatible with [TeamCity](https://www.jetbrains.com/teamcity/download/) **2017.1.1** and greater.

# Configuring 

The plugin adds the Artifacts Storage tab to the Project Settings page in the TeamCity Web UI. 
The tab lists the internal TeamCity artifacts storage displayed by default and marked as active.

To configure Google Cloud Storage for TeamCity artifacts, perform the following:
1. Select Google Storage as the storage type
2. Fill in the account name and key
3. Save your settings.

The configured Google Cloud Storage will appear on the Artifacts storage page. Make it active using the corresponding link.
Now the artifacts of this project, its subprojects, and build configurations will be stored in the configured storage.

# Build

This project uses gradle as the build system. You can easily open it in [IntelliJ IDEA](https://www.jetbrains.com/idea/help/importing-project-from-gradle-model.html) or [Eclipse](http://gradle.org/eclipse/).
To test & build the plugin, execute the `build` gradle command.

# Contributions

We appreciate all kinds of feedback, so please feel free to send a PR or write an issue.
