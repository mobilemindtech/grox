package org.springframework.security.core.userdetails

/**
 * Compatibilidade com o {@code UserDetailsPasswordService} do Spring Security: chamado depois de um
 * login bem-sucedido quando o {@code passwordEncoder} pede {@code upgradeEncoding}, com a senha já
 * recodificada. Bean {@code userDetailsPasswordService} (ou a propriedade do {@code DaoAuthenticationProvider}).
 */
interface UserDetailsPasswordService {
    UserDetails updatePassword(UserDetails user, String newPassword)
}
