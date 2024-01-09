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

class ActivityResultSerializable<T: Serializable>(private val activityToStart: Class<out Activity>) : ActivityResultContract<T?, T?>() {
    companion object {
        const val INPUT_DATA = "data"
        const val OUTPUT_DATA = "data"
    }

    override fun createIntent(context: Context, input: T?): Intent {
        return Intent().setClass(context, activityToStart).putExtra(INPUT_DATA, input)
    }

    @Suppress("UNCHECKED_CAST")
    override fun parseResult(resultCode: Int, intent: Intent?): T? {
        if (intent == null) { return null }
        if (!intent.hasExtra(OUTPUT_DATA)) { return null }
        return intent.getSerializableExtraCompat<Serializable>(OUTPUT_DATA) as? T?
    }
}