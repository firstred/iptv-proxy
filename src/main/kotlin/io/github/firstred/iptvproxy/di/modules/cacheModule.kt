package io.github.firstred.iptvproxy.di.modules

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovieInfoEndpoint
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeriesInfoEndpoint
import io.github.firstred.iptvproxy.serialization.json
import org.koin.core.qualifier.named
import org.koin.dsl.module

val cacheModule = module {
    single(named("series-info")) {
        InMemoryKache<String, XtreamSeriesInfoEndpoint>(maxSize = if (config.cache.enabled) config.cache.size.seriesInfo.toLong() else 1L) {
            strategy = KacheStrategy.FIFO
            expireAfterWriteDuration = config.cache.ttl.seriesInfo
            sizeCalculator = { _, endpoint -> json.encodeToString(XtreamSeriesInfoEndpoint.serializer(), endpoint).length.toLong() }
        }
    }
    single(named("movie-info")) {
        InMemoryKache<String, XtreamMovieInfoEndpoint>(maxSize = if (config.cache.enabled) config.cache.size.movieInfo.toLong() else 1L) {
            strategy = KacheStrategy.FIFO
            expireAfterWriteDuration = config.cache.ttl.movieInfo
            sizeCalculator = { _, endpoint -> json.encodeToString(XtreamMovieInfoEndpoint.serializer(), endpoint).length.toLong() }
        }
    }

    single(named("video-chunks")) {
        InMemoryKache<String, ByteArray>(maxSize = if (config.cache.enabled) config.cache.size.videoChunks.toLong() else 1L) {
            strategy = KacheStrategy.FIFO
            expireAfterWriteDuration = config.cache.ttl.videoChunks
            sizeCalculator = { _, byteArray -> byteArray.size.toLong() }
        }
    }
}
