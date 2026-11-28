package org.sumidoku.android

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import org.sumidoku.android.core.common.CoroutineFailures
import org.sumidoku.android.core.image.di.UpscaleEntryPoint
import org.sumidoku.android.core.image.upscale.EnhancementDecoder

@HiltAndroidApp
class SumidokuApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // View-model scopes carry no exception handler, so anything that escapes a
        // coroutine body used to kill the process. launchGuarded() routes those here.
        CoroutineFailures.sink = { error ->
            Log.e(LOG_TAG, "unhandled coroutine failure: ${error.message}", error)
        }
    }

    /**
     * The app has a single image loader, so the waifu2x decoder is registered here rather than on a
     * separate page loader. It returns null for requests that were not marked by
     * `EnhancementDecoder.applyTo`, so covers and thumbnails keep the default decode path.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val entryPoint = EntryPointAccessors.fromApplication(this, UpscaleEntryPoint::class.java)
        return ImageLoader.Builder(context)
            .components {
                add(
                    EnhancementDecoder.Factory(
                        context = this@SumidokuApp,
                        cache = entryPoint.enhancementCache(),
                        enhancer = entryPoint.imageEnhancer(),
                    ),
                )
            }
            .build()
    }

    private companion object {
        const val LOG_TAG = "Sumidoku/Coroutine"
    }
}
