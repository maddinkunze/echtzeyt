package com.maddin.echtzeyt.components

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.widget.AppCompatAutoCompleteTextView

// Inspired by https://stackoverflow.com/questions/70612110/why-wont-the-autocompletetextview-showdropdown-not-trigger-on-call
class InstantAutoCompleteTextView : AppCompatAutoCompleteTextView {
    constructor(context: Context?) : super(context!!)
    constructor(arg0: Context?, arg1: AttributeSet?) : super(arg0!!, arg1)
    constructor(arg0: Context?, arg1: AttributeSet?, arg2: Int) : super(arg0!!, arg1, arg2)

    override fun enoughToFilter(): Boolean {
        return true
    }

    // basically showDropDown but somehow works
    fun showSuggestions(): Boolean {
        return if (windowVisibility == VISIBLE) {
            performFiltering(text, 0)
            showDropDown()
            true
        } else {
            false
        }
    }

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