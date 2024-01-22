package com.maddin.echtzeyt.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import com.maddin.echtzeyt.R

class StationSearchbar : ConstraintLayout {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        getAttributes(context, attrs, defStyleAttr)
    }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        getAttributes(context, attrs)
    }
    constructor(context: Context) : super(context) {
        getAttributes(context)
    }

    private var mInfo: String? = null
    private var mFillUntil: Int = 0
    private var mIsAnimated: Boolean = true
    private val mTxtInfo: TextView by lazy { findViewById(R.id.searchbar_txtSearchInfo) }
    private val mEdtSearch: StationSearchTextView by lazy { findViewById(R.id.searchbar_edtSearch) }
    private val mBtnImagePaddingNormal by lazy { context.resources.getDimension(R.dimen.button_padding_small) }
    private val mBtnImagePaddingFocused by lazy { context.resources.getDimension(R.dimen.button_padding) }

    var searchStationAPI
        get() = mEdtSearch.searchStationAPI
        set(value) { mEdtSearch.searchStationAPI = value }
    var currentStation
        get() = mEdtSearch.currentStation
        set(value) { mEdtSearch.currentStation = value }
    val onItemSelectedListeners get() = mEdtSearch.onItemSelectedListeners

    init {
        inflate(context, R.layout.comp_searchbar_station, this)
    }

    private fun getAttributes(context: Context, attrs: AttributeSet?=null, defStyleAttr: Int=0, defStyleRes: Int=0) {
        val styledAttr = context.obtainStyledAttributes(attrs, R.styleable.StationSearchbar, defStyleAttr, defStyleRes)
        try {
            mInfo = styledAttr.getString(R.styleable.StationSearchbar_textInfo)
            mIsAnimated = styledAttr.getBoolean(R.styleable.StationSearchbar_animated, mIsAnimated)
            mFillUntil = styledAttr.getResourceId(R.styleable.StationSearchbar_fillUntil, mFillUntil)
        } finally {
            styledAttr.recycle()
        }
    }

    fun setText(text: String) {
        mEdtSearch.setText(text)
    }

    fun setInfoText(text: String) {
        mTxtInfo.text = text
        mTxtInfo.visibility = View.VISIBLE
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        mInfo?.let { setInfoText(it) }
        mTxtInfo.setOnClickListener { mEdtSearch.requestFocus() }
        mEdtSearch.isAnimated = mIsAnimated


        if (mFillUntil != 0) {
            val constraints = ConstraintSet()
            constraints.clone(this)
            constraints.connect(mEdtSearch.id, ConstraintSet.END, mFillUntil, ConstraintSet.START)
            constraints.applyTo(this)
        }

        mEdtSearch.onFocusChangeListeners.add { focused ->
            mTxtInfo.animate().alpha(if (focused) 1f else 0.5f).setDuration(200).start()

            val pad = if (focused) mBtnImagePaddingFocused else mBtnImagePaddingNormal
            for (view in children) {
                if (view !is AnimatableImageButton) { continue }
                view.animatePadding(pad, 200)
            }
        }
    }

    override fun clearFocus() {
        mEdtSearch.clearFocus()
    }

    fun updateSearch() {
        mEdtSearch.updateSearch()
    }

    fun ntUpdateSearch() {
        mEdtSearch.ntUpdateSearch()
    }
}