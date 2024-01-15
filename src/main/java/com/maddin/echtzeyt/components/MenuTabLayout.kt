package com.maddin.echtzeyt.components

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.get
import androidx.core.view.updatePadding
import com.google.android.material.tabs.TabLayout
import com.maddin.echtzeyt.R

val TabLayout.Tab.textView: TextView
    get() { return view[1] as TextView }

class MenuTabLayout(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : TabLayout(context, attrs, defStyleAttr), TabLayout.OnTabSelectedListener {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    private var mAlphaTextNormal = 0.6f
    private var mAlphaTextSelected = 1f

    private var mLastAnimatedTabShow: Tab? = null
    private var mLastAnimatedTabHide: Tab? = null

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
        onAddTab(tab, false)
    }

    override fun addTab(tab: Tab, position: Int, setSelected: Boolean) {
        super.addTab(tab, position, setSelected)
        onAddTab(tab, setSelected)
    }

    override fun addTab(tab: Tab, setSelected: Boolean) {
        super.addTab(tab, setSelected)
        onAddTab(tab, setSelected)
    }

    override fun addTab(tab: Tab, position: Int) {
        super.addTab(tab, position)
        onAddTab(tab, false)
    }

    private fun onAddTab(tab: Tab, selected: Boolean) {
        tab.text = " ${tab.text} "
        tab.textView.maxLines = 1
        tab.textView.setSingleLine()
        tab.textView.ellipsize = null
        tab.textView.alpha = if (selected) mAlphaTextSelected else mAlphaTextNormal
        reinforceTabTypeface(tab, selected)

        tab.view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> reinforceTabTypeface(tab) }
    }

    private fun reinforceTabTypeface(tab: Tab) {
        if (tab.parent == null) { return }
        reinforceTabTypeface(tab, tab.isSelected)
    }

    private fun reinforceTabTypeface(tab: Tab, selected: Boolean) {
        val typeface = if (selected) tab.textView.typeface else null
        val style = if (selected) Typeface.BOLD else Typeface.NORMAL
        tab.textView.setTypeface(typeface, style)
    }

    override fun onTabSelected(tab: Tab?) {
        if (tab == null) { return }
        if (mLastAnimatedTabShow != tab) {
            ViewCompat.animate(tab.textView).alpha(mAlphaTextSelected).setDuration(120).withEndAction { mLastAnimatedTabShow = null }.start()
            mLastAnimatedTabShow = tab
        }
        reinforceTabTypeface(tab, true)
    }

    override fun onTabUnselected(tab: Tab?) {
        if (tab == null) { return }
        if (mLastAnimatedTabHide != tab) {
            ViewCompat.animate(tab.textView).alpha(mAlphaTextNormal).setDuration(80).withEndAction { mLastAnimatedTabHide = null }.start()
            mLastAnimatedTabHide = tab
        }
        reinforceTabTypeface(tab, false)
    }

    override fun onTabReselected(tab: Tab?) {}
}