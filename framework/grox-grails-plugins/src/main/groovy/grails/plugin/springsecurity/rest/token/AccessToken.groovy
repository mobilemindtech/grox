package grails.plugin.springsecurity.rest.token

/** Compatibilidade com o {@code AccessToken} do spring-security-rest (token emitido no login REST). */
class AccessToken {
    String accessToken
    String refreshToken
    /** Segundos até expirar (null: sem expiração, como no armazenamento GORM). */
    Integer expiration
    /** O usuário autenticado ({@code username}, {@code authorities}...). */
    Object principal

    String getTokenType() { 'Bearer' }
}
