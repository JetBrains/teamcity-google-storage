

package jetbrains.buildServer.serverSide.artifacts.google

import jetbrains.buildServer.artifacts.google.publish.GoogleFileUtils
import org.testng.Assert
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.io.File

class GoogleFileUtilsTest {
    @DataProvider
    fun getContentTypeData(): Array<Array<Any?>> {
        return arrayOf(
            arrayOf("file.appx", "application/octet-stream"),
            arrayOf("file.zip", "application/zip"),
            arrayOf("file.txt", "text/plain"),
            arrayOf("file.jpg", "image/jpeg"),
            arrayOf("file.css", "text/css"),
            arrayOf("file.bin", "application/octet-stream")
        )
    }

    @Test(dataProvider = "getContentTypeData")
    fun getContentTypeTest(fileName: String, expectedType: String) {
        Assert.assertEquals(
            GoogleFileUtils.getContentType(getResourceAsFile("/GoogleFileUtilsTest/$fileName")),
            expectedType
        )
    }

    private fun getResourceAsFile(path: String): File =
        this.javaClass.getResource(path)?.path?.let { File(it) }!!
}