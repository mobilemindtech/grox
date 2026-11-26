# Plugins reimplementados

Este capítulo cobre os plugins do Grails que o Grox reimplementa: console web, redis, rabbitmq-native, rendering (PDF), excel-export, audit-logging, hibernate-filter e o mínimo do GPars. Os jars originais dependem do Spring e do Groovy 5, então não rodam no Groovy 6. O Grox reimplementa **só o que os projetos reais usam**, com a API pública no pacote original: o código da aplicação compila sem mudanças.

Quartz e asset-pipeline têm capítulos próprios: [Jobs agendados](jobs.md) e [Assets e estáticos](assets.md). Spring Security tem os capítulos de segurança.

| Plugin | Ativação | Dependência que a app declara |
|---|---|---|
| console | `grox.console.enabled` (padrão: só em development) | |
| redis | Sempre que o jedis estiver no classpath | `redis.clients:jedis` |
| rabbitmq-native | `rabbitmq.connections` configurado | `com.rabbitmq:amqp-client` |
| rendering | flying-saucer no classpath | `org.xhtmlrenderer:flying-saucer-pdf` |
| excel-export | POI no classpath | `org.apache.poi:poi-ooxml` |
| audit-logging | `grails.plugin.auditLog.auditDomainClassName` | |
| hibernate-filter | `static hibernateFilters` nas domain classes | |

As dependências pesadas ficam `compileOnly` no framework: a aplicação declara só as que usa.

## Console web

`/console` executa scripts Groovy dentro da aplicação, como o plugin console. Serve para inspecionar dados, testar services e corrigir registros.

- **Editor:** `Ctrl+Enter` executa o script ou só a seleção. Histórico e rascunho ficam no navegador.
- **Rollback no final:** roda o script numa transação desfeita ao terminar, para experimentar gravações.
- **Bindings:** `ctx` com todos os beans (`ctx.usuarioService`, `ctx.getBean('redisService')`, `ctx.beanNames`), `grailsApplication`, `config`, `runtime`, `request`, `session`, `springSecurityService`, `log`, `out` e cada service pelo nome.
- **Imports:** os pacotes das domain classes, services e controllers já vêm importados, então `Usuario.where { ... }.list()` funciona direto, assim como `Tenants`, `JSON` e `DetachedCriteria`.
- **Contexto:** o script roda com a sessão de dados e a autenticação de quem executa. O tenant é o do usuário logado; use `Tenants.withId(...)` para outro.
- **Retorno:** o resultado (`inspect()`), a saída de `println` e os erros (compilação com a linha, execução com as linhas do script).

```bash
curl -X POST http://localhost:8080/console/execute \
     -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
     -d '{"code": "Usuario.count()", "rollback": false}'
# {"result": "3", "resultType": "java.lang.Integer", "output": "", "error": null, "durationMs": 4, "rolledBack": false}
```

Com JWT dispensa o CSRF; com sessão, envie `X-CSRF-TOKEN`.

| Chave | Padrão | |
|---|---|---|
| `grox.console.enabled` | `true` só em development | Também `grails.plugin.console.enabled`. Fora de development, a subida avisa no log |
| `grox.console.path` | `/console` | |
| `grox.console.access` | `[ROLE_ADMIN]` | Com segurança ativa |
| `grox.console.localOnly` | `true` | **Sem** segurança, só aceita localhost |

Proteções: o `/execute` exige `Content-Type: application/json` (que um formulário HTML não envia) e recusa `Origin` diferente do `Host` ou `Sec-Fetch-Site` de outro site. Sem segurança ativa, só aceita `Host` localhost (contra DNS rebinding) e recusa `X-Forwarded-For`/`Forwarded`. O acesso é conferido mesmo em cadeias do `chainMap` sem autorização. Cada execução é registrada no log `org.grox.plugins.console.WebConsole`, com o usuário e a duração.

O bean `consoleService` (`eval(codigo)`, do plugin web-console) executa código com os mesmos bindings. Ele existe mesmo com o console desligado: nunca passe entrada do usuário a ele.

**O console executa qualquer código com os privilégios da aplicação.** Não o ligue em produção sem necessidade.

Diferenças: editor simples (sem CodeMirror); arquivos salvos no servidor ❌.

## Redis (plugin redis)

```groovy
// build.gradle
implementation libs.jedis      // ou 'redis.clients:jedis:<versão>'
```

```yaml
grails:
  redis:
    host: localhost          # port 6379, timeout 2000, password, database, useSSL, poolConfig: [maxTotal: 8...]
    connections:             # opcionais: redisService<Nome> e redisService.withConnection('cache')
      - name: cache
        database: 1
```

Sem `grails.redis`, a conexão é `localhost:6379`, como no plugin. O pool só conecta no primeiro uso.

```groovy
redisService.withRedis { Jedis redis -> redis.setex(chave, 60, json) }
def total = redisService.memoize("total:${id}", [expire: 300]) { calcular() }            // String em cache
Cliente c = redisService.memoizeObject(Cliente, "cli:${id}", 300) { Cliente.get(id) }    // JSON (Gson)
redisService.ultimoAcesso = 'agora'                                                     // SET direto
redisService.del('chave')                                                               // comandos do Jedis
redisService.withOptionalRedis { r -> r.get('x') }                                      // null se o redis estiver fora
redisService.deleteKeysWithPattern('cli:*')
```

Também: `memoizeHash`, `memoizeHashField`, `memoizeScore`, `withPipeline`, `withTransaction`. `sentinels` + `masterName` usam o `JedisSentinelPool`. `useSSL` verifica o nome do host.

Não suportados: `memoizeDomainList`, `memoizeDomainObject` e as anotações `@Memoize`.

O Redis da sessão HTTP (`grox.session.redis`) é outra conexão, pelo Lettuce, e não depende deste plugin.

## RabbitMQ (plugin rabbitmq-native, subconjunto)

```yaml
rabbitmq:
  connections:
    - name: main
      host: localhost
      username: guest
      password: '${RABBIT_PASSWORD}'
      ssl: false
  exchanges:
    - {name: app, type: topic}
  queues:
    - {name: alertas, exchange: app, binding: 'app.alertas'}
```

O Grox conecta, declara exchanges e filas (com `exchange` e `binding`) e cria o bean `rabbitMessagePublisher`:

```groovy
rabbitMessagePublisher.send(new RabbitMessageProperties(exchange: 'app.desk', routingKey: "tenant-${id}", body: bytes, expiration: '60000'))
rabbitMessagePublisher.send { exchange = 'app'; routingKey = 'app.alertas'; body = [tipo: 'x'] }   // Map/List viram JSON
rabbitMessagePublisher.send('fila', 'texto')
```

- Sem o broker, a aplicação sobe e o envio falha. `rabbitmq.enabled: false` desliga.
- Cada envio usa um canal só seu, emprestado de um pool pequeno por conexão (até 16 ociosos), sem abrir e fechar um canal por mensagem. Um canal com erro é descartado.
- `ssl: true` valida o certificado pelo truststore da JVM (`-Djavax.net.ssl.trustStore=...`) e o nome do host. O plugin original confiava em qualquer certificado.
- Não existem ainda: consumers (`grails-app/rabbit-consumers`, `static rabbitConfig`), RPC e confirms.
- `static rabbitQueue` em services é do plugin rabbitmq antigo: o rabbitmq-native o ignora, e o Grox também (com aviso no log).

## PDF (plugin rendering)

```groovy
// build.gradle
implementation 'org.xhtmlrenderer:flying-saucer-pdf:<versão>'
```

```groovy
pdfRenderingService.render([template: 'recibo', controller: this, model: [conta: c], filename: 'recibo.pdf'], response)
ByteArrayOutputStream pdf = pdfRenderingService.render(template: '/recibo/recibo', model: [conta: c])
pdfRenderingService.render([template: '/recibo/recibo', model: m], outputStream)
renderPdf(template: 'recibo', model: [conta: c])                  // no controller: template relativo a ele
```

- O template precisa ser **XHTML bem formado**.
- `base` (ou `grails.serverURL`) resolve URLs relativas de imagens e CSS.
- Imagens em bytes vão embutidas: `<rendering:inlinePng bytes="${logo}" height="66px"/>` (também `inlineJpeg` e `inlineGif`); os demais atributos vão para o `<img>`.
- O nome do arquivo no `Content-Disposition` é escapado.
- Renderizar GIF, PNG ou JPEG como saída ❌.

Cuidado: um template que coloca URL vinda do usuário em `<img src>` faz o servidor buscar essa URL (inclusive endereços internos ou `file:`). Use só URLs controladas pela aplicação.

## Excel (plugin excel-export)

```groovy
// build.gradle
implementation 'org.apache.poi:poi-ooxml:<versão>'
```

```groovy
import pl.touk.excel.export.WebXlsxExporter

def xls = new WebXlsxExporter()
xls.setWorksheetName('Contas')
xls.setResponseHeaders(response, 'contas.xlsx')
xls.fillHeader(['Cliente', 'Cidade', 'Total'])
xls.add(contas, ['cliente.nome', 'cidade', { it.total * 2 }])   // propriedades, caminhos, closures ou chaves de mapa
xls.putCellValue(0, 0, 'Cliente').bold().centre()
xls.fillRow(['Total', '', soma], contas.size() + 1)
xls.save(response.outputStream)
```

Sobre o POI 5. Subconjunto: `putCellValue`, estilos `bold`, `centre`, `thickBorder`, `left`, `right`, `setWorksheetName`, `setResponseHeaders`, `fillHeader`, `fillRow`, `add`, `getCellAt`, `sheet(nome)` (outra planilha) e `save(outputStream)`. Fora de uma resposta HTTP, use `XlsxExporter` (sem `setResponseHeaders`) e grave num `FileOutputStream`.

## Auditoria (plugin audit-logging)

```yaml
grails:
  plugin:
    auditLog:
      auditDomainClassName: app.AuditTrail   # obrigatório: ativa o plugin
      logFullClassName: false                # className sem o pacote
      mask: [senha]                          # valor gravado como propertyMask
      asyncWrite: true                       # grava num executor depois do commit
```

```groovy
import grails.plugins.orm.auditable.Auditable

class Contrato implements Auditable {
    String numero
    BigDecimal valor
}
```

- **O que é gravado:** um registro por propriedade, com `eventName` (`INSERT`, `UPDATE`, `DELETE`), `propertyName`, `oldValue`, `newValue`, `actor`, `uri`, `className`, `persistedObjectId` e `persistedObjectVersion`. No UPDATE, só as alteradas, com o valor antigo vindo do banco. Os valores são truncados no `maxSize` da domain de auditoria.
- **Quando:** depois do commit, numa sessão nova. Com `asyncWrite`, num executor de virtual threads (`asyncWriteThreads`, `asyncWriteQueueCapacity`). Sem transação, grava na hora.
- **Configuração:** as chaves e padrões do plugin: `verbose`, `excluded`, `included`, `mask`, `propertyMask`, `logIds`, `defaultActor`, `failOnError`, `truncateLength`, `ignoreEvents`. A domain pode sobrescrever os métodos do trait (`isAuditable(evento)`, `getLogExcluded()`, `beforeSaveLog(audit)`...).
- **Quem:** o bean `auditRequestResolver` (`AuditRequestResolver`, com `currentActor` e `currentURI`). O padrão é o `SpringSecurityRequestResolver` com segurança ativa; uma subclasse pode ser registrada no `resources.groovy`.
- **Desligar num trecho:** `AuditLogContext.withoutAuditLog { ... }` ou `AuditLogContext.withConfig([verbose: false]) { ... }`.
- A domain de auditoria segue o formato do `audit-quickstart`.

Limites: alterações gravadas só pelo flush automático, sem `save()`, não são auditadas; `Stampable` compila, mas `createdBy`/`lastUpdatedBy` não são gravados; vários datasources ❌. Sob carga, prefira `asyncWrite`: a gravação síncrona pede uma segunda conexão enquanto a da requisição está presa.

## Filtros do Hibernate (plugin hibernate-filter)

`static hibernateFilters`, `withoutHibernateFilters`, `withoutHibernateFilter`, `withHibernateFilters`, `enableHibernateFilter` e `disableHibernateFilter`. Ver [Domain classes](domain.md#filtros-do-hibernate-plugin-hibernate-filter).

## GPars (mínimo)

O jar do GPars não roda no Groovy 6. O Grox oferece o que os projetos usam, sobre o `groovy.concurrent` do Groovy 6:

```groovy
import groovyx.gpars.GParsPool

GParsPool.withPool(4) {
    def totais = pedidos.collectParallel { calcular(it) }
    itens.eachParallel { processar(it) }
    def grandes = itens.findAllParallel { it.valor > 1000 }
}
new DefaultPGroup().task { ... }
```

Atores e dataflow do GPars não existem: use os do `groovy.concurrent` (`Actor`, `Awaitable`).

## Veja também

- [Jobs agendados](jobs.md)
- [Assets e estáticos](assets.md)
- [Beans e resources.groovy](beans.md)
- [Plugins do Grails](../migracao/plugins.md)
- [Configuração](../referencia/configuracao.md)
