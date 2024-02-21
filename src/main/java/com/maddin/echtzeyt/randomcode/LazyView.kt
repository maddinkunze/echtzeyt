package com.maddin.echtzeyt.randomcode

import android.app.Activity
import android.content.Context
import android.view.View
import com.maddin.echtzeyt.fragments.EchtzeytForegroundFragment
import kotlin.reflect.KProperty

open class LazyView<T : View>(private val id: Int) {
    private object UninitializedValue
    @Volatile private var propValue: Any? = UninitializedValue
    private var initFun: ((T) -> Unit)? = null

    operator fun getValue(thisRef: View, property: KProperty<*>): T {
        return getValue { id -> thisRef.findViewById(id) }
    }

    operator fun getValue(thisRef: Activity, property: KProperty<*>): T {
        return getValue { id -> thisRef.findViewById(id) }
    }

    operator fun getValue(thisRef: EchtzeytForegroundFragment, property: KProperty<*>): T {
        return getValue { id -> thisRef.safeView.findViewById(id) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getValue(findViewById: (Int) -> T): T {
        val localValue = propValue

        if(localValue != UninitializedValue) {
            return localValue as T
        }

        return synchronized(this) {
            val localValue2 = propValue

            if (localValue2 != UninitializedValue) {
                localValue2 as T
            } else {
                val initializedValue = findViewById(id)
                init(initializedValue)
                propValue = initializedValue
                initializedValue
            }
        }
    }

    protected open fun init(item: T) {
        initFun?.invoke(item)
    }

    fun init(lambda: (T) -> Unit): LazyView<T> {
        initFun = lambda
        return this
    }
}