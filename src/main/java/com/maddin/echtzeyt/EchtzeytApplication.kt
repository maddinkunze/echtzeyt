package com.maddin.echtzeyt

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.multidex.MultiDexApplication
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.maddin.echtzeyt.components.DefaultMOTTypeResolver
import com.maddin.echtzeyt.randomcode.IconLineDrawable
import com.maddin.echtzeyt.randomcode.LineDrawable
import com.maddin.transportapi.components.Bike
import com.maddin.transportapi.components.Bus
import com.maddin.transportapi.components.CableCar
import com.maddin.transportapi.components.Ferry
import com.maddin.transportapi.components.Helicopter
import com.maddin.transportapi.components.Hike
import com.maddin.transportapi.components.Plane
import com.maddin.transportapi.components.Scooter
import com.maddin.transportapi.components.Subway
import com.maddin.transportapi.components.Train
import com.maddin.transportapi.components.Tram
import com.maddin.transportapi.components.Walk
import com.maddin.transportapi.components.isNightBus
import com.maddin.transportapi.components.isReplacement
import com.maddin.transportapi.components.isRunning
import com.maddin.transportapi.impl.germany.SBahn

interface ThemedContext {
    val themedContext: Context
    fun getColorCompat(@ColorRes resId: Int) = ContextCompat.getColor(themedContext, resId)
    fun getDrawableCompat(@DrawableRes resId: Int) = try {
        ContextCompat.getDrawable(themedContext, resId)
    } catch (_: Resources.NotFoundException) {
        VectorDrawableCompat.create(themedContext.resources, resId, null)!!
    }
}

abstract class EchtzeytApplication : MultiDexApplication(), ThemedContext {
    override lateinit var themedContext: Context

    abstract fun configure()
    override fun onCreate() {
        super.onCreate()

        try {
            ECHTZEYT_CONFIGURATION.initApplication(this)
            setCorrectNightMode()
            addMOTTypeBadges()
            configure()
            enableCompatSupportForVectorDrawables()
        } catch (_: Throwable) {}
    }

    protected open fun setCorrectNightMode() {
        val preferences = ECHTZEYT_CONFIGURATION.preferences(this)
        val nightMode = when {
            preferences.getBoolean("autoDark", true) -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            preferences.getBoolean("darkMode", false) -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }

        val nightModeT = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()
        val configuration = Configuration(resources.configuration)
        configuration.uiMode = when (nightMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> Configuration.UI_MODE_NIGHT_NO or nightModeT
            AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES or nightModeT
            else -> resources.configuration.uiMode
        }

        themedContext = createConfigurationContext(configuration)

        if (AppCompatDelegate.getDefaultNightMode() == nightMode) { return }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    protected open fun addMOTTypeBadges() {
        addDefaultVehicleTypeBadges()
    }

    private fun addDefaultVehicleTypeBadges() {
        (ECHTZEYT_CONFIGURATION.motTypeResolver as? DefaultMOTTypeResolver)?.let { resolver ->
            resolver.defaultDrawable = LineDrawable(this, R.color.lineBackgroundDefault, R.color.lineForegroundDefault, R.color.lineForegroundHintLight)

            resolver.add({ it is Bus && it.isReplacement }, drawable=IconLineDrawable(this, R.color.lineBackgroundBusTrainReplacement, R.color.lineForegroundBusTrainReplacement, R.color.lineForegroundHintLight, R.drawable.ic_bus))
            resolver.add({ it is Bus && it.isNightBus },    drawable=IconLineDrawable(this, R.color.lineBackgroundBusNight,            R.color.lineForegroundBusNight,            R.color.lineForegroundHintDark,  R.drawable.ic_bus))
            resolver.add({ it is Bus },                     drawable=IconLineDrawable(this, R.color.lineBackgroundBus,                 R.color.lineForegroundBus,                 R.color.lineForegroundHintDark,  R.drawable.ic_bus))
            resolver.add({ it is Tram },                    drawable=IconLineDrawable(this, R.color.lineBackgroundTram,                R.color.lineForegroundTram,                R.color.lineForegroundHintDark,  R.drawable.ic_tram)     .apply { iconSize = 0.8 })
            resolver.add({ it is Subway },                  drawable=    LineDrawable(this, R.color.lineBackgroundSubway,              R.color.lineForegroundSubway,              R.color.lineForegroundHintDark)                          .apply { radius = 0.4 })
            resolver.add({ it is SBahn },                   drawable=IconLineDrawable(this, R.color.lineBackgroundSuburban,            R.color.lineForegroundSuburban,            R.color.lineForegroundHintDark,  R.drawable.ic_suburban) .apply { radius = 0.5 })
            resolver.add({ it is CableCar },                drawable=IconLineDrawable(this, R.color.lineBackgroundCablecar,            R.color.lineForegroundCablecar,            R.color.lineForegroundHintLight, R.drawable.ic_cablecar))
            resolver.add({ it is Train },                   drawable=IconLineDrawable(this, R.color.lineBackgroundTrain,               R.color.lineForegroundTrain,               R.color.lineForegroundHintLight, R.drawable.ic_train))
            resolver.add({ it is Plane },                   drawable=IconLineDrawable(this, R.color.lineBackgroundPlane,               R.color.lineForegroundPlane,               R.color.lineForegroundHintDark,  R.drawable.ic_plane))
            resolver.add({ it is Helicopter },              drawable=IconLineDrawable(this, R.color.lineBackgroundHelicopter,          R.color.lineForegroundHelicopter,          R.color.lineForegroundHintLight, R.drawable.ic_helicopter))
            resolver.add({ it is Ferry },                   drawable=IconLineDrawable(this, R.color.lineBackgroundFerry,               R.color.lineForegroundFerry,               R.color.lineForegroundHintDark,  R.drawable.ic_boat))
            resolver.add({ it is Scooter },                 drawable=IconLineDrawable(this, R.color.lineBackgroundScooter,             R.color.lineForegroundScooter,             R.color.lineForegroundHintAuto,  R.drawable.ic_scooter))
            resolver.add({ it is Bike },                    drawable=IconLineDrawable(this, R.color.lineBackgroundBike,                R.color.lineForegroundBike,                R.color.lineForegroundHintAuto,  R.drawable.ic_bike))
            //resolver.add({ it is Segway}, drawable=IconLineDrawable(this, R.color.lineBackgroundSegway, R.color.lineForegroundSegway, R.color.lineForegroundHintAuto, R.drawable.ic_segway))
            //resolver.add({ it is Escalator }, drawable=IconLineDrawable(this, R.color.lineBackgroundEscalator, R.color.lineForegroundEscalator, R.color.lineForegroundHintAuto, R.drawable.ic_escalator))
            //resolver.add({ it is Stairs }, drawable=IconLineDrawable(this, R.color.lineBackgroundStairs, R.color.lineForegroundStairs, R.color.lineForegroundHintAuto, R.drawable.ic_stairs).apply { iconSize = 0.8 })
            resolver.add({ it is Hike },                    drawable=IconLineDrawable(this, R.color.lineBackgroundWalkLong,            R.color.lineForegroundWalkLong,            R.color.lineForegroundHintAuto,  R.drawable.ic_hike)     .apply { iconPaddingLeft = 0.25; iconPaddingRight = 0.07; paddingRight = 0.0 })
            resolver.add({ it is Walk && it.isRunning },    drawable=IconLineDrawable(this, R.color.lineBackgroundWalkRun,             R.color.lineForegroundWalkRun,             R.color.lineForegroundHintAuto,  R.drawable.ic_run)      .apply { iconPaddingLeft = 0.25; iconPaddingRight = 0.07; paddingRight = 0.0 })
            resolver.add({ it is Walk },                    drawable=IconLineDrawable(this, R.color.lineBackgroundWalk,                R.color.lineForegroundWalk,                R.color.lineForegroundHintAuto,  R.drawable.ic_walk)     .apply { iconPaddingLeft = 0.25; iconPaddingRight = 0.07; paddingRight = 0.0 })
        }
    }

    private fun enableCompatSupportForVectorDrawables() {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }
}