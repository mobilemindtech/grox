package org.springframework.security.authentication

/** Compatibilidade com o {@code ProviderManager} (bean {@code authenticationManager}): só guarda os providers. */
class ProviderManager {
    final List providers

    ProviderManager(List providers) { this.providers = providers ?: [] }

    ProviderManager(Object... providers) { this.providers = providers as List }
}
