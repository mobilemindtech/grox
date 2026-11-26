package grails.plugin.springsecurity.userdetails

import org.springframework.security.core.userdetails.UsernameNotFoundException

/** Compatibilidade: {@code UsernameNotFoundException} sem stack trace (usuário inexistente é esperado). */
class NoStackUsernameNotFoundException extends UsernameNotFoundException {

    private static final long serialVersionUID = 1L

    NoStackUsernameNotFoundException() {
        super('Usuário não encontrado')
    }

    @Override
    synchronized Throwable fillInStackTrace() {
        this
    }
}
