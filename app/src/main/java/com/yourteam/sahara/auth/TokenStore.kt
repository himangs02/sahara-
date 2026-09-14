package com.yourteam.sahara.auth

/** Where the backend JWT lives on-device. Kept separate from [AuthRepository]'s local session
 * (which drives the app's actual gating and is unaffected by this) -- this store only remembers
 * the last backend access token, for the Stage 3 background backend-verification path. */
interface TokenStore {
    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()
}
