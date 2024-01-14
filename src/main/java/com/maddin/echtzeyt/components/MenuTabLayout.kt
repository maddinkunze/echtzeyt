package com.maddin.echtzeyt.components

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.view.get
import androidx.core.view.updatePadding
import com.google.android.material.tabs.TabLayout
import com.maddin.echtzeyt.R

val TabLayout.Tab.textView: TextView
    get() { return view[1] as TextView }

class MenuTabLayout(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : TabLayout(context, attrs, defStyleAttr), TabLayout.OnTabSelectedListener {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    private var mAlphaTextNormal = 0.7f
    private var mAlphaTextSelected = 1f

    init {
        val styledAttr = context.theme.obtainStyledAttributes(attrs, R.styleable.MenuTabLayout, defStyleAttr, 0)
        try {
            val paddingContent = styledAttr.getDimensionPixelSize(R.styleable.MenuTabLayout_paddingContent, 0)
            getChildAt(0).updatePadding(left=paddingLeft+paddingContent, right=paddingRight+paddingContent)
        } finally {
            styledAttr.recycle()
        }

        addOnTabSelectedListener(this)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        onTabSelected(getTabAt(selectedTabPosition))
    }

    override fun addTab(tab: Tab) {
        super.addTab(tab)
        tab.textView.alpha = mAlphaTextNormal
    }

    private fun getTextViewFromTab(tab: Tab): TextView {
        return tab.view[1] as TextView
    }

    override fun onTabSelected(tab: Tab?) {
        if (tab == null) { return }
        tab.textView.setTypeface(tab.textView.typeface, Typeface.BOLD)
        tab.textView.alpha = mAlphaTextSelected
    }

    override fun onTabUnselected(tab: Tab?) {
        if (tab == null) { return }
        tab.textView.setTypeface(null, Typeface.NORMAL)
        tab.textView.alpha = mAlphaTextNormal
    }

    override fun onTabReselected(tab: Tab?) {}
}