package io.github.firstred.iptvproxy.di.modules

import com.mayakapps.kache.FileKache
import com.mayakapps.kache.KacheStrategy
import io.github.firstred.iptvproxy.BuildConfig
import io.github.firstred.iptvproxy.classes.CacheExpiryTimers
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.di.hooksOf
import io.github.firstred.iptvproxy.utils.toInt
import io.github.z4kn4fein.semver.toVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

val cacheModule = module {
    single(named("cache")) {
        CoroutineScope(Job())
    }
    singleOf(::CacheExpiryTimers) binds hooksOf(CacheExpiryTimers::class)

    single(named("series-info")) {
        runBlocking { FileKache(
            directory = config.getCacheDirectory("series_info"),
            maxSize = if (config.cache.enabled) config.cache.size.seriesInfo.toLong() else 1L,
        ) {
            strategy = KacheStrategy.FIFO
            cacheVersion = BuildConfig.APP_VERSION.toVersion().toInt()
        } }
    }
    single(named("movie-info")) {
        runBlocking { FileKache(
            directory = config.getCacheDirectory("movie_info"),
            maxSize = if (config.cache.enabled) config.cache.size.movieInfo.toLong() else 1L,
        ) {
            strategy = KacheStrategy.FIFO
            cacheVersion = BuildConfig.APP_VERSION.toVersion().toInt()
        } }
    }
    single(named("video-chunks")) {
        runBlocking { FileKache(
            directory = config.getCacheDirectory("video_chunks"),
            maxSize = if (config.cache.enabled) config.cache.size.videoChunks.toLong() else 1L,
        ) {
            strategy = KacheStrategy.FIFO
        } }
    }
}
