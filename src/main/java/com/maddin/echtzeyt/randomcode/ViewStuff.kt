package com.maddin.echtzeyt.randomcode

import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import java.util.concurrent.atomic.AtomicInteger

fun View.printTree(indentation: String = " - ") {
    Log.d(ECHTZEYT_CONFIGURATION.LOG_TAG, "$indentation$this")
    if (this !is ViewGroup) { return }
    for (child in children) {
        child.printTree("  $indentation")
    }
}

private val sNextGeneratedId = AtomicInteger(1)

// thanks to https://stackoverflow.com/questions/6790623/programmatic-views-how-to-set-unique-ids for this code
fun generateViewId(): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) { return View.generateViewId() }

    while (true) {
        val result = sNextGeneratedId.get()
        // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
        var newValue = result + 1
        if (newValue > 0x00FFFFFF) newValue = 1 // Roll over to 1, not 0.
        if (sNextGeneratedId.compareAndSet(result, newValue)) {
            return result
        }
    }
}

fun applyRandomViewId(view: View, force: Boolean=false) {
    if (view.id > 0 && !force) { return }
    view.id = generateViewId()
}

fun View.applyRandomId(force: Boolean=false) {
    applyRandomViewId(this, force)
}

interface DisablingParentScrollChild {
    val changeParentScrollListeners: MutableList<(Boolean) -> Unit>

    fun enableParentScroll() {
        changeParentScrollListeners.forEach { it(true) }
    }

    fun disableParentScroll() {
        changeParentScrollListeners.forEach { it(false) }
    }
}