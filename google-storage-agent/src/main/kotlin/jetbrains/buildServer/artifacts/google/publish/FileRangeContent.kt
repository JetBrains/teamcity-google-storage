package jetbrains.buildServer.artifacts.google.publish

import com.google.api.client.http.AbstractInputStreamContent
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class FileRangeContent(type: String, private val file: File) : AbstractInputStreamContent(type) {
    var range: Long = 0

    override fun getLength(): Long {
        return file.length() - range
    }

    override fun retrySupported(): Boolean {
        return true
    }

    override fun getInputStream(): InputStream {
        return FileInputStream(this.file).apply {
            if (range > 0) {
                this.skip(range)
            }
        }
    }
}