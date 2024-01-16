package com.maddin.echtzeyt.fragments

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.ConditionVariable
import android.util.Log
import android.view.View
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.ECHTZEYT_LOG_TAG

abstract class EchtzeytForegroundFragment: Fragment {
    constructor(@LayoutRes resId: Int) : super(resId)
    constructor() : super()

    protected val isInForeground = ConditionVariable()
    protected var isDestroyed = false
    protected val preferences: SharedPreferences by lazy { ECHTZEYT_CONFIGURATION.preferences(safeContext) }

    private var mView: View? = null
    protected val safeView: View  // should always return the last valid view (or at least a valid parent view); maintains a copy of the old view even after the fragment is destroyed
        get() { return view ?: mView!!  }

    private var mContext: Context? = null
    protected val safeContext: Context  // should always return the last valid context (or at least any valid context); maintains a copy of the old context even after the fragment is destroyed
        get() { return context ?: mContext ?: safeView.context }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mView = view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    override fun onResume() {
        super.onResume()
        isInForeground.open()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (hidden) {
            isInForeground.close()
        } else {
            isInForeground.open()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isVisible) { return }
        isInForeground.close()
    }

    override fun onStop() {
        super.onStop()
        isInForeground.close()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isDestroyed = true
    }
}

// thanks to https://stackoverflow.com/questions/67334537/how-to-make-viewpager2-less-sensitive for parts of the following code
fun ViewPager2.reduceDragSensitivity(f: Int = 2) {
    try {
        val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView

        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        touchSlopField.set(recyclerView, touchSlop * f)
    } catch (e: Throwable) {
        Log.w(ECHTZEYT_LOG_TAG, "Unable to reduce drag sensitivity", e)
    }
}

@Suppress("MemberVisibilityCanBePrivate")
open class NamedFragment(@StringRes val nameRes: Int, val fragmentClass: Class<out Fragment>) {
    open fun createInstance(): Fragment {
        return fragmentClass.getConstructor().newInstance()
    }
}

class MenuViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle, private val fragments: List<NamedFragment>) : FragmentStateAdapter(fragmentManager, lifecycle) {
    override fun getItemCount(): Int {
        return fragments.size
    }

    override fun createFragment(position: Int): Fragment {
        return fragments[position].createInstance()
    }

    fun getFragmentNameResource(position: Int): Int {
        return fragments[position].nameRes
    }
}