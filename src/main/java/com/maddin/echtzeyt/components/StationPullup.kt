package com.maddin.echtzeyt.components

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.ImageViewCompat
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.transportapi.Serving
import com.maddin.transportapi.Station
import kotlin.concurrent.thread

@Suppress("MemberVisibilityCanBePrivate")
class StationPullup @JvmOverloads constructor(context: Context, attrs: AttributeSet?=null, defStyleAttr: Int=0) : PullupScrollView(context, attrs, defStyleAttr) {
    init {
        inflate(context, R.layout.comp_pullup_station, this)
    }

    private var mStationSelected: Station? = null
    private val mTxtStationName: TextView by lazy { findViewById(R.id.txtStationName) }
    private val mBtnLike: ImageButton by lazy { findViewById(R.id.btnPullupStationLike) }
    private val mBtnSelect: ImageButton by lazy { findViewById(R.id.btnPullupStationConfirm) }
    private val mViewCloseRing: AppCompatImageView by lazy { findViewById(R.id.viewClosePullupRing) }
    private val mBtnClose: ImageButton by lazy { findViewById(R.id.btnClosePullup) }
    private val mOnCloseListeners = mutableListOf<(View) -> Unit>()
    private val mOnSelectedListeners = mutableListOf<(View, Station) -> Unit>()
    private val mLayoutServing: FlowLayout by lazy { findViewById(R.id.layoutServingLines) }
    private val mTxtNoServing: TextView by lazy { findViewById(R.id.txtServingNone) }
    private val mViewPadding: View by lazy { findViewById(R.id.viewBottomPadding) }
    private var mColorCloseRingBackground = 0

    init {
        val styledAttr = context.theme.obtainStyledAttributes(attrs, R.styleable.PullupScrollView, defStyleAttr, 0)
        try {
            mColorCloseRingBackground = styledAttr.getColor(R.styleable.PullupScrollView_backgroundColorPullup, mColorCloseRingBackground)
        } finally {
            styledAttr.recycle()
        }
    }

    fun addOnCloseListener(listener: (View) -> Unit) {
        mOnCloseListeners.add(listener)
    }

    fun addOnConfirmListener(listener: (View, Station) -> Unit) {
        mOnSelectedListeners.add(listener)
        mBtnSelect.visibility = View.VISIBLE
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        mBtnClose.setOnClickListener { closePullup() }
        if (mColorCloseRingBackground != 0) ImageViewCompat.setImageTintList(mViewCloseRing, ColorStateList(arrayOf(intArrayOf()), intArrayOf(mColorCloseRingBackground)))
        mBtnSelect.setOnClickListener { confirmStation() }
        mBtnLike.setOnClickListener { toggleLike() }
    }

    private fun closePullup() {
        super.hidePullup()
        for (listener in mOnCloseListeners) {
            listener(this)
        }
    }

    private fun confirmStation() {
        val station = mStationSelected ?: return
        for (listener in mOnSelectedListeners) {
            listener(this, station)
        }
    }

    fun setStation(station: Station, showPullup: Boolean = false) {
        saveState()
        clearStation()
        mStationSelected = station
        mBtnLike.isEnabled = true
        mBtnSelect.isEnabled = true
        mTxtStationName.text = station.name

        val shouldShowPullup = showPullup && !isVisible()

        mLayoutServing.removeAllViews()
        mLayoutServing.visibility = GONE
        mTxtNoServing.visibility = GONE
        mViewPadding.visibility = GONE

        ECHTZEYT_CONFIGURATION.pullupStationAPI?.let { api ->
            thread(start=true, isDaemon=true) {
                val stationLines = api.getStation(station.id)

                val stationServing = stationLines as? Serving?
                val servingLines = stationServing?.lines?.map { VehicleTypeTextView(context).apply { setLine(it) } }
                if (mStationSelected != station) { return@thread }

                post {
                    servingLines?.let {
                        for (view in it) { mLayoutServing.addView(view) }
                        if (it.isEmpty()) { mTxtNoServing.visibility = VISIBLE }
                        mLayoutServing.visibility = VISIBLE
                        mLayoutServing.minimumHeight = 500
                        mViewPadding.visibility = VISIBLE
                    }

                    if (!shouldShowPullup) {
                        animateFromSavedState()
                    }
                }
            }
        }

        updateLikeResource()

        if (shouldShowPullup) { showPullup() }
    }

    private fun toggleLike() {
        mStationSelected?.let { ECHTZEYT_CONFIGURATION.toggleFavoriteStation(it) }
        updateLikeResource()
    }

    private fun updateLikeResource() {
        mBtnLike.setImageResource(if (isCurrentStationInFavorites()) R.drawable.ic_star_filled else R.drawable.ic_star)
    }

    private fun isCurrentStationInFavorites() : Boolean {
        return mStationSelected?.let { ECHTZEYT_CONFIGURATION.isFavoriteStation(it) } ?: false
    }

    fun clearStation() {
        mTxtStationName.setText(R.string.widgetNoStation)
        mBtnLike.isEnabled = false
        mBtnSelect.isEnabled = false
    }
}