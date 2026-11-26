# Segurança sem o plugin

O módulo `grox-security` é o motor de segurança do Grox: regras de URL, expressões de acesso, login por formulário, HTTP Basic, JWT, CSRF e senhas com bcrypt. Ele não depende do módulo Grails nem do plugin spring-security, e lê as mesmas chaves de `grails.plugin.springsecurity.*`. Este capítulo mostra como ativá-lo numa app do `grox-dsl`, as regras (inclusive o `access:` das rotas), os mecanismos de login, as fontes de usuários e o que muda sem o plugin.

## Ativando

A segurança fica ativa quando existe o bloco `grails.plugin.springsecurity` na configuração (yml, `application.groovy`) ou um bloco `security { }` na [DSL de projeto](dsl-projeto.md):

```groovy
class Aplicacao {
    static app = {
        security {
            user 'admin', password: '{noop}admin', roles: ['ROLE_ADMIN']
            user 'leitor', password: '{noop}leitor', roles: ['ROLE_USER']
            useBasicAuth = true
            stateless = ['/api/**']
        }
    }
    static void main(String[] args) { GroxApp.run(Aplicacao, args) }
}
```

O mesmo em YAML:

```yaml
grails:
  plugin:
    springsecurity:
      useBasicAuth: true
      stateless: ['/api/**']
      users:
        - {username: admin, password: '{noop}admin', roles: [ROLE_ADMIN]}
        - {username: leitor, password: '{noop}leitor', roles: [ROLE_USER]}
```

`{noop}` guarda a senha em texto: use só em desenvolvimento. Em produção, grave o hash (`{bcrypt}$2a$...`), gerado com `org.grox.security.Passwords.encode('senha')`.

## Regras de acesso

A ordem de avaliação de cada requisição:

1. o `access:` da rota (ou do `group`, do `@Handler`, do `@Get`...), que vale como um `@Secured`;
2. a **primeira** regra que casar com o caminho e o método, nesta ordem: `interceptUrlMap`, `controllerAnnotations.staticRules` e as regras `rule` da DSL de projeto;
3. senão, `rejectIfNoRule` (padrão `true`: negado).

```groovy
// src/main/resources/application.groovy: o formato que o s2-quickstart gera
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
    [pattern: '/', access: ['permitAll']],
    [pattern: '/sobre', access: ['permitAll']],
    [pattern: '/livros', httpMethod: 'GET', access: ['permitAll']],
    [pattern: '/livros/**', httpMethod: 'GET', access: ['permitAll']],
    [pattern: '/api/livros/**', httpMethod: 'GET', access: ['permitAll']],
    [pattern: '/api/**', access: ['ROLE_ADMIN']],
    [pattern: '/relatorios/**', access: ['ROLE_ADMIN']],
    [pattern: '/**', access: ['isAuthenticated()']]
]
```

```groovy
// nas rotas
routes {
    get('/app/info', access: 'permitAll') { render json: [ok: true] }
    group('/admin', access: 'ROLE_ADMIN') {
        get('/painel') { render { h1 'Painel' } }
    }
}

@Handler(value = '/api/relatorios', access = ['ROLE_ADMIN'])
class RelatoriosApi {
    @Get(value = '/publico', access = ['permitAll']) def publico() { render json: [ok: true] }
}
```

- **`access`:** concede se **qualquer** item conceder. Itens: `permitAll`, `denyAll`, `isAuthenticated()`, `isFullyAuthenticated()`, `isAnonymous()`, `ROLE_X`, `hasRole('X')`, `hasAnyRole(...)`, `hasAuthority(...)`, `hasAnyAuthority(...)`, os legados `IS_AUTHENTICATED_*`, e combinações com `and`, `or`, `!` e parênteses. Uma expressão inválida impede a subida.
- **Padrões Ant:** `/admin/**` casa com `/admin` e tudo abaixo; `*` com um segmento; `?` com um caractere.
- **Sempre liberadas:** as URLs do próprio login (`/login/auth`, `/login/authenticate`, `/logoff`, `/api/login`) e `/assets/**`. Os estáticos (`/static/**`) em GET dispensam a cadeia de segurança quando a regra libera anônimos.
- **Sem permissão:** navegador sem login vai para o login (e a URL pedida é guardada); API, XHR, JSON ou caminho stateless recebem `401`; usuário logado sem a role recebe `403`.
- Caminhos com `.` ou `..` recebem `400`.

A sintaxe completa (inclusive `filterChain.chainMap`) está em [Segurança: regras de acesso](../recursos/seguranca-regras.md).

## Mecanismos de autenticação

| Mecanismo | Como |
|---|---|
| Formulário e sessão | `GET /login/auth` mostra a página (a `views/login/auth.gsp` da app, ou a padrão). `POST /login/authenticate` com `username`, `password` e `_csrf`. Sucesso: redireciona para a URL guardada ou `successHandler.defaultTargetUrl`; falha: `/login/auth?login_error=1`. Logout: `POST /logoff` com `_csrf` |
| HTTP Basic | `useBasicAuth: true`. Autentica a cada requisição, sem sessão |
| JWT | `rest.active: true`. `POST /api/login` com `{"username", "password"}` devolve `access_token` e `refresh_token`; depois, `Authorization: Bearer <token>`. Renovação em `POST /oauth/access_token`, logout em `POST /api/logout` |

```yaml
grails:
  plugin:
    springsecurity:
      rest:
        active: true
        token:
          storage:
            jwt:
              secret: "${JWT_SECRET}"     # no mínimo 32 bytes; sem ele, aleatório a cada subida
              expiration: 3600
```

- **`stateless`:** caminhos (padrões Ant) sem sessão nem CSRF, que só aceitam Basic ou Bearer.
- **Login:** troca o id da sessão (contra session fixation) e gera um novo token CSRF.
- **Limite de tentativas:** `grox.security.loginThrottle.*` (padrão 5 falhas por IP e usuário em 300 s). *(extensão Grox)*

Os detalhes de JWT, refresh, revogação e tokens em GORM estão em [Segurança: REST, JWT e tokens](../recursos/seguranca-rest.md).

## CSRF

O CSRF fica ativo por padrão (`csrf.enabled: true`) *(extensão Grox)*. Ele vale para POST, PUT, PATCH e DELETE com sessão.

- O `<g:form>` já inclui o campo `_csrf`. Num `<form>` escrito à mão, inclua `<input type="hidden" name="_csrf" value="...">` (na página de login, o modelo traz `csrfParameter` e `csrfToken`).
- Em AJAX, envie o cabeçalho `X-CSRF-TOKEN`.
- Não vale para Bearer, Basic de clientes de API, caminhos `stateless` e POST anônimo sem cookie de sessão que não venha de outro site.
- POST sem token recebe `403`.

Mais em [Segurança: CSRF e proteções](../recursos/seguranca-protecoes.md).

## Usuários

As fontes de usuários são consultadas nesta ordem:

1. um bean ou service `userDetailsService` que implemente `org.grox.security.UserDetailsService`;
2. os usuários em memória (`users` do yml e `user` da DSL de projeto);
3. as domain classes de `userLookup`, com o módulo `grox-security-gorm`.

Sem nenhuma fonte, a subida avisa no log.

### `UserDetailsService` próprio

Implemente a interface numa classe própria e registre-a como o bean `userDetailsService`:

```groovy
import org.grox.security.GroxUser
import org.grox.security.UserDetailsService

class LdapUsuarios implements UserDetailsService {
    def diretorio                                             // injetado pelo nome

    GroxUser loadUserByUsername(String username) {
        def u = diretorio.buscar(username)
        if (!u) return null                                   // usuário inexistente
        def g = new GroxUser(username: u.login, password: u.hashSenha, id: u.id, tenantId: u.empresa)
        g.roles = u.grupos.collect { "ROLE_${it.toUpperCase()}" }
        g
    }
}

// na DSL de projeto
beans { userDetailsService(LdapUsuarios) }
```

O `GroxUser` tem `username`, `password` (codificada), `authorities` (ou `roles`), `enabled`, `accountExpired`, `accountLocked`, `passwordExpired`, `id` e `tenantId`. Contas desativadas, bloqueadas ou expiradas falham no login, cada caso com a sua mensagem (`springSecurity.errors.login.*`).

### Usuários em domain classes

Com o módulo `grox-security-gorm` no build, os usuários vêm de domain classes, no padrão do `s2-quickstart`:

```groovy
dependencies {
    implementation project(':framework:grox-dsl')
    implementation project(':framework:grox-security-gorm')
}
```

```yaml
grails:
  plugin:
    springsecurity:
      userLookup:
        userDomainClassName: livraria.auth.Usuario
        authorityJoinClassName: livraria.auth.UsuarioPapel
        tenantPropertyName: tenantId          # tenant do usuário (extensão Grox)
      authority:
        className: livraria.auth.Papel
```

```groovy
import grails.gorm.annotation.Entity
import org.grox.security.Passwords

@Entity
class Usuario {
    String username
    String password
    boolean enabled = true
    boolean accountExpired, accountLocked, passwordExpired
    static constraints = { username blank: false, unique: true; password blank: false }
    def beforeInsert() { encode() }
    def beforeUpdate() { encode() }
    protected void encode() { if (!Passwords.isEncoded(password)) password = Passwords.encode(password) }
}
@Entity class Papel { String authority }
@Entity class UsuarioPapel { Usuario user; Papel role }
```

A junção é descoberta pelas propriedades do tipo do usuário e do papel; sem junção, vale a propriedade `authorities` do usuário. O mesmo módulo traz os tokens REST em domain class (`rest.token.storage.useGorm`) e define o tenant do usuário logado (`UserTenantResolver`) como resolver padrão de [multi-tenancy](../recursos/multi-tenancy.md).

## O usuário nas rotas e nas views

Sem o plugin (módulo `grox-grails-plugins`) não há `springSecurityService`, `SpringSecurityUtils` nem tags `sec:`. Uma classe que declara `springSecurityService` recebe `null`, e a subida avisa no log quais são. Use:

| Onde | Como |
|---|---|
| Rotas | `request.userPrincipal` (`null` sem login): `.name`, `.authorities*.authority`, `.principal` (o `GroxUser`, com `id` e `tenantId`) |
| Services e código fora da requisição | `org.grox.security.SecurityContextHolder.authentication` (propagada para services-ator e tarefas assíncronas) |
| GSP | `<g:if test="${request.userPrincipal}">`, `${request.userPrincipal.name}` |
| DSL de HTML | `if (request.userPrincipal) { span request.userPrincipal.name }` |

```groovy
get('/minha-conta', access: 'isAuthenticated()') {
    def auth = request.userPrincipal
    render json: [usuario: auth.name, papeis: auth.authorities*.authority, tenant: auth.principal.tenantId]
}
```

Para codificar senhas: `org.grox.security.Passwords.encode(raw)` (conforme `password.algorithm`, padrão bcrypt) e `Passwords.isEncoded(valor)`.

## Veja também

- [Segurança: regras de acesso](../recursos/seguranca-regras.md)
- [Segurança: autenticação e usuários](../recursos/seguranca-autenticacao.md)
- [Segurança: REST, JWT e tokens](../recursos/seguranca-rest.md)
- [Segurança: CSRF e proteções](../recursos/seguranca-protecoes.md)
- [Segurança: checklist](../recomendacoes/seguranca.md)
