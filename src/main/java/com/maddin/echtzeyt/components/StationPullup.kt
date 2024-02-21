package com.maddin.echtzeyt.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.LazyView
import com.maddin.transportapi.components.POI
import com.maddin.transportapi.components.Station
import com.maddin.transportapi.endpoints.POIRequestImpl
import kotlin.concurrent.thread

@Suppress("MemberVisibilityCanBePrivate")
class StationPullup @JvmOverloads constructor(context: Context, attrs: AttributeSet?=null, defStyleAttr: Int=0) : PullupScrollView(context, attrs, defStyleAttr) {
    init {
        inflate(context, R.layout.comp_pullup_station, this)
    }

    private var mPOISelected: POI? = null
    private val mTxtStationName: TextView by LazyView(R.id.txtStationName)
    private val mBtnLike: ImageButton by LazyView(R.id.btnPullupStationLike)
    private val mBtnSelect: ImageButton by LazyView(R.id.btnPullupStationConfirm)
    private val mOnSelectedListeners = mutableListOf<(View, POI) -> Unit>()
    private val mLayoutServing: FlowLayout by LazyView(R.id.layoutServingLines)
    private val mTxtNoServing: TextView by LazyView(R.id.txtServingNone)
    private val mViewPadding: View by LazyView(R.id.viewBottomPadding)

    fun addOnCloseListener(listener: (View) -> Unit) {
        onClosePullupListeners.add(listener)
    }

    fun addOnConfirmListener(listener: (View, POI) -> Unit) {
        mOnSelectedListeners.add(listener)
        mBtnSelect.visibility = View.VISIBLE
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        //mBtnClose.setOnClickListener { closePullup() }
        //if (mColorCloseRingBackground != 0) ImageViewCompat.setImageTintList(mViewCloseRing, ColorStateList(arrayOf(intArrayOf()), intArrayOf(mColorCloseRingBackground)))
        mBtnSelect.setOnClickListener { confirmStation() }
        mBtnLike.setOnClickListener { toggleLike() }
    }

    private fun confirmStation() {
        val station = mPOISelected ?: return
        for (listener in mOnSelectedListeners) {
            listener(this, station)
        }
    }

    fun setPOI(poi: POI, showPullup: Boolean = false) {
        saveState()
        clearStation()
        mPOISelected = poi
        mBtnLike.isEnabled = true
        mBtnSelect.isEnabled = true
        mTxtStationName.text = poi.name

        val shouldShowPullup = showPullup && !isVisible()

        mLayoutServing.removeAllViews()
        mLayoutServing.visibility = GONE
        mTxtNoServing.visibility = GONE
        mViewPadding.visibility = GONE

        ECHTZEYT_CONFIGURATION.pullupStationAPI?.let { api ->
            thread(start=true, isDaemon=true) {
                val poiId = poi.id ?: return@thread
                val poiDetails = api.getPOI(POIRequestImpl(poiId))

                val servingLines = (poiDetails.poi as? Station)?.lines?.map { VehicleTypeTextView(context).apply { setLine(it) } }
                if (mPOISelected != poi) { return@thread }

                post {
                    servingLines?.let {
                        for (view in it) { mLayoutServing.addView(view) }
                        if (it.isEmpty()) { mTxtNoServing.visibility = VISIBLE }
                        mLayoutServing.visibility = VISIBLE
                        mViewPadding.visibility = VISIBLE
                    }

                    updateScrollLimits(!shouldShowPullup)
                }
            }
        }

        updateLikeResource()

        if (shouldShowPullup) { showPullup() }
    }

    private fun toggleLike() {
        mPOISelected?.let { ECHTZEYT_CONFIGURATION.toggleFavoritePOI(it) }
        updateLikeResource()
    }

    private fun updateLikeResource() {
        mBtnLike.setImageResource(if (isCurrentStationInFavorites()) R.drawable.ic_star_filled else R.drawable.ic_star)
    }

    private fun isCurrentStationInFavorites() : Boolean {
        return mPOISelected?.let { ECHTZEYT_CONFIGURATION.isFavoritePOI(it) } ?: false
    }

    fun clearStation() {
        mTxtStationName.setText(R.string.widgetNoStation)
        mBtnLike.isEnabled = false
        mBtnSelect.isEnabled = false
    }
}