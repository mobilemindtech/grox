package sample.auth

/** Token do login REST quando {@code rest.token.storage.useGorm: true} (ver TokenGormTest). */
class AuthenticationToken {
    String tokenValue
    String username
}
