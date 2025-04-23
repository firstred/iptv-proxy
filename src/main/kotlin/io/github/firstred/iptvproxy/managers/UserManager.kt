package io.github.firstred.iptvproxy.managers

import io.github.firstred.iptvproxy.IptvUser
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.di.IptvUsersByName
import io.github.firstred.iptvproxy.utils.hash
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

    companion object {
        fun validateUserToken(username: String, token: String?): String? =
            if (token == (username + config.appSecret).hash()) username else null
    }
}
