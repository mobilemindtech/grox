package org.grox.security

/**
 * Tokens opacos do login REST (sem JWT), guardados fora do processo: a implementação com
 * domain class fica no módulo {@code org.grox.security.gorm} ({@code rest.token.storage.useGorm}).
 */
interface TokenStore {

    /** Gera e grava um token para o usuário. */
    String store(String username)

    /** Usuário do token, ou null se o token não existe ou o usuário está bloqueado. */
    GroxUser load(String token, UserDetailsService users)

    /** @return false se o token não existia */
    boolean remove(String token)
}
