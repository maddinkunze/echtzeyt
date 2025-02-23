package com.maddin.echtzeyt.randomcode

import android.content.Context
import java.io.File

fun Context.unpackResourceIntoCache(resId: Int, filename: String): File {
    val file = File(cacheDir, filename)
    if (!file.exists()) {
        val inputStream = resources.openRawResource(resId)
        val outputStream = file.outputStream()
        val buffer = ByteArray(8192)
        var read: Int
        while (true) {
            read = inputStream.read(buffer)
            if (read < 0) { break }
            outputStream.write(buffer, 0, read)
        }
        inputStream.close()
        outputStream.flush()
        outputStream.close()
    }
    return file
}