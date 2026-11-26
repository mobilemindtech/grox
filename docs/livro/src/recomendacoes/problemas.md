# Solução de problemas

Sintomas conhecidos, a causa e o que fazer, agrupados por área. No fim, as ferramentas de depuração do framework.

## Build e compilação

| Sintoma | Causa | Solução |
|---|---|---|
| `NoClassDefFoundError: org/codehaus/groovy/runtime/callsite/CallSite` | Jar compilado com Groovy ≤ 5 (Grails, GORM, plugins, bibliotecas Groovy antigas) | Remova a dependência. Use a API emulada pelo Grox ou importe os fontes e compile com Groovy 6 |
| O build falha em `compileGsp` com `arquivo.gsp:13: ...` | Erro de sintaxe ou de Groovy numa GSP | Corrija a linha indicada. O erro aparece no build, não na primeira requisição |
| O build falha em `assetCompile` | `require` de um asset inexistente | Corrija o caminho. Em desenvolvimento é só um aviso, no build é erro |
| Erro de compilação num `where { }` | Expressão não suportada (`nome.size() > 3`, funções, subqueries) ou propriedade inexistente | Reescreva com os operadores suportados, use `createCriteria` ou HQL |
| O compilador fica pendurado em projetos grandes | Memória do worker do Groovy (512 MB padrão) | `tasks.withType(GroovyCompile) { groovyOptions.forkOptions.memoryMaximumSize = '4g' }` |
| `Illegal method name` ao rodar um teste | Nome de teste entre aspas com `.`, `/`, `;`, `[` ou `<` | Troque esses caracteres no nome do método |
| `Cannot select from a parameterized type` | `@DelegatesTo(value = Tipo<A>)` | Use `@DelegatesTo(Tipo)` |

## Subida

| Sintoma | Causa | Solução |
|---|---|---|
| `Unable to determine Dialect` | URL do banco ausente ou de outro banco que o driver | Defina `dataSource.url` (as variáveis do placeholder podem estar vazias) |
| A subida trava num `ALTER TABLE` | `dbCreate: update` com `grox.schema.update: full` alterando colunas de uma tabela grande | Volte ao padrão `safe`, ou use `dbCreate: none` e aplique o DDL por migração |
| A app criou tabelas ou colunas novas num banco existente | Nome divergente do GORM (palavras reservadas, siglas), ou `hasMany` unidirecional sem a tabela de junção esperada | Gere o DDL com `grox.schema.updateScript` antes, e ajuste `mapping` |
| `Multi-tenancy sem grails.gorm.multiTenancy.tenantResolverClass` | Classes `MultiTenant` sem segurança ativa nem resolver | Configure o `tenantResolverClass` |
| A expressão de acesso impede a subida | Regra inválida em `interceptUrlMap`, `staticRules` ou `@Secured` | Corrija a expressão indicada no log |
| `o segredo JWT precisa ter ao menos 32 bytes` | `jwt.secret` curto | Use um segredo maior |
| Aviso: `grails.redis configurado, mas o jedis não está no classpath` | Plugin sem a dependência | Declare `implementation libs.jedis` (ou remova o bloco) |
| Bloco do YAML vira uma `String` | Versões antigas: variável de ambiente homônima (`MAIL`, `PATH`) | O Grox atual só sobrepõe valores-folha; atualize |

## Requisições

| Sintoma | Causa | Solução |
|---|---|---|
| `403` em POST de formulário | CSRF sem token | `<g:form>`, cabeçalho `X-CSRF-TOKEN` ou `csrf.enabled: false` |
| `403` ao acessar dados | `TenantNotFoundException`: classe `MultiTenant` sem tenant | Login com usuário que tem tenant, ou `Tenants.withId` |
| `401` numa rota de API com token válido | `chainMap` sem `restTokenValidationFilter` naquele caminho, ou `useBearerToken: false` e o token no cabeçalho errado | Confira o `chainMap` e o `headerName` |
| `400` com `.` ou `..` no caminho | Proteção contra contorno de regras | Não use segmentos `.`/`..` nas URLs |
| `405` | Método fora do `allowedMethods`, ou rota da DSL com outro verbo | Ajuste o `allowedMethods` ou a rota |
| `413` | Corpo maior que `grox.http.maxContentLength` | Aumente o limite |
| `429` no login | Limite de tentativas | Espere a janela (`windowSeconds`) ou ajuste `grox.security.loginThrottle.*` |
| `503` com erro na página, em desenvolvimento | A recarga completa falhou (erro no BootStrap, mapeamento inválido) | Corrija e salve: a próxima recarga sobe a nova geração |
| `503` numa action assíncrona | A promise passou de `grox.async.timeout` | Aumente o timeout ou divida o trabalho |
| A página de erro não mostra a mensagem | Ambiente fora de development | Veja o log, ou rode em development |
| Um segundo `render` não aparece | `render x as JSON` fecha o writer, como no Grails | Faça um só render por resposta |

## Dados e sessão

| Sintoma | Causa | Solução |
|---|---|---|
| `LazyInitializationException` | Entidade carregada numa sessão e usada em outra (tarefa assíncrona, job, thread própria) | Carregue dentro da tarefa, ou use `withTransaction { }` em volta do uso |
| `EntityFilterException` num one-to-one | Filho multi-tenant ausente (versões antigas) | Use `hasOne`; o Grox atual mapeia como many-to-one por fórmula e devolve `null` |
| Alteração não gravada | Sem `save()` e fora de transação | Chame `save()` |
| Entidade inválida gravada | Uma transação posterior da mesma requisição fez flush | Chame `discard()` depois de uma validação que falhou |
| `NotSerializableException` com o nome de um atributo | Sessão no Redis com um valor não `Serializable` | Guarde ids ou objetos serializáveis; nunca entidades |
| Atributo da sessão sumiu, com aviso no log | Classe fora da allowlist na leitura do Redis | Acrescente o pacote em `grox.session.serialization.allow` |
| Depois de uma recarga completa, a sessão tem objetos de "outra classe" | Instâncias da geração anterior na sessão em memória | Guarde ids na sessão |
| HQL recusado: `?0`, `?` ou nomes de coluna | Hibernate 6+ é mais estrito | `?0` e `?` o Grox renumera; nomes de coluna (`tenant_id`) precisam virar nomes de propriedade |
| `sort` por coleção falha | O Hibernate não ordena por `hasMany` | Ordene por uma propriedade simples |
| `session.createSQLQuery` não existe | Removido no Hibernate 6 | Use `createNativeQuery` |

## Concorrência

| Sintoma | Causa | Solução |
|---|---|---|
| Requisições param e não respondem | Ciclo entre services-ator (A → B → A) | Quebre o ciclo |
| Throughput baixo num controller | Actions bloqueando todos os atores do pool | Promise (`task { }`) para esperas longas, ou `poolSize` maior |
| Valores trocados entre requisições | Estado em campos de controller (modo `pool`) ou em services singleton | Mova o estado para a sessão, o banco ou um service-ator |

## Depuração

| Ferramenta | Como |
|---|---|
| SQL gerado | `dataSource.logSql: true` (e `formatSql: true`) |
| Mapeamento Hibernate | `grox.data.ormXmlFile: /tmp/orm.xml` grava o `orm.xml` gerado. Se o Hibernate falha na subida, o XML vai para o log. No runtime, `app.datastore.ormXml` |
| DDL pendente | `grox.schema.updateScript` (com `grox.schema.scriptOnly: true` para encerrar em seguida) |
| GSP gerada | `CompiledGsp.generatedSource` guarda o Groovy produzido; cada linha tem `//@gsp:N` com a linha original |
| Nível de log | Com `slf4j-simple`: `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug` mostra as actions registradas e as GSPs compiladas. Com Logback, ajuste o logger `org.grox` |
| Fases da subida | Linha `Subida por fase` no log, ou `groxRuntime.startupPhases` |
| Requisições | `grox.accessLog.enabled: true` |
| Estado da aplicação | Console web (`/console`): beans em `ctx`, domain classes importadas, rollback |
| Métricas por rota | `/actuator/metrics` (count, errors, avgMs, maxMs por `controller.action`) |
| Exceções por requisição | JFR: `jfr print --events jdk.JavaExceptionThrow --stack-depth 20 arquivo.jfr` |
| Erros de GSP | A mensagem traz arquivo e linha da GSP (`/usuario/show.gsp:13`) |
| Página de erro | Em development, o erro padrão (sem mapeamento `"500"`) inclui o stack trace |

## Veja também

- [Perguntas frequentes](faq.md)
- [Armadilhas do Groovy 6](../apendices/groovy6.md)
- [Armadilhas da migração](../migracao/armadilhas.md)
- [Configuração](../referencia/configuracao.md)
