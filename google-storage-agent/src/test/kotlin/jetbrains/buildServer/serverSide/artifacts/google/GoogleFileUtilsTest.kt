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
                arrayOf<Any?>("file.css", "text/css"),
                arrayOf<Any?>("file.zip", "application/zip"),
                arrayOf<Any?>("file.txt", "text/plain"),
                arrayOf<Any?>("file.jpg", "image/jpeg"),
                arrayOf<Any?>("file.bin", "application/octet-stream")
        )
    }

    @Test(dataProvider = "getContentTypeData")
    fun getContentTypeTest(fileName: String, expectedType: String) {
        Assert.assertEquals(GoogleFileUtils.getContentType(File("GoogleFileUtilsTest/$fileName")), expectedType)
    }
}