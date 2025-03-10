package com.maddin.echtzeyt.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.AdapterView
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.maddin.echtzeyt.R
import kotlin.math.roundToInt

// Inspired by https://stackoverflow.com/questions/70612110/why-wont-the-autocompletetextview-showdropdown-not-trigger-on-call
open class InstantAutoCompleteTextView : AppCompatAutoCompleteTextView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    val onFocusChangeListeners = mutableListOf<(Boolean) -> Unit>()

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)

        onFocusChangeListeners.forEach { it(focused) }
    }

    override fun enoughToFilter(): Boolean {
        return true
    }

    // basically showDropDown but somehow works
    fun showSuggestions(): Boolean {
        return if (windowVisibility == VISIBLE) {
            performFiltering("", 0)
            showDropDown()
            true
        } else {
            false
        }
    }

    override fun performFiltering(text: CharSequence?, keyCode: Int) {}

    fun addOnTextChangedListener(listener: (text: CharSequence?) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, i1: Int, i2: Int, i3: Int) {}
            override fun onTextChanged(text: CharSequence?, i1: Int, i2: Int, i3: Int) { listener(text) }
            override fun afterTextChanged(text: Editable?) {}
        })
    }

    fun addOnItemSelectedListener(listener: () -> Unit) {
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { listener() }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ -> listener() }
    }
}