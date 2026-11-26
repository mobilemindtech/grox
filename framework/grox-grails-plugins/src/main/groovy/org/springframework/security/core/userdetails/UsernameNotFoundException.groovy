package org.springframework.security.core.userdetails

/** Compatibilidade: lançada por um {@code UserDetailsService} quando o usuário não existe. */
class UsernameNotFoundException extends RuntimeException {
    UsernameNotFoundException(String message = 'Usuário não encontrado', Throwable cause = null) {
        super(message, cause)
    }
}
