package org.springframework.security.authentication

/** Bean {@code authenticationTrustResolver}: no Grox não há remember-me, e anônimo é a ausência de autenticação. */
class AuthenticationTrustResolver {
    boolean isAnonymous(Object authentication) { authentication == null }

    boolean isRememberMe(Object authentication) { false }

    boolean isFullyAuthenticated(Object authentication) { authentication != null }

    boolean isAuthenticated(Object authentication) { authentication != null }
}
