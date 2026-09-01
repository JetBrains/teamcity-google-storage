package jetbrains.buildServer.artifacts.google.publish

import org.testng.Assert.assertEquals
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

class GoogleArtifactsPublisherTest {
    @DataProvider
    fun teamCityArtifactsPaths(): Array<Array<Any>> = arrayOf(
        arrayOf(".teamcity", true),
        arrayOf(".teamcity/artifact", true),
        arrayOf(".teamcity\\artifact", true),
        arrayOf(".teamcity.build_cache/artifact", false),
        arrayOf("artifact", false)
    )

    @Test(dataProvider = "teamCityArtifactsPaths")
    fun identifiesTeamCityArtifactsPath(path: String, expected: Boolean) {
        assertEquals(isInternalTeamCityArtifactsPath(path), expected)
    }
}
