package org.sumidoku.android.core.image.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.sumidoku.android.core.image.upscale.EnhancementCache
import org.sumidoku.android.core.image.upscale.ImageEnhancer

/**
 * Entry point for the Application, which builds Coil's image loader before any ViewModel exists.
 * `SumidokuApp` is `@HiltAndroidApp`, but the loader factory may be called from outside an injected
 * component, so the upscaling singletons are fetched explicitly instead of relying on field-injection
 * ordering.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpscaleEntryPoint {
    fun enhancementCache(): EnhancementCache
    fun imageEnhancer(): ImageEnhancer
}
