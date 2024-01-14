package com.maddin.echtzeyt.fragments.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.components.LabeledDiscreteSeekBar
import com.maddin.echtzeyt.fragments.NamedFragment
import java.lang.Exception
import kotlin.math.absoluteValue

interface SettingsProperty {
    fun load(preferences: SharedPreferences)
    fun save(editor: Editor)
    fun start() {}
    fun stop() {}
}

open class BoolProperty(protected val settingName: String, protected val switch: SwitchCompat, protected val default: Boolean) : SettingsProperty {
    private val mOnChangeListeners = mutableListOf<() -> Unit>()
    private var mAcceptInputs = false
    var value = default
        protected set

    override fun load(preferences: SharedPreferences) {
        mAcceptInputs = false

        value = preferences.getBoolean(settingName, default)
        switch.isChecked = value

        switch.setOnCheckedChangeListener { _, checked ->
            if (!mAcceptInputs) { return@setOnCheckedChangeListener }
            value = checked

            mOnChangeListeners.forEach { it() }
            preferences.edit { save(this) }
        }
    }

    override fun save(editor: Editor) {
        editor.putBoolean(settingName, value)
    }

    override fun start() {
        // set the value again since it may have been wrongly "restored" (weird bug?)
        switch.isChecked = value
        mAcceptInputs = true
    }

    override fun stop() {
        mAcceptInputs = false
    }

    fun setEnabled(enable: Boolean) {
        switch.isEnabled = enable
        switch.alpha = if (enable) 1f else 0.5f
    }

    fun setChecked(value: Boolean) {
        this.value = value
        switch.isChecked = value
    }

    fun addOnChangeListener(listener: () -> Unit) {
        mOnChangeListeners.add(listener)
    }

    fun removeOnChangeListener(listener: () -> Unit) {
        mOnChangeListeners.remove(listener)
    }
}

open class DiscreteSliderProperty(protected val propertyName: String, protected val seekbar: LabeledDiscreteSeekBar, protected val items: IntArray, protected val default: Int) : SettingsProperty {
    private var mAcceptInputs = false
    var value = default
        protected set

    override fun load(preferences: SharedPreferences) {
        mAcceptInputs = false

        run {
            val valueT = preferences.getInt(propertyName, default)
            seekbar.progress = valueToIndex(valueT)
            value = indexToValue(seekbar.progress)
            if (value != valueT) { preferences.edit { save(this) } }
        }

        seekbar.addOnChangeListener { _, _, index, _ ->
            if (!mAcceptInputs) { return@addOnChangeListener }
            val valueT = value
            value = indexToValue(index)
            if (value == valueT) { return@addOnChangeListener }
            preferences.edit { save(this) }
        }
    }

    override fun start() {
        seekbar.progress = valueToIndex(value)
        mAcceptInputs = true
    }

    override fun stop() {
        mAcceptInputs = false
    }

    private fun valueToIndex(value: Int): Int {
        return items.mapIndexed { i, it -> Pair(i, (it-value).absoluteValue) }.minBy { it.second }.first
    }

    private fun indexToValue(index: Int): Int {
        return items[index]
    }

    override fun save(editor: Editor) {
        editor.putInt(propertyName, value)
    }


}

abstract class SettingsFragment : Fragment, NamedFragment {
    constructor() : super()
    constructor(@LayoutRes resId: Int) : super(resId)

    protected abstract val settings: Array<out SettingsProperty>

    private var mViewOld: View? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // avoid unnecessary init calls
        //if (mViewOld == view) { return }
        mViewOld = view

        val context = this.context ?: view.context
        onLoadSettings(view, context, savedInstanceState)

        val preferences = ECHTZEYT_CONFIGURATION.preferences(context)
        for (setting in settings) {
            setting.load(preferences)
        }
        onSettingsLoaded()
    }

    abstract fun onLoadSettings(view: View, context: Context, savedInstanceState: Bundle?)

    protected open fun onSettingsLoaded() {}

    override fun onStart() {
        super.onStart()
        try {
            for (setting in settings) {
                setting.start()
            }
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try {
            for (setting in settings) {
                setting.stop()
            }
        } catch(_: Exception) {}
    }

    fun save(editor: Editor) {
        try {
            for (setting in settings) {
                setting.save(editor)
            }
        } catch (_: Throwable) {}
    }
}