package com.maddin.echtzeyt.fragments

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.ConditionVariable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.activities.EchtzeytActivity
import com.maddin.echtzeyt.components.PullupManager
import com.maddin.echtzeyt.randomcode.ActivityViewpagerScrollable
import com.maddin.echtzeyt.randomcode.DisablingParentScrollChild

abstract class EchtzeytForegroundFragment: Fragment {
    constructor(@LayoutRes resId: Int) : super(resId)
    constructor() : super()

    protected val isInForeground = ConditionVariable()
    protected var isDestroyed = false
    protected val preferences: SharedPreferences by lazy { ECHTZEYT_CONFIGURATION.preferences(safeContext) }
    private var onBackPressedCallback: OnBackPressedCallback? = null

    private var mView: View? = null
    val safeView: View  // should always return the last valid view (or at least a valid parent view); maintains a copy of the old view even after the fragment is destroyed
        get() { return view ?: mView!!  }

    private var mContext: Context? = null
    protected val safeContext: Context  // should always return the last valid context (or at least any valid context); maintains a copy of the old context even after the fragment is destroyed
        get() { return context ?: mContext ?: safeView.context }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // TODO: fix this weird bug -> seems to be present in the fragment_realtime.xml and settings_fragment_realtime.xml files but no other
        return try { super.onCreateView(inflater, container, savedInstanceState) } catch(e: Throwable) { null }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mView = view

        onBackPressedCallback = activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
            onBackPressed()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    private fun setInForeground(inForeground: Boolean) {
        if (inForeground) { isInForeground.open() } else { isInForeground.close() }
        onBackPressedCallback?.isEnabled = inForeground && usesOnBackPressed
    }

    override fun onResume() {
        super.onResume()
        setInForeground(true)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        setInForeground(!hidden)
    }

    override fun onPause() {
        super.onPause()
        if (isVisible) { return }
        setInForeground(false)
    }

    override fun onStop() {
        super.onStop()
        setInForeground(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isDestroyed = true
    }

    protected open fun onBackPressed() {}
    protected open var usesOnBackPressed = false
        set(value) {
            field=value
            onBackPressedCallback?.isEnabled = isInForeground.block(1) && value
        }

    fun registerParentScrollDisablingView(view: DisablingParentScrollChild) {
        (activity as? ActivityViewpagerScrollable)?.let {
            view.changeParentScrollListeners.add { enable -> if (enable) it.enableScroll() else it.disableScroll() }
        }
    }
}

abstract class EchtzeytPullupFragment : EchtzeytForegroundFragment {
    constructor(@LayoutRes resId: Int) : super(resId)
    constructor() : super()
    val pullupManager by lazy { PullupManager(this) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListeners()
    }

    private fun initListeners() {
        pullupManager.onFirstOpenedListeners.add { usesOnBackPressed = true }
        pullupManager.onLastClosedListeners.add { usesOnBackPressed = false }
    }

    override fun onBackPressed() {
        pullupManager.onBackPressed()
    }
}

// thanks to https://stackoverflow.com/questions/67334537/how-to-make-viewpager2-less-sensitive for parts of the following code
fun ViewPager2.reduceDragSensitivity(f: Int = 3) {
    try {
        val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView

        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        touchSlopField.set(recyclerView, touchSlop * f)
    } catch (e: Throwable) {
        Log.w(ECHTZEYT_CONFIGURATION.LOG_TAG, "Unable to reduce drag sensitivity", e)
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