package org.grox.security

/**
 * Fonte de usuários. Para trocar a padrão (GORM + usuários do yml), declare na aplicação
 * um service {@code UserDetailsService} (bean {@code userDetailsService}) que implemente
 * esta interface.
 */
interface UserDetailsService {
    /** @return o usuário (com a senha codificada) ou null */
    GroxUser loadUserByUsername(String username)
}
