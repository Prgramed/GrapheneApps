package dev.emusic.data.preferences

import dev.emusic.data.api.CredentialProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialProviderImpl @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
    private val credentialStore: CredentialStore,
) : CredentialProvider {

    private val scope = CoroutineScope(SupervisorJob())

    @Volatile
    private var cached: AppPreferences = AppPreferences()

    init {
        preferencesRepository.preferencesFlow
            .onEach { cached = it }
            .launchIn(scope)
    }

    override val serverUrl: String get() = cached.serverUrl
    override val username: String get() = cached.username
    override val password: String get() = credentialStore.password
}
