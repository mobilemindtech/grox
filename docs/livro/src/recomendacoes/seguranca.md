# Segurança: checklist

Uma lista para revisar antes de colocar uma aplicação no ar. O Grox já liga várias proteções por padrão (CSRF, cabeçalhos de segurança, limite de tentativas de login, allowlist na sessão do Redis, tempo de login constante); a maior parte do trabalho é não desligá-las sem motivo e configurar o que depende do seu ambiente.

## Autenticação

- [ ] Senhas com `password.algorithm: bcrypt` (padrão). Os digests sem sal (`SHA-256`, `MD5`) existem para ler bancos legados; planeje a migração para bcrypt com um `passwordEncoder` que peça `upgradeEncoding`.
- [ ] Nada de `{noop}` fora de desenvolvimento. Usuários do bloco `users` do yml (ou do `security { }` da DSL) são para dev e testes.
- [ ] `rest.token.storage.jwt.secret` com ao menos 32 bytes aleatórios, vindo de variável de ambiente. Sem ele, o segredo é aleatório por execução.
- [ ] Validade curta do access token (`jwt.expiration`, padrão 1 h) e refresh token com a validade que o negócio aceita (`refreshExpiration`, padrão 30 dias).
- [ ] Limite de tentativas de login ligado (`grox.security.loginThrottle.enabled`, padrão `true`).
- [ ] Contas com `enabled`, `accountLocked`, `accountExpired` e `passwordExpired` mapeadas, se o seu modelo de usuário tiver esses estados.

## Autorização

- [ ] `rejectIfNoRule: true` (padrão): o que não tem regra é negado.
- [ ] Uma regra final `'/**'` explícita (`isAuthenticated()` ou `denyAll`), para não depender só do `rejectIfNoRule`.
- [ ] `@Secured` nas actions sensíveis, além das regras de URL. A anotação é avaliada antes das regras.
- [ ] `/assets/**` e `/static/**` liberados (`permitAll`) só se forem de fato públicos.
- [ ] Rotas do `grox-dsl` com `access:` na rota, no grupo ou no `@Handler`.

## Sessão e CSRF

- [ ] CSRF ligado (`csrf.enabled`, padrão `true`). Use `<g:form>` (inclui o `_csrf`) e envie `X-CSRF-TOKEN` no AJAX. Se uma app migrada precisar desligar, registre a decisão e planeje religar.
- [ ] Caminhos de API sem sessão em `stateless` ou com `chainMap` sem `securityContextPersistenceFilter`: aceitam só token e não precisam de CSRF.
- [ ] `grox.session.cookie.secure: auto` (padrão) ou `true` atrás de HTTPS.
- [ ] Sessão no Redis: não amplie `grox.session.serialization.allow` com pacotes de bibliotecas de terceiros (abre espaço para gadgets de desserialização).
- [ ] Nenhuma entidade do GORM na sessão: guarde ids.

## Binding e dados

- [ ] `bindable: false` nas constraints de campos que o usuário não pode escolher: tenant, papéis, flags de conta, preços calculados.
- [ ] Telas de administração que precisam desses campos usam `bindData(obj, params, [include: [...]])`.
- [ ] SQL nativo (`createNativeQuery`, `groovy.sql.Sql`) **não é filtrado por tenant**: inclua `tenant_id = :tenant` com `Tenants.currentId()`.
- [ ] HQL sempre com parâmetros (`:nome` ou `?1`), nunca concatenando entrada do usuário.
- [ ] `Tenants.withoutId { }` só onde é preciso ver todos os tenants, e nunca com entrada do usuário decidindo o tenant.

## Exposição

- [ ] Console web desligado em produção (`grox.console.enabled` é `true` só em development). Se precisar dele, exija `ROLE_ADMIN` e saiba que ele executa qualquer código com os privilégios da aplicação.
- [ ] O bean `consoleService` existe mesmo com o console desligado: nunca passe entrada do usuário ao `eval`.
- [ ] `/actuator/metrics` e `/prometheus` restritos (`grox.management.metricsAccess`, padrão `[ROLE_ADMIN]`).
- [ ] Ambiente `production`: páginas de erro sem mensagem de exceção nem stack trace.
- [ ] `grox.http.trustedProxies` só com os endereços dos seus proxies. Exposta direto à internet, use `''`.
- [ ] Cabeçalhos de segurança ligados (`grox.http.securityHeaders`); HSTS acrescentado quando houver HTTPS.
- [ ] `grox.http.maxContentLength` no tamanho do maior upload esperado (os uploads ficam em memória).

## Plugins

- [ ] PDF (plugin rendering): não monte templates com URLs vindas do usuário em `<img src>`. O Flying Saucer busca as URLs do documento no servidor.
- [ ] RabbitMQ com `ssl: true` valida o certificado pelo truststore da JVM; configure o truststore em vez de desligar a validação.
- [ ] Redis com `useSSL` verifica o nome do host.
- [ ] Auditoria com `asyncWrite` ou dentro de `Tenants.withoutId`: confira que os registros saem com o tenant e o usuário certos.

## Veja também

- [Segurança: regras de acesso](../recursos/seguranca-regras.md)
- [Segurança: autenticação e usuários](../recursos/seguranca-autenticacao.md)
- [Segurança: REST, JWT e tokens](../recursos/seguranca-rest.md)
- [Segurança: CSRF e proteções](../recursos/seguranca-protecoes.md)
- [Implantação em produção](implantacao.md)
