# Segurança: REST, JWT e tokens

Este capítulo cobre a autenticação por token no estilo do spring-security-rest: login em `/api/login`, JWT, refresh, logout com revogação, tokens guardados em domain class, o cabeçalho `X-Auth-Token` e o evento de criação de token.

Onde o token fica (JWT ou GORM) e como ele chega (`Authorization: Bearer` ou outro cabeçalho) são configurações independentes, como no plugin. O padrão é **JWT com `Authorization: Bearer`**.

## Ativação

```yaml
grails:
  plugin:
    springsecurity:
      rest:
        active: true
        login:
          endpointUrl: /api/login                     # padrão
        token:
          storage:
            jwt:
              secret: '${JWT_SECRET}'                 # no mínimo 32 bytes
              expiration: 3600                        # segundos (padrão)
              refreshExpiration: 2592000              # 30 dias (padrão)
      stateless: ['/api/**']                          # extensão Grox: sem sessão nem CSRF
```

`rest.active` é `true` quando o bloco `rest` existe.

**Configure o `secret`.** Sem ele, o segredo é aleatório a cada subida (e a cada recarga completa em desenvolvimento): os tokens emitidos deixam de valer.

## Login

```bash
curl -X POST http://localhost:8080/api/login \
     -H 'Content-Type: application/json' \
     -d '{"username": "admin", "password": "admin"}'
```

```json
{
  "username": "admin",
  "roles": ["ROLE_ADMIN"],
  "token_type": "Bearer",
  "access_token": "eyJ...",
  "expires_in": 3600,
  "refresh_token": "eyJ..."
}
```

Depois, em cada requisição:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/livros
```

- JWT HS256, com as claims `sub` (usuário), `roles`, `tenant`, `uid` (id) e `exp`.
- Token inválido, adulterado ou expirado dá `401`.
- Contas desativadas e o limite de tentativas valem no `/api/login` (ver [autenticação](seguranca-autenticacao.md#limite-de-tentativas-extensão-grox)).
- Com o `UserTenantResolver`, o tenant vem da claim `tenant`.

## Refresh

```bash
curl -X POST http://localhost:8080/oauth/access_token \
     -d 'grant_type=refresh_token&refresh_token=eyJ...'
```

- Aceita formulário ou JSON e devolve um novo access token.
- O usuário é **recarregado**, então roles novas e contas desativadas valem na hora.
- Refresh inválido dá `403`.
- O refresh token só serve para renovar: não é aceito como access token.
- URL em `rest.token.generation.refreshEndpointUrl` (padrão `/oauth/access_token`).

## Logout e revogação

```bash
curl -X POST http://localhost:8080/api/logout \
     -H "Authorization: Bearer $TOKEN" \
     -d 'refresh_token=eyJ...'
```

Revoga o access token e, se enviado, o refresh token. URL em `rest.logout.endpointUrl` (padrão `/api/logout`).

As revogações ficam em memória ou no Redis até a expiração de cada token:

```yaml
grox:
  security:
    tokenRevocation:
      store: redis      # padrão: o mesmo de grox.session.store
```

Com vários nós, use `redis`; em memória, cada nó só conhece as próprias revogações.

## Tokens em GORM

Como no plugin, o token pode ser opaco e guardado numa domain class, no lugar do JWT:

```yaml
grails:
  plugin:
    springsecurity:
      rest:
        token:
          storage:
            useGorm: true
            gorm:
              tokenDomainClassName: app.AuthenticationToken
              tokenValuePropertyName: tokenValue   # padrão
              usernamePropertyName: username       # padrão
              cacheSeconds: 30                     # extensão Grox (padrão 30; 0 desliga)
          validation:
            useBearerToken: false                  # padrão true
            headerName: X-Auth-Token               # padrão; usado quando useBearerToken é false
```

```groovy
class AuthenticationToken {
    String tokenValue
    String username
}
```

- **Login:** `POST /api/login` grava um registro e devolve `{"username", "roles", "token_type": "Bearer", "access_token"}`, sem `expires_in` nem `refresh_token`. O token é aleatório (160 bits, base 32, como o `SecureRandomTokenGenerator` do plugin) e vale até o logout.
- **Validação:** o token é procurado no banco e o usuário é recarregado pelo `username`, numa sessão de dados só e com as roles numa consulta.
- **Cache** *(extensão Grox)*: o usuário de cada token fica em memória por `cacheSeconds`, para não repetir as consultas a cada requisição. Roles novas e contas desativadas, bloqueadas ou expiradas passam a valer depois desse prazo. O logout invalida o cache na hora no mesmo nó; nos outros, vale depois do prazo.
- **Logout:** `POST /api/logout`, com o token no mesmo cabeçalho, apaga o registro.
- **Sem refresh:** `/oauth/access_token` só existe com JWT.
- Os tokens são gravados sem hash, como no plugin: os já emitidos pelo Grails no mesmo banco continuam valendo.
- Token inexistente dá `401`.

## Cabeçalho próprio

`useBearerToken` e `headerName` também valem para o JWT. Com `useBearerToken: false`, o token passa a ser lido do cabeçalho configurado, e `Authorization: Bearer` é ignorado:

```bash
curl -H "X-Auth-Token: $TOKEN" http://localhost:8080/api/livros
```

O parâmetro `access_token` no corpo do formulário não é aceito.

## Evento de criação de token

```groovy
import grails.plugin.springsecurity.rest.RestTokenCreationEvent
import org.springframework.context.ApplicationListener

class TokenCreationListener implements ApplicationListener<RestTokenCreationEvent> {
    void onApplicationEvent(RestTokenCreationEvent event) {
        log.info "login de ${event.accessToken.principal.username}"
    }
}
// resources.groovy: tokenCreationListener(TokenCreationListener)
```

- Publicado a cada token emitido em `POST /api/login` (JWT e GORM), de forma síncrona. Uma falha no listener vai para o log e não impede o login.
- `event.accessToken` tem `accessToken`, `refreshToken`, `expiration` e `principal`.
- Durante o evento, o usuário já está no contexto de segurança (`springSecurityService.currentUser` funciona), e por isso o resolver de tenant enxerga o tenant dele. No Grails o contexto ainda está vazio nesse ponto.

## Token e sessão no mesmo site

Para que `/api/**` aceite só token e o resto do site só sessão, use o `chainMap` (ver [regras de acesso](seguranca-regras.md#cadeia-de-filtros-filterchainchainmap)). O CSRF não se aplica a requisições com Bearer.

## Bean `jwtService`

Com JWT ativo, o bean `jwtService` valida um token recebido por outro caminho: `jwtService.parse(token)` devolve as claims (ou falha se o token for inválido ou expirado).

## Diferenças para o Grails

| Recurso | Grox |
|---|---|
| `/api/validate` | ❌ |
| JWE e assinatura RSA | ❌ (só HS256) |
| Parâmetro `access_token` no formulário | ❌ |
| Cache do token GORM (`cacheSeconds`) | *(extensão Grox)* |
| Revogação de JWT no logout (memória/Redis) | ✅ |
| Contexto de segurança no `RestTokenCreationEvent` | Já preenchido |

## Veja também

- [Segurança: regras de acesso](seguranca-regras.md)
- [Segurança: autenticação e usuários](seguranca-autenticacao.md)
- [Segurança: CSRF e proteções](seguranca-protecoes.md)
- [Beans e resources.groovy](beans.md)
