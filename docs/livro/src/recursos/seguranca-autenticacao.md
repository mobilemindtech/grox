# Segurança: autenticação e usuários

Este capítulo cobre como o usuário se autentica (formulário e sessão, HTTP Basic), a página de login, os controllers de login e logout embutidos, as fontes de usuários, as senhas, o `springSecurityService`, as tags `sec:` e a API usada por um `LoginController` próprio. Tokens REST ficam em [REST, JWT e tokens](seguranca-rest.md).

## Mecanismos

| Mecanismo | Como ativar |
|---|---|
| Formulário e sessão | Sempre que a segurança está ativa |
| HTTP Basic | `useBasicAuth: true` |
| JWT ou token GORM (spring-security-rest) | `rest.active: true` (ou existir o bloco `rest`). Ver [REST, JWT e tokens](seguranca-rest.md) |

## Login por formulário

| URL | Chave | Padrão |
|---|---|---|
| Página de login | `auth.loginFormUrl` | `/login/auth` |
| Envio do formulário (POST) | `apf.filterProcessesUrl` | `/login/authenticate` |
| Campos | `apf.usernameParameter`, `apf.passwordParameter` | `username`, `password` |
| Destino depois do login | `successHandler.defaultTargetUrl` | `/` |
| Sempre ir para o destino padrão | `successHandler.alwaysUseDefault` | `false` |
| Falha | `failureHandler.defaultFailureUrl` | `/login/auth?login_error=1` |
| Logout | `logout.filterProcessesUrl` | `/logoff` |
| Depois do logout | `logout.afterLogoutUrl` | `/` |
| Logout só por POST | `logout.postOnly` | `true` |

Todas as chaves ficam sob `grails.plugin.springsecurity`.

Fluxo:

1. `GET /login/auth` mostra a página.
2. `POST /login/authenticate` com `username`, `password` e `_csrf`.
3. No sucesso, redireciona para a URL salva (a que o usuário tentou abrir) ou para `defaultTargetUrl`. O id da sessão é trocado e o token CSRF renovado.
4. Na falha, redireciona para `defaultFailureUrl`, com a mensagem traduzida em `flash.message`.
5. O logout é `POST /logoff` com `_csrf`.

### A página de login

Se existir `views/login/auth.gsp`, ela é usada; senão, uma página padrão do framework. O modelo tem:

| Variável | Conteúdo |
|---|---|
| `postUrl` | URL do POST |
| `usernameParameter`, `passwordParameter` | Nomes dos campos |
| `csrfParameter`, `csrfToken` | Campo e valor do CSRF |
| `errorMessage` | Mensagem da última falha |

```html
<form action="${postUrl}" method="POST">
  <g:if test="${errorMessage}"><p class="erro">${errorMessage}</p></g:if>
  <input name="${usernameParameter}"/>
  <input type="password" name="${passwordParameter}"/>
  <input type="hidden" name="${csrfParameter}" value="${csrfToken}"/>
  <button>Entrar</button>
</form>
```

A página é renderizada com `controllerName`/`params.controller` = `login` e `actionName`/`params.action` = `auth` (templates relativos resolvem em `views/login/`), e com sessão de dados aberta (layouts que leem associações lazy funcionam).

### Controllers embutidos

Sem um `LoginController` da aplicação, o Grox faz o papel do controller do plugin:

- `/login` e `/login/index` redirecionam para `/login/auth` (ou para o `defaultTargetUrl`, se já logado);
- `/login/authfail` é aceito;
- a falha de login coloca a mensagem em `flash.message`.

Sem um `LogoutController` da aplicação, `/logout` e `/logout/index` (inclusive com id, como `<g:link controller="logout">`) fazem o logout como o `/logoff`: com `postOnly: true`, respondem 405 a GET, e o POST com sessão exige o `_csrf`.

Um `LoginController` ou `LogoutController` da aplicação é respeitado.

## HTTP Basic

```yaml
grails.plugin.springsecurity.useBasicAuth: true
grails.plugin.springsecurity.basic.realmName: Minha App   # padrão: Grox
```

Autentica a cada requisição, sem sessão. Útil para APIs internas e testes.

## Contas e mensagens

O login falha se `enabled` for `false` ou se `accountLocked`, `accountExpired` ou `passwordExpired` forem `true`. Cada caso tem a sua mensagem:

| Código | Padrão (pt) |
|---|---|
| `springSecurity.errors.login.fail` | Usuário ou senha inválidos. |
| `springSecurity.errors.login.disabled` | Sua conta está desativada. |
| `springSecurity.errors.login.locked` | Sua conta está bloqueada. |
| `springSecurity.errors.login.expired` | Sua conta expirou. |
| `springSecurity.errors.login.passwordExpired` | Sua senha expirou. |
| `springSecurity.errors.login.throttled` | Muitas tentativas de login... |

O tempo de resposta é o mesmo para usuário inexistente (há uma verificação de senha falsa com o mesmo custo), para não revelar quais usuários existem.

## Limite de tentativas *(extensão Grox)*

```yaml
grox:
  security:
    loginThrottle:
      enabled: true            # padrão
      maxAttempts: 5           # falhas por IP + usuário na janela
      maxAttemptsPerUser: 20   # falhas por usuário, de qualquer IP
      windowSeconds: 300
```

Vale para formulário, REST e Basic. Acima do limite, o formulário mostra `springSecurity.errors.login.throttled`; REST e Basic respondem `429` com `Retry-After`. Não há limite só por IP: atrás de um proxy, todos teriam o mesmo.

## Fontes de usuários

Os usuários são procurados nesta ordem:

1. um bean `userDetailsService` da aplicação (service ou `resources.groovy`);
2. os `users` do yml (ou `security { user ... }` da DSL de projeto);
3. as domain classes de `userLookup`.

### Usuários no yml *(extensão Grox)*

```yaml
grails:
  plugin:
    springsecurity:
      users:
        - {username: suporte, password: '{noop}suporte', roles: [ROLE_ADMIN]}
```

Úteis em desenvolvimento e em apps pequenas. Em produção, guarde a senha codificada (`{bcrypt}...`).

### Domain classes

No padrão do `s2-quickstart`:

```groovy
class User {
    String username
    String password
    String tenantId
    boolean enabled = true
    boolean accountExpired
    boolean accountLocked
    boolean passwordExpired

    static constraints = {
        username blank: false, unique: true
        password blank: false
        tenantId nullable: true
    }
    Set<Role> getAuthorities() { UserRole.findAllByUser(this)*.role as Set }
    def beforeInsert() { encodePassword() }
    def beforeUpdate() { if (isDirty('password')) encodePassword() }
    protected void encodePassword() { password = Passwords.encode(password) }
}
class Role { String authority }
class UserRole { User user; Role role }
```

```yaml
grails:
  plugin:
    springsecurity:
      userLookup:
        userDomainClassName: app.User
        authorityJoinClassName: app.UserRole
        tenantPropertyName: tenantId          # extensão Grox: tenant do usuário
      authority:
        className: app.Role
```

| Chave em `userLookup` | Padrão |
|---|---|
| `usernamePropertyName` | `username` |
| `passwordPropertyName` | `password` |
| `enabledPropertyName` | `enabled` |
| `accountExpiredPropertyName` | `accountExpired` |
| `accountLockedPropertyName` | `accountLocked` |
| `passwordExpiredPropertyName` | `passwordExpired` |
| `authoritiesPropertyName` | `authorities` |
| `tenantPropertyName` *(extensão Grox)* | `tenantId` |

`authority.nameField` (padrão `authority`) é o nome da propriedade da role. A junção é descoberta pelas propriedades do tipo `User` e `Role`; sem `authorityJoinClassName`, vale a propriedade `authorities` do usuário. As roles são lidas numa consulta só.

### `userDetailsService` próprio

Duas formas:

```groovy
// API do Grox
class MeusUsuarios implements org.grox.security.UserDetailsService { ... }

// estilo Spring Security (LDAP, outro banco...)
class LdapUserDetailsService implements GrailsUserDetailsService {
    UserDetails loadUserByUsername(String username) {
        def u = buscar(username)
        if (!u) throw new UsernameNotFoundException(username)
        new GrailsUser(u.login, u.hash, true, true, true, true,
                [new SimpleGrantedAuthority('ROLE_USER')], u.id)
    }
    UserDetails loadUserByUsername(String username, boolean loadRoles) { loadUserByUsername(username) }
}
// resources.groovy: userDetailsService(LdapUserDetailsService)
```

No estilo Spring, `UsernameNotFoundException` (e `NoStackUsernameNotFoundException`) significa usuário inexistente, e id e tenant vêm das propriedades `id` e `tenantId` do `UserDetails`.

## Senhas

As senhas ficam no formato do `DelegatingPasswordEncoder` do spring-security-core, então Grails e Grox leem o mesmo banco.

| `password.algorithm` | Grava | Lê também |
|---|---|---|
| `bcrypt` (padrão) | `{bcrypt}$2a$...` (`password.bcrypt.logrounds`, padrão 10) | `$2a$...` sem prefixo |
| `SHA-256`, `SHA-512`, `SHA-1`, `MD5` | `{SHA-256}hash` sem sal, como o Grails 8, com `password.hash.iterations` repetições e base64 se `password.encodeHashAsBase64` | `{SHA-256}{sal}hash` |
| `{noop}texto` | | Só para desenvolvimento |

Para codificar: `springSecurityService.encodePassword(raw)` ou `org.grox.security.Passwords.encode(raw)`. Codifique sempre que a senha mudar (`isDirty('password')`, como acima); `Passwords.isEncoded(x)` só reconhece hashes (bcrypt, ou digest com o tamanho certo), nunca `{noop}`, porque o valor pode vir do usuário. pbkdf2, scrypt e argon2 não são suportados.

### `passwordEncoder` da aplicação

Um bean `passwordEncoder` que implemente o `PasswordEncoder` do Spring Security (`DelegatingPasswordEncoder`, `BCryptPasswordEncoder`, `MessageDigestPasswordEncoder`, ou um próprio) substitui o `password.algorithm` no login e no `encodePassword`.

Se ele pedir `upgradeEncoding(hash)` (hash antigo), o login bem-sucedido chama o bean `userDetailsPasswordService` (`UserDetailsPasswordService.updatePassword(user, novoHash)`), como o `DaoAuthenticationProvider`. O upgrade só acontece depois das checagens de conta. Os dois beans também são lidos de um `DaoAuthenticationProvider` declarado em `doWithSpring` ou no `resources.groovy`.

## No código da aplicação

### `springSecurityService`

| Membro | |
|---|---|
| `currentUser` | A domain class do usuário logado |
| `principal` | `GroxUser` (`username`, `authorities*.authority`, `roles`, `id`, `tenantId`); sem login, a String `'anonymousUser'` |
| `loggedIn` | `true`/`false` |
| `authentication` | A autenticação; `null` sem login |
| `encodePassword(raw)` | Codifica |
| `reauthenticate(username)` | Troca o usuário da sessão (renova id da sessão e CSRF) |

`principal as GrailsUser` (ou `as UserDetails`) converte o `GroxUser`, com o id e sem a senha.

Nos controllers também existem `isLoggedIn()`, `getPrincipal()` e `getAuthenticatedUser()`. A autenticação é propagada para services-ator e tarefas assíncronas.

### `SpringSecurityUtils`

```groovy
SpringSecurityUtils.ifAllGranted('ROLE_A,ROLE_B')
SpringSecurityUtils.ifAnyGranted('ROLE_A,ROLE_B')
SpringSecurityUtils.ifNotGranted('ROLE_X')
SpringSecurityUtils.principalAuthorities
SpringSecurityUtils.securityConfig.userLookup.userDomainClassName   // ConfigObject com as chaves do plugin
```

### Tags `sec:`

| Tag | |
|---|---|
| `<sec:ifLoggedIn>`, `<sec:ifNotLoggedIn>` | Corpo conforme o login |
| `<sec:ifAllGranted roles="ROLE_A,ROLE_B">`, `ifAnyGranted`, `ifNotGranted` | Por roles |
| `<sec:username/>` | Nome do usuário (escapado) |
| `<sec:loggedInUserInfo field="tenantId"/>` | Propriedade do principal |
| `<sec:access expression="hasRole('ADMIN')">`, `<sec:access url="/admin">`, `<sec:noAccess ...>` | Pela expressão ou pela regra da URL |
| `<sec:csrfInput/>`, `${sec.csrfToken()}` | Campo e valor do CSRF |

`sec:link` não existe. As tags ficam no módulo do plugin spring-security (`grox-grails-plugins`); fora dele, use `request.userPrincipal`.

`request.userPrincipal` e `request.remoteUser` devolvem o usuário autenticado; `request.isUserInRole` é sempre `false` (use as tags ou o `SpringSecurityUtils`).

### `LoginController` próprio

Para apps que reescreveram o controller do spring-security-core, o Grox oferece:

- `SecurityContextHolder.context.authentication` (get, set e `clearContext()`);
- `HttpSessionRequestCache` e `SavedRequest` (a URL salva);
- `session[WebAttributes.AUTHENTICATION_EXCEPTION]` e `SpringSecurityUtils.SPRING_SECURITY_LAST_USERNAME_KEY`, com a exceção da falha (`BadCredentialsException`, `LockedException`, `DisabledException`...);
- `SpringSecurityUtils.securityConfig`;
- `authenticationTrustResolver`;
- `sessionRegistry` (ver [Sessão HTTP](sessao.md#sessionregistry));
- `springSecurityService.isAjax(request)`.

## Diferenças para o Grails

| Recurso | Grox |
|---|---|
| `authentication` sem login | `null` (no Spring, um `AnonymousAuthenticationToken`) |
| Usuários no yml, `tenantPropertyName`, limite de tentativas | *(extensão Grox)* |
| Remember-me, switch user, OAuth2/OIDC, LDAP pronto, Digest | ❌ (LDAP via `userDetailsService` próprio) |
| Hierarquia de roles, grupos, ACL | ❌ |
| pbkdf2, scrypt, argon2 | ❌ |
| `sec:link` | ❌ |
| `isUserInRole` | Sempre `false` |

## Veja também

- [Segurança: regras de acesso](seguranca-regras.md)
- [Segurança: REST, JWT e tokens](seguranca-rest.md)
- [Segurança: CSRF e proteções](seguranca-protecoes.md)
- [Multi-tenancy](multi-tenancy.md)
- [Sessão HTTP](sessao.md)
