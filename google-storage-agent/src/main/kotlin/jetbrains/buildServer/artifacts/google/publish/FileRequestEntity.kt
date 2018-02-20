package jetbrains.buildServer.artifacts.google.publish

import com.intellij.openapi.util.io.StreamUtil
import org.apache.commons.httpclient.methods.FileRequestEntity
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class FileRangeRequestEntity(private val file: File, contentType: String)
    : FileRequestEntity(file, contentType) {

    var range: Long = 0

    override fun getContentLength(): Long {
        return file.length() - range
    }

    override fun writeRequest(out: OutputStream?) {
        FileInputStream(this.file).use {
            if (range > 0) {
                it.skip(range)
            }

            StreamUtil.copyStreamContent(it, out)
        }
    }
}