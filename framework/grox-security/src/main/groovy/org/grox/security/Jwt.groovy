package org.grox.security

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

/** Tokens JWT HS256 (claims: sub, roles, tenant, uid, iat, exp, iss). */
@Slf4j
class Jwt {

    static final String ISSUER = 'grox'

    final byte[] secret
    final int expirationSeconds
    int refreshExpirationSeconds = 30 * 24 * 3600
    TokenRevocations revocations = new TokenRevocations.Memory()

    Jwt(String secret, int expirationSeconds) {
        if (secret) {
            if (secret.getBytes('UTF-8').length < 32) throw new IllegalArgumentException('o segredo JWT precisa ter ao menos 32 bytes')
            this.secret = secret.getBytes('UTF-8')
        } else {
            this.secret = new byte[32]
            new SecureRandom().nextBytes(this.secret)
            log.warn('grails.plugin.springsecurity.rest.token.storage.jwt.secret não definido: usando segredo aleatório (tokens não sobrevivem a reinícios nem funcionam entre nós)')
        }
        this.expirationSeconds = expirationSeconds
    }

    String create(GroxUser user) {
        long now = System.currentTimeMillis().intdiv(1000)
        Map claims = [sub: user.username, roles: user.roles as List, iat: now, exp: now + expirationSeconds, iss: ISSUER, jti: UUID.randomUUID().toString()]
        if (user.tenantId != null) claims.tenant = user.tenantId
        if (user.id != null) claims.uid = user.id
        encode(claims)
    }

    /** Refresh token: só serve para obter novos access tokens ({@code typ: refresh}). */
    String createRefresh(GroxUser user) {
        long now = System.currentTimeMillis().intdiv(1000)
        encode([sub: user.username, typ: 'refresh', iat: now, exp: now + refreshExpirationSeconds, iss: ISSUER, jti: UUID.randomUUID().toString()])
    }

    private String encode(Map claims) {
        String unsigned = b64(JsonOutput.toJson([alg: 'HS256', typ: 'JWT'])) + '.' + b64(JsonOutput.toJson(claims))
        unsigned + '.' + b64(sign(unsigned))
    }

    /** @return a autenticação ou null se o token for inválido, expirado, revogado ou de refresh */
    Authentication authenticate(String token) {
        Map claims = verify(token)
        if (claims == null || claims.typ == 'refresh') return null
        def user = new GroxUser(username: claims.sub, tenantId: (Serializable) claims.tenant, id: (Serializable) claims.uid)
        user.roles = (claims.roles ?: []) as List<String>
        new Authentication(principal: user, method: 'bearer', credentials: [jti: claims.jti, exp: claims.exp])
    }

    /** Claims de um refresh token válido (ou null). */
    Map verifyRefresh(String token) {
        Map claims = verify(token)
        claims?.typ == 'refresh' ? claims : null
    }

    void revoke(Map claims) {
        if (claims?.jti) revocations.revoke(claims.jti.toString(), claims.exp as long)
    }

    /** Assinatura, emissor, expiração e revogação. */
    Map verify(String token) {
        String[] parts = token.split(/\./)
        if (parts.length != 3) return null
        byte[] expected = sign(parts[0] + '.' + parts[1])
        byte[] given
        try {
            given = Base64.urlDecoder.decode(parts[2])
            Map header = (Map) new JsonSlurper().parseText(new String(Base64.urlDecoder.decode(parts[0]), 'UTF-8'))
            if (header.alg != 'HS256') return null
        } catch (IllegalArgumentException | groovy.json.JsonException ignored) {
            return null
        }
        if (!MessageDigest.isEqual(expected, given)) return null
        Map claims = (Map) new JsonSlurper().parseText(new String(Base64.urlDecoder.decode(parts[1]), 'UTF-8'))
        if (claims.iss != ISSUER || !(claims.exp instanceof Number) || (claims.exp as long) < System.currentTimeMillis().intdiv(1000)) return null
        if (claims.jti && revocations.isRevoked(claims.jti.toString())) return null
        claims
    }

    private byte[] sign(String data) {
        Mac mac = Mac.getInstance('HmacSHA256')
        mac.init(new SecretKeySpec(secret, 'HmacSHA256'))
        mac.doFinal(data.getBytes('UTF-8'))
    }

    private static String b64(Object v) {
        byte[] bytes = v instanceof byte[] ? (byte[]) v : v.toString().getBytes('UTF-8')
        Base64.urlEncoder.withoutPadding().encodeToString(bytes)
    }
}
