package com.fixpoc

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.ReactApplication
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactContext
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.facebook.react.uimanager.DisplayMetricsHolder

class MainActivity : ReactActivity() {

    override fun getMainComponentName(): String = "FixPoC"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

    /**
     * Workaround for the bug this repo demonstrates.
     *
     * React Native's `DisplayMetricsHolder.initDisplayMetrics` calls
     * `WindowManager.defaultDisplay.getRealMetrics()`, which always reports
     * the device's primary display, regardless of which display the activity
     * is actually on. Push the activity-scoped metrics into the holder
     * directly, then re-emit dimensions to JS via the (internal)
     * `DeviceInfoModule.emitUpdateDimensionsEvent()`.
     */
    private fun applyActivityDisplayMetrics() {
        val window = DisplayMetrics().apply { setTo(resources.displayMetrics) }
        val screen = DisplayMetrics().apply { setTo(resources.displayMetrics) }

        val activityDisplay: Display? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
            }
        @Suppress("DEPRECATION")
        activityDisplay?.getRealMetrics(screen)
        @Suppress("DEPRECATION")
        screen.scaledDensity = window.scaledDensity

        DisplayMetricsHolder.setWindowDisplayMetrics(window)
        DisplayMetricsHolder.setScreenDisplayMetrics(screen)
    }

    private fun refreshDimensionsForActiveReactContext() {
        applyActivityDisplayMetrics()
        val reactContext: ReactContext =
            (application as? ReactApplication)?.reactHost?.currentReactContext ?: return
        emitDimensionsUpdate(reactContext)
    }

    private fun emitDimensionsUpdate(reactContext: ReactContext) {
        try {
            val moduleClass =
                Class.forName("com.facebook.react.modules.deviceinfo.DeviceInfoModule")
            @Suppress("UNCHECKED_CAST")
            val module =
                reactContext.getNativeModule(moduleClass as Class<out NativeModule>) ?: return
            moduleClass.getMethod("emitUpdateDimensionsEvent").invoke(module)
        } catch (e: Throwable) {
            android.util.Log.w("FixPoC", "Failed to emit dimensions update", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyActivityDisplayMetrics()
        super.onCreate(savedInstanceState)

        val reactHost = (application as? ReactApplication)?.reactHost
        val listener =
            object : ReactInstanceEventListener {
                override fun onReactContextInitialized(context: ReactContext) {
                    refreshDimensionsForActiveReactContext()
                }
            }
        reactHost?.addReactInstanceEventListener(listener)
        if (reactHost?.currentReactContext != null) {
            refreshDimensionsForActiveReactContext()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshDimensionsForActiveReactContext()
    }
}
