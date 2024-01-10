package com.maddin.echtzeyt.randomcode

import android.os.Build
import android.view.View
import java.util.concurrent.atomic.AtomicInteger

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

fun applyRandomViewId(view: View) {
    if (view.id > 0) { return }
    view.id = generateViewId()
}

fun View.applyRandomId() {
    applyRandomViewId(this)
}