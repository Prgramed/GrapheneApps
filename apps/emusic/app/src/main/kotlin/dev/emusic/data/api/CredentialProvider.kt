package dev.emusic.data.api

interface CredentialProvider {
    val serverUrl: String
    val username: String
    val password: String
}
