package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.classes.IptvUser
import io.github.firstred.iptvproxy.dtos.config.IptvFlatServerConfig
import io.github.firstred.iptvproxy.dtos.config.IptvProxyUserConfig
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.compoundOr
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

fun List<String>.matchRegexpList(item: String): Boolean {
    return this.any {
        if (it.startsWith("regexp:")) {
            val regex = it.substring(7).toRegex()
            return regex.matches(item)
        }

        if (it.startsWith("glob:")) {
            val glob = it.substring(5)
            return glob.split("*").all { part -> item.contains(part, ignoreCase = true) }
        }

        item.contains(it, ignoreCase = true)
    }
}

fun IptvUser.isFilterActive(): Boolean {
    return channelBlacklist.isNotEmpty()
            || categoryBlacklist.isNotEmpty()
            || channelWhitelist.isNotEmpty()
            || categoryWhitelist.isNotEmpty()
}

fun IptvProxyUserConfig.isFilterActive(): Boolean {
    return channelBlacklist.isNotEmpty()
            || categoryBlacklist.isNotEmpty()
            || channelWhitelist.isNotEmpty()
            || categoryWhitelist.isNotEmpty()
}
fun ListFilters.isFilterActive(): Boolean {
    return channelBlacklist.isNotEmpty()
            || categoryBlacklist.isNotEmpty()
            || channelWhitelist.isNotEmpty()
            || categoryWhitelist.isNotEmpty()
}

fun IptvUser.isWhiteListOnly(): Boolean = channelWhitelist.isNotEmpty() || categoryWhitelist.isNotEmpty()
fun IptvProxyUserConfig.isWhiteListOnly(): Boolean = channelWhitelist.isNotEmpty() || categoryWhitelist.isNotEmpty()
fun ListFilters.isWhiteListOnly(): Boolean = channelWhitelist.isNotEmpty() || categoryWhitelist.isNotEmpty()

fun IptvUser.matchChannelBlacklist(channel: String): Boolean = channelBlacklist.matchRegexpList(channel)
fun IptvProxyUserConfig.matchChannelBlacklist(channel: String): Boolean = channelBlacklist.matchRegexpList(channel)
fun ListFilters.matchChannelBlacklist(channel: String): Boolean = channelBlacklist.matchRegexpList(channel)
fun IptvUser.matchChannelWhitelist(channel: String): Boolean = channelWhitelist.matchRegexpList(channel)
fun IptvProxyUserConfig.matchChannelWhitelist(channel: String): Boolean = channelWhitelist.matchRegexpList(channel)
fun ListFilters.matchChannelWhitelist(channel: String): Boolean = channelWhitelist.matchRegexpList(channel)
fun IptvUser.matchCategoryBlacklist(category: String): Boolean = categoryBlacklist.matchRegexpList(category)
fun IptvProxyUserConfig.matchCategoryBlacklist(category: String): Boolean = categoryBlacklist.matchRegexpList(category)
fun ListFilters.matchCategoryBlacklist(category: String): Boolean = categoryBlacklist.matchRegexpList(category)
fun IptvUser.matchCategoryWhitelist(category: String): Boolean = categoryWhitelist.matchRegexpList(category)
fun IptvProxyUserConfig.matchCategoryWhitelist(category: String): Boolean = categoryWhitelist.matchRegexpList(category)
fun ListFilters.matchCategoryWhitelist(category: String): Boolean = categoryWhitelist.matchRegexpList(category)

data class ListFilters(
    val channelBlacklist: List<String> = emptyList(),
    val categoryBlacklist: List<String> = emptyList(),
    val channelWhitelist: List<String> = emptyList(),
    val categoryWhitelist: List<String> = emptyList()
) {
    fun isNotEmpty(): Boolean {
        return channelBlacklist.isNotEmpty()
                || categoryBlacklist.isNotEmpty()
                || channelWhitelist.isNotEmpty()
                || categoryWhitelist.isNotEmpty()
    }

    fun applyToQuery(query: Query, nameColumn: Column<String>, categoryColumn: Column<String>): Query {
        if (isNotEmpty()) {
            if (isWhiteListOnly()) {
                query.andWhere {
                    (listOf(
                        nameColumn inList channelWhitelist.filter(String::isNotGlobOrRegexp),
                        categoryColumn inList categoryWhitelist.filter(String::isNotGlobOrRegexp),
                    )
                            + channelWhitelist.filter(String::isRegexp).map {
                        nameColumn regexp it.substringAfter(":")
                    }
                            + categoryWhitelist.filter(String::isRegexp).map {
                        categoryColumn regexp it.substringAfter(":")
                    }
                            ).compoundOr()
                }
            } else if (isNotEmpty() && isFilterActive()) {
                if (channelBlacklist.isNotEmpty()) {
                    query.andWhere {
                        nameColumn notInList categoryBlacklist.filter(String::isNotGlobOrRegexp)
                    }
                    channelBlacklist.filter(String::isRegexp).forEach {
                        query.andWhere {
                            nameColumn regexp it.substringAfter(":")
                        }
                    }
                }
                if (categoryBlacklist.isNotEmpty()) {
                    query.andWhere {
                        categoryColumn notInList channelBlacklist.filter(String::isNotGlobOrRegexp)
                    }
                    categoryBlacklist.filter(String::isRegexp).forEach {
                        query.andWhere {
                            categoryColumn regexp it.substringAfter(":")
                        }
                    }
                }
            }
        }

        return query
    }
}

fun IptvUser.toListFilters() = ListFilters(
    channelBlacklist = channelBlacklist,
    categoryBlacklist = categoryBlacklist,
    channelWhitelist = channelWhitelist,
    categoryWhitelist = categoryWhitelist
)

fun IptvProxyUserConfig.toListFilters() = ListFilters(
    channelBlacklist = channelBlacklist,
    categoryBlacklist = categoryBlacklist,
    channelWhitelist = channelWhitelist,
    categoryWhitelist = categoryWhitelist
)

fun CharSequence.isNotGlobOrRegexp(): Boolean =
    !this.startsWith("regexp:") && !this.startsWith("glob:")
fun CharSequence.isRegexp(): Boolean = this.startsWith("regexp:")

fun IptvFlatServerConfig.remapEpgChannelId(epgChannelId: String): String {
    for ((key, value) in epgRemapping) {
        if (key.startsWith("regexp:")) {
            val pattern = key.substringAfter("regexp:").toRegex()
            if (pattern.matches(epgChannelId)) return epgChannelId.replace(pattern, value)
        } else if (key == epgChannelId) {
            return value
        }
    }

    return epgChannelId
}
