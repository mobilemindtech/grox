# Sessão HTTP

Este capítulo cobre a sessão HTTP: os stores em memória e no Redis, como e quando a sessão é gravada, as regras de serialização, o cookie, a troca de id no login e o `sessionRegistry`.

## Uso

```groovy
session.carrinho = []
session.carrinho << item
session.getAttribute('usuarioId')
session.removeAttribute('x')
session.invalidate()
```

A API é a mesma nos dois stores, e a sessão implementa `jakarta.servlet.http.HttpSession`.

## Configuração

```yaml
grox:
  session:
    store: redis          # memory (padrão) ou redis
    timeout: 1800         # segundos de inatividade
    cookieName: GROXSESSIONID
    cookie:
      secure: auto        # auto, true ou false
    redis:
      url: redis://localhost:6379        # com senha: redis://:senha@host:6379
      keyPrefix: 'grox:session:'
    serialization:
      allow: ['br.com.empresa.modelo']   # pacotes extras aceitos na leitura do Redis
```

## Stores

| Store | Quando usar |
|---|---|
| `memory` | Um nó só. As sessões ficam na memória do processo e somem no reinício. Até `grox.session.maxActive` sessões (padrão 100 mil): cheio, saem as expiradas e depois as 10% acessadas há mais tempo, que sob um robô são as dele |
| `redis` | Vários nós ou sessões que sobrevivem a reinícios. Cliente Lettuce (que usa o Netty, sem outra pilha de IO) |

Outros stores (JDBC, Hazelcast) não existem; quem precisar pode implementar `org.grox.session.SessionRepository`.

## Como a sessão é gravada

A sessão é carregada uma vez por requisição e gravada **no fim**, só com o que mudou (modelo do Spring Session):

- atributos alterados e removidos;
- atributos **lidos com valor mutável**, para que `session.carrinho << item` também seja salvo;
- a autenticação e o flash não são regravados só por terem sido lidos: valores que implementam `org.grox.session.SessionValue` dizem se mudaram (`isModified()`).

No Redis:

- cada sessão é um hash `grox:session:<id>` com TTL igual ao timeout;
- **uma ida ao Redis por gravação:** um script Lua faz HSET, HDEL e EXPIRE de forma atômica;
- uma sessão existente só é gravada se a chave ainda existir. Assim, um logout (ou a expiração) durante uma requisição concorrente não é desfeito quando ela termina.

## Serialização (Redis)

A serialização é a do Java:

- **todo atributo precisa ser `Serializable`**; senão a gravação falha com `NotSerializableException` e o nome do atributo;
- **não guarde entidades do GORM na sessão**: guarde o id e carregue de novo.

Na leitura, só são aceitas classes de uma allowlist *(extensão Grox)*:

- JDK comum (`java.lang`, `java.util`, `java.time`, `java.math`), o framework e `GString`;
- os pacotes da aplicação: o da classe `Application`, o `grails.codegen.defaultPackage` e a lista `grox.session.serialization.allow`.

Qualquer outra classe é recusada e o atributo é ignorado, com aviso no log. Isso impede que quem consiga gravar no Redis execute código por um "gadget" de biblioteca.

## Cookie

- Nome `GROXSESSIONID` (`grox.session.cookieName`), `HttpOnly` e `SameSite=Lax`.
- `Secure` conforme `grox.session.cookie.secure`: `auto` (padrão, em HTTPS, inclusive atrás de proxy confiável), `true` ou `false`.

## Login e logout

- O login troca o id da sessão (proteção contra session fixation) e gera um token CSRF novo. O mesmo vale para `springSecurityService.reauthenticate(username)` e para `SecurityContextHolder.context.authentication = ...` com outro usuário.
- O logout invalida a sessão.
- Para anônimos, a sessão só é criada quando algo a usa: um atributo gravado, o flash ou o token CSRF de um `<g:form>`. Uma página pública com formulário cria uma sessão para cada visitante sem cookie (ver [Desempenho](../recomendacoes/desempenho.md#formulários-em-páginas-públicas)).
- Caminhos `stateless` e cadeias sem `securityContextPersistenceFilter` não leem nem criam sessão (ver [regras de acesso](seguranca-regras.md)).

## `sessionRegistry`

O `SessionRegistryImpl` do Spring Security enxerga as sessões ativas, em memória ou no Redis. Como no spring-security-core, a aplicação declara o bean:

```groovy
// grails-app/conf/spring/resources.groovy
beans = {
    sessionRegistry(org.springframework.security.core.session.SessionRegistryImpl)
}
```

```groovy
def sessionRegistry
sessionRegistry.getAllSessions(usuario, false)*.expireNow()   // derruba as sessões do usuário
```

`expireNow()` apaga a sessão na hora (no Spring, o `ConcurrentSessionFilter` desloga na próxima requisição). O `ConcurrentSessionControlAuthenticationStrategy` é aceito como configuração, mas o limite de sessões por usuário não é aplicado.

## Recarga em desenvolvimento

Na recarga de classes, a sessão continua (o usuário segue logado). Instâncias guardadas na sessão em memória são da geração antiga; depois de uma recarga completa, uma domain class guardada é de outra classe. Mais um motivo para guardar ids.

## Migrando do spring-session-redis

O Grox não lê `server.servlet.session` nem `spring.session.redis`. Crie um bloco `grox.session` equivalente:

```yaml
grox:
  session:
    store: redis
    cookieName: APPSESSION
    timeout: 3600
    redis:
      url: "${SESSION_REDIS_URL:redis://localhost:6379}"
      keyPrefix: 'app:grox:session:'
```

As chaves e a serialização são diferentes das do Spring Session. Os dois convivem no mesmo Redis, mas as sessões não são compartilhadas: na troca, quem estiver logado entra de novo.

## Diferenças para o Grails

| Recurso | Grox |
|---|---|
| Container de Servlet | Não há: a sessão é própria, com a interface `HttpSession` |
| spring-session | Substituído por `grox.session.store: redis` |
| Atributos não serializáveis no Redis | ❌ (erro claro ao gravar) |
| Allowlist de desserialização | *(extensão Grox)* |
| Limite de sessões por usuário | ❌ |

## Veja também

- [Segurança: autenticação e usuários](seguranca-autenticacao.md)
- [Segurança: CSRF e proteções](seguranca-protecoes.md)
- [Recarga em desenvolvimento](recarga.md)
- [Implantação em produção](../recomendacoes/implantacao.md)
