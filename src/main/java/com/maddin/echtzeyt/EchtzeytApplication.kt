package com.maddin.echtzeyt

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.multidex.MultiDexApplication
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.maddin.echtzeyt.components.DefaultVehicleTypeResolver
import com.maddin.echtzeyt.randomcode.IconLineDrawable
import com.maddin.echtzeyt.randomcode.LineDrawable
import com.maddin.transportapi.components.VehicleTypes

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
            addDefaultVehicleTypeBadges()
            configure()
            enableCompatSupportForVectorDrawables()
        } catch (_: Throwable) {}
    }

    private fun setCorrectNightMode() {
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

        themedContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)
            this
        }

        if (AppCompatDelegate.getDefaultNightMode() == nightMode) { return }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun addDefaultVehicleTypeBadges() {
        (ECHTZEYT_CONFIGURATION.vehicleTypeResolver as? DefaultVehicleTypeResolver)?.let {
            it.defaultDrawable = LineDrawable(this, R.color.lineBackgroundDefault, R.color.lineForegroundDefault, R.color.lineForegroundHintLight)
            it.add(VehicleTypes.BUS, drawable=IconLineDrawable(this, R.color.lineBackgroundBus, R.color.lineForegroundBus, R.color.lineForegroundHintDark, R.drawable.ic_bus))
            it.add(VehicleTypes.TRAM, drawable=IconLineDrawable(this, R.color.lineBackgroundTram, R.color.lineForegroundTram, R.color.lineForegroundHintDark, R.drawable.ic_tram).apply { iconSize = 0.8 })
            it.add(VehicleTypes.SUBWAY, drawable=LineDrawable(this, R.color.lineBackgroundSubway, R.color.lineForegroundSubway, R.color.lineForegroundHintDark).apply { radius = 0.4 })
            it.add(VehicleTypes.TRAIN_SUBURBAN, drawable=IconLineDrawable(this, R.color.lineBackgroundSuburban, R.color.lineForegroundSuburban, R.color.lineForegroundHintDark, R.drawable.ic_suburban).apply { radius = 0.5 })
            it.add(VehicleTypes.CABLECAR, drawable=IconLineDrawable(this, R.color.lineBackgroundCablecar, R.color.lineForegroundCablecar, R.color.lineForegroundHintLight, R.drawable.ic_cablecar))
            it.add(VehicleTypes.TRAIN, drawable=IconLineDrawable(this, R.color.lineBackgroundTrain, R.color.lineForegroundTrain, R.color.lineForegroundHintLight, R.drawable.ic_train))
            it.add(VehicleTypes.BUS_NIGHT, drawable=IconLineDrawable(this, R.color.lineBackgroundBusNight, R.color.lineForegroundBusNight, R.color.lineForegroundHintDark, R.drawable.ic_bus))
            it.add(VehicleTypes.PLANE, drawable=IconLineDrawable(this, R.color.lineBackgroundPlane, R.color.lineForegroundPlane, R.color.lineForegroundHintDark, R.drawable.ic_plane))
            it.add(VehicleTypes.HELICOPTER, drawable=IconLineDrawable(this, R.color.lineBackgroundHelicopter, R.color.lineForegroundHelicopter, R.color.lineForegroundHintLight, R.drawable.ic_helicopter))
            it.add(VehicleTypes.BIKE, drawable=IconLineDrawable(this, R.color.lineBackgroundBike, R.color.lineForegroundBike, R.color.lineForegroundHintAuto, R.drawable.ic_bike))
            it.add(VehicleTypes.FERRY, drawable=IconLineDrawable(this, R.color.lineBackgroundFerry, R.color.lineForegroundFerry, R.color.lineForegroundHintDark, R.drawable.ic_boat))
            it.add(VehicleTypes.ESCALATOR, drawable=IconLineDrawable(this, R.color.lineBackgroundEscalator, R.color.lineForegroundEscalator, R.color.lineForegroundHintAuto, R.drawable.ic_escalator))
            it.add(VehicleTypes.SCOOTER, drawable=IconLineDrawable(this, R.color.lineBackgroundScooter, R.color.lineForegroundScooter, R.color.lineForegroundHintAuto, R.drawable.ic_scooter))
            it.add(VehicleTypes.SEGWAY, drawable=IconLineDrawable(this, R.color.lineBackgroundSegway, R.color.lineForegroundSegway, R.color.lineForegroundHintAuto, R.drawable.ic_segway))
            it.add(VehicleTypes.WALK, drawable=IconLineDrawable(this, R.color.lineBackgroundWalk, R.color.lineForegroundWalk, R.color.lineForegroundHintAuto, R.drawable.ic_walk).apply { iconPaddingLeft = 0.25; paddingRight = 0.0 })
            it.add(VehicleTypes.WALK_RUN, drawable=IconLineDrawable(this, R.color.lineBackgroundWalkRun, R.color.lineForegroundWalkRun, R.color.lineForegroundHintAuto, R.drawable.ic_run).apply { iconPaddingLeft = 0.25; paddingRight = 0.0 })
            it.add(VehicleTypes.WALK_LONG, drawable=IconLineDrawable(this, R.color.lineBackgroundWalkLong, R.color.lineForegroundWalkLong, R.color.lineForegroundHintAuto, R.drawable.ic_hike).apply { iconPaddingLeft = 0.25; paddingRight = 0.0 })
            it.add(VehicleTypes.STAIRS, drawable=IconLineDrawable(this, R.color.lineBackgroundStairs, R.color.lineForegroundStairs, R.color.lineForegroundHintAuto, R.drawable.ic_stairs).apply { iconSize = 0.8 })
            it.add(VehicleTypes.BUS_TRAIN_REPLACEMENT, drawable=IconLineDrawable(this, R.color.lineBackgroundBusTrainReplacement, R.color.lineForegroundBusTrainReplacement, R.color.lineForegroundHintLight, R.drawable.ic_bus))
        }
    }

    private fun enableCompatSupportForVectorDrawables() {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }
}