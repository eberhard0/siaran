package com.iameberhard.siaran

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes any crash to Download/Siaran/crash-*.txt (and keeps the last one in app
 * storage) so it can be read without a computer and adb.
 */
class SiaranApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { saveCrash(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun saveCrash(thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val report = buildString {
            appendLine("Siaran ${packageManager.getPackageInfo(packageName, 0).versionName} crash $stamp")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine(error.stackTraceToString())
        }
        File(filesDir, LAST_CRASH).writeText(report)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "siaran-crash-$stamp.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Siaran")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            contentResolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) }
        }
    }

    companion object {
        const val LAST_CRASH = "last-crash.txt"
    }
}
