/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
