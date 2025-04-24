package io.github.firstred.iptvproxy.managers

import io.github.firstred.iptvproxy.di.modules.IptvUsersByName
import io.github.firstred.iptvproxy.entities.IptvUser
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UserManager : KoinComponent {
    private val iptvUsersByName: IptvUsersByName by inject()

    fun isAllowedUser(username: String, password: String): Boolean {
        if (iptvUsersByName[username] == null) return false
        if (iptvUsersByName[username]?.password != password) return false

        return true
    }

    fun isAllowedUser(user: IptvUser): Boolean = isAllowedUser(user.username, user.password)

    fun getUser(username: String, password: String): IptvUser {
        val user = iptvUsersByName[username]

        if (null == user) throw IllegalArgumentException("User $username not found")
        if (password != user.password) throw IllegalArgumentException("Invalid password for user $username")

        return user
    }

    fun getUserOrNull(username: String, password: String): IptvUser? = try {
        getUser(username, password)
    } catch (_: IllegalArgumentException) {
        null
    }
}
