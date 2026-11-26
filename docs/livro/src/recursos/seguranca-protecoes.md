# Segurança: CSRF e proteções

Este capítulo reúne as proteções que o Grox aplica além da autenticação e das regras de acesso: CSRF, cabeçalhos de segurança, cookie `Secure`, proxies confiáveis, limites do servidor HTTP, caminhos suspeitos, mensagens de erro em produção e a allowlist da sessão no Redis. Várias são extensões do Grox, ligadas por padrão, que o Grails não aplica.

## CSRF

**Ativo por padrão** *(extensão Grox)*: o spring-security-core do Grails não tem CSRF.

```yaml
grails.plugin.springsecurity.csrf.enabled: true   # padrão
```

### Como enviar o token

- **Formulários:** o `<g:form>` (e o `<g:uploadForm>`) já inclui o campo `_csrf`. Num `<form>` HTML puro, use `<sec:csrfInput/>` ou o campo à mão:

  ```html
  <form method="post" action="/x">
    <input type="hidden" name="_csrf" value="${sec.csrfToken()}"/>
  </form>
  ```

- **AJAX:** envie o cabeçalho `X-CSRF-TOKEN`:

  ```html
  <meta name="csrf" content="${sec.csrfToken()}"/>
  <script>
    fetch('/pedido/salvar', {method: 'POST', headers: {'X-CSRF-TOKEN': document.querySelector('meta[name=csrf]').content}, body: dados})
  </script>
  ```

- O `_csrf` e o `_method` funcionam também em formulários multipart.

### Quando o token é exigido

| Requisição | CSRF |
|---|---|
| POST, PUT, PATCH, DELETE com sessão | Exigido |
| GET, HEAD, OPTIONS | Não |
| Bearer (JWT ou token GORM) | Não |
| Caminho `stateless` | Não |
| Anônima **sem** cookie de sessão, sem vir de outro site (POST público de API) | Não: não há estado do usuário a forjar |
| Anônima vinda de outro site pelo navegador (`Sec-Fetch-Site`/`Origin`) | Exigido, mesmo sem sessão: evita login CSRF (a action poderia criar a sessão e logar a vítima na conta do atacante) |
| Login por formulário | Sempre exigido |
| HTTP Basic de cliente de API | Não |
| HTTP Basic vindo de um navegador em outro site (`Sec-Fetch-Site: cross-site`/`same-site`, ou `Origin` diferente do `Host`) | Exigido: o navegador reenvia sozinho credenciais Basic guardadas. Em caminho stateless, a requisição é recusada |

O token é renovado no login, no `reauthenticate` e ao trocar o usuário pelo `SecurityContextHolder`.

### Migrando apps sem token

Apps Grails cujas views e AJAX não enviam o token podem desligar o CSRF:

```yaml
grails.plugin.springsecurity.csrf.enabled: false
```

É o que os candidatos reais fizeram (app-c-web, app B, app A). A alternativa é trocar `<form>` por `<g:form>` e mandar o cabeçalho no AJAX. A injeção automática do token em todo formulário está no roadmap.

## Cabeçalhos de segurança *(extensão Grox)*

Acrescentados às respostas que não os definem:

| Cabeçalho | Valor padrão |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `SAMEORIGIN` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |

```yaml
grox:
  http:
    securityHeaders:
      Strict-Transport-Security: 'max-age=31536000; includeSubDomains'   # acrescenta
      X-Frame-Options: DENY                                              # troca
      Referrer-Policy: ''                                                # remove
# ou securityHeaders: false para desligar todos
```

## Cookie de sessão

`HttpOnly` e `SameSite=Lax` sempre. `Secure` conforme `grox.session.cookie.secure`:

| Valor | |
|---|---|
| `auto` (padrão) | Em HTTPS, inclusive atrás de proxy confiável que informe `X-Forwarded-Proto: https` |
| `true` | Sempre |
| `false` | Nunca |

## Proxies confiáveis

```yaml
grox.http.trustedProxies: '10\.0\.0\.\d+|127\.0\.0\.1'
```

Regex dos endereços cujos `X-Forwarded-For`, `X-Forwarded-Proto` e `X-Forwarded-Host` são aceitos, como o `internalProxies` do Tomcat. O padrão aceita loopback e redes privadas; vazio não aceita nenhum. De outros endereços, esses cabeçalhos são ignorados: o IP do cliente (usado no access log e no limite de tentativas de login) e o esquema não podem ser falsificados.

## Limites do servidor HTTP

| Chave | Padrão | Protege contra |
|---|---|---|
| `grox.http.maxContentLength` | `10485760` (10 MB) | Corpos enormes (acima: `413`). O `100-continue` respeita o limite |
| `grox.http.requestTimeout` | `30` s | Slowloris: prazo para receber a requisição inteira a partir do primeiro byte. `0` desliga |
| `grox.http.idleTimeout` | `60` s | Conexões keep-alive ociosas sem resposta pendente. `0` desliga |
| `grox.http.maxConnections` | `10000` | Conexões simultâneas; as excedentes são fechadas. `0` sem limite |
| `grox.http.maxPipelined` | `16` | Requisições em voo por conexão antes de pausar a leitura |

Métodos HTTP desconhecidos dão `405` ou `501`; charset ou multipart inválido, `400`.

## Caminhos e redirecionamentos

- Caminhos com segmentos `.` ou `..` recebem `400`, para não contornar as regras de acesso.
- `/admin.json` e `/admin/` são avaliados também pelo caminho canônico (ver [regras de acesso](seguranca-regras.md#padrões-de-caminho)).
- A URL salva antes do login não aceita destinos externos (`//evil.com`).
- O cache de estáticos não segue symlinks para fora do diretório público.
- `Content-Disposition` é escapado (downloads, PDF, Excel), e os links reversos também.
- `</` dentro de JSON é escapado, para JSON embutido em `<script>`.

## Erros em produção

Fora de development:

- as páginas e o JSON de erro padrão mostram só o status, sem a mensagem da exceção (SQL, tabelas, caminhos);
- o `/actuator/health` omite a mensagem da falha do banco ou do Redis.

As duas continuam no log. Em development, a página de erro inclui o stack trace.

O ambiente padrão é `production`: um artefato empacotado sobe protegido mesmo sem configurar nada (ver [Ambientes](../referencia/ambientes.md)).

## Sessão no Redis

A desserialização da sessão só aceita classes de uma allowlist (JDK comum, framework, pacotes da aplicação e `grox.session.serialization.allow`). Detalhes em [Sessão HTTP](sessao.md#serialização-redis).

## Login

- Tempo de resposta igual para usuário inexistente (verificação falsa com o mesmo custo, também com o `passwordEncoder` da app).
- Limite de tentativas por IP e usuário (`grox.security.loginThrottle`, ver [autenticação](seguranca-autenticacao.md#limite-de-tentativas-extensão-grox)).
- Session fixation: o id da sessão muda no login.

## Console web

O `/console` executa código com os privilégios da aplicação. Vem ligado só em development e tem proteções próprias (ver [Plugins reimplementados](plugins.md#console-web)).

## Multi-tenancy e SQL nativo

O filtro de tenant não vale para SQL nativo: inclua `tenant_id` nas consultas (ver [Multi-tenancy](multi-tenancy.md#sql-nativo)). E proteja os campos que o usuário não pode escolher com `bindable: false` (ver [Validação](validacao.md#binding)).

## Diferenças para o Grails

| Proteção | Grails | Grox |
|---|---|---|
| CSRF | Desligado (sem suporte no spring-security-core) | Ligado |
| Cabeçalhos de segurança | Não por padrão | Ligados |
| Limite de tentativas de login | Não | Ligado |
| Allowlist na sessão do Redis | Não | Ligada |
| Ambiente padrão | `production` | `production` |
| `/admin.json` contra `/admin/**` | Brecha do `AntPathMatcher` | Fechada |

## Veja também

- [Segurança: regras de acesso](seguranca-regras.md)
- [Segurança: autenticação e usuários](seguranca-autenticacao.md)
- [Sessão HTTP](sessao.md)
- [Segurança: checklist](../recomendacoes/seguranca.md)
- [Configuração](../referencia/configuracao.md)
