package com.maddin.echtzeyt.randomcode

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import java.io.Serializable

@Suppress("DEPRECATION")
inline fun <reified T : Serializable> Intent.getSerializableExtraCompat(key: String): T? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { return getSerializableExtra(key, T::class.java) }
    return getSerializableExtra(key) as? T?
}

open class ActivityResultSerializable<T: Serializable, R: Serializable>(private val activityToStart: Class<out Activity>) : ActivityResultContract<T?, R?>() {
    companion object {
        const val ACTION = "action"
        const val INPUT_DATA = "data_in"
        const val OUTPUT_DATA = "data_out"
        fun createResult(result: Serializable): Intent {
            return Intent().putExtra(OUTPUT_DATA, result)
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> parseIntent(intent: Intent?, key: String): T? {
            intent ?: return null
            if (!intent.hasExtra(key)) { return null }
            return intent.getSerializableExtraCompat<Serializable>(key) as? T?
        }

        fun <T> parseIntent(intent: Intent?): T? {
            return parseIntent(intent, INPUT_DATA)
        }
    }

    override fun createIntent(context: Context, input: T?): Intent {
        return Intent().setClass(context, activityToStart).putExtra(INPUT_DATA, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): R? {
        return parseIntent(intent, OUTPUT_DATA)
    }
}