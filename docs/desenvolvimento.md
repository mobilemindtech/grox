# Desenvolvimento do framework

## Comandos

```bash
./gradlew build                       # tudo (framework, sample-app, app)
./gradlew :framework:test             # testes unitários do framework (todos os módulos)
./gradlew :framework:grox-core:compileGroovy   # compila um módulo só (grox-core, grox-gorm, grox-gsp...)
./gradlew :sample-app:test            # integração: sobe a app real em porta aleatória
./gradlew :sample-plain:test          # integração da app sem Grails (grox-dsl)
./gradlew :sample-app:run             # app de exemplo com reload de views
./gradlew :sample-app:compileGsp      # só pré-compila as GSPs (valida sintaxe)
bench/run.sh "" /usuario/index        # benchmark (ver docs/benchmark.md)
```

O framework está dividido em subprojetos `framework/grox-*` (ver [modularizacao.md](modularizacao.md)); o projeto `framework` só agrega os módulos e guarda os testes. Uma classe nova vai para o módulo do seu pacote (o mapa está no `ArquiteturaTest`, que falha se o arquivo estiver no subprojeto errado), e uma dependência nova vai no `build.gradle` desse módulo. O código dos módulos compila sem as ASTs globais (`framework/gradle/compilador-modulos.groovy`), que são para as aplicações.

O `gradle-plugin/` é um build incluído (`pluginManagement { includeBuild('gradle-plugin') }`): mudanças nele valem no próximo build, sem publicar nada.

Versões ficam em `gradle/libs.versions.toml`: Groovy 6.0.0, Netty 4.2.18, SiteMesh 3.2.3, Hibernate 7.4.11, HikariCP 7.1.0, Lettuce 7.8.0, bcrypt (at.favre) 0.10.2, Jakarta Servlet API 6.1.0 (só a API), H2 2.5.252 (testes e exemplo), SLF4J 2.0.20 e JUnit 6.1.3, com JDK 27 via toolchain. Regra do projeto: **só versões estáveis**.

## Testes

| Arquivo | Cobre |
|---|---|
| `framework/src/test/.../UrlMappingsTest` | Parsing de padrões, DSL completo, ordenação, reverse |
| `framework/src/test/.../ConfigTest` | YAML com vários documentos, chaves com ponto, environments, precedência |
| `framework/src/test/.../DataBindingTest` | Conversões, `bindData`, `GroxParams`, negociação por Accept |
| `framework/src/test/.../GspTest` | Motor GSP: encoding, sintaxe, tags, templates, layouts, reload, linhas de erro |
| `framework/src/test/.../GormTest` | Camada de dados com H2: AST, `orm.xml`, CRUD, ordenação e paginação, validação e mensagens, finders, associações e cascata, transações, HQL, JSON. As domain classes de teste ficam em `framework/src/test/grails-app/domain` para a AST valer |
| `framework/src/test/.../QueryTest` | `where { }` (operadores, locais, associações, encadeamento, whereAny, find/findAll), deleteAll/updateAll, createCriteria (junctions, associações, projeções), erros de compilação da AST, typeMismatch |
| `framework/src/test/.../AssetTest` | Diretivas require/require_tree/require_self, ordem e deduplicação, `url()` no CSS, erros, compilador (digest e manifest), reload em desenvolvimento |
| `framework/src/test/.../PromisesTest` | API `grails.async` (task, tasks, then, waitAll, erros) e propagação da autenticação |
| `framework/src/test/.../SecurityTest` | Expressões de acesso, padrões Ant, bcrypt e prefixos, JWT (assinatura, expiração, adulteração) |
| `framework/src/test/.../TenancyTest` | Discriminador com H2: preenchimento, filtros (list/get/count/HQL/finders), withId/withoutId, ausência de tenant, binding protegido |
| `framework/src/test/.../ReloadTest` | Recarga: camadas pela anotação e fixação, diferenças por bytecode, classloaders por geração (bytecode do retrato), entrada do Netty (espera, contagem, 503) |
| `framework/src/test/.../ArquiteturaTest` | Fronteiras dos módulos planejados ([modularizacao.md](modularizacao.md)): dependência nova entre módulos falha, e a lista de violações conhecidas só encolhe |
| `framework/src/test/.../RedisSessionTest` | Repositório Redis real (sobe `redis-server` em porta livre; pulado se não instalado) |
| `sample-app/src/test/.../SegurancaTest` | Form login, URL salva, session fixation, mensagens, CSRF, roles, `@Secured`, 403, logout, Basic, JWT, multi-tenancy ponta a ponta |
| `sample-app/src/test/.../RecursosTest` | paginate/sortableColumn, busca com where, estatísticas com createCriteria, upload multipart (inclusive o tipo inválido não ser gravado), typeMismatch em formulário e JSON, interceptors |
| `sample-app/src/test/.../OperacaoTest` | health, info, metrics/prometheus (acesso por role), JWT refresh e logout com revogação, uso das GSPs pré-compiladas |
| `sample-app/src/test/.../DesligamentoTest` | Desligamento gracioso com requisição em andamento (socket cru) |
| `sample-app/src/test/.../ConsoleTest` | Console web: acesso, CSRF, bindings, tenant, rollback, erros |
| `sample-app/src/test/.../AssetsTest` | Assets pré-compilados: tags com digest, bundle, `url()`, cache immutable, ETag/304 |
| `sample-app/src/test/.../AsyncTest` | Actions assíncronas: ator liberado (1 ator, 5 requisições), view com layout e interceptors, tasks com tenant, erro 500 e timeout 503 |
| `sample-app/src/test/.../RecargaTest` | `DevReloader` real sobre cópias: recarga leve (service, action nova) mantendo sessão e Hibernate, GSP e assets depois dela, completa (domain class), falha com 503 e recuperação |
| `sample-app/src/test/.../RedisSessionAppTest` | Sessão no Redis sobrevive ao reinício da aplicação |
| `sample-app/src/test/.../SampleAppTest` | HTTP de ponta a ponta: REST e CRUD HTML com GORM, validação em HTML e JSON, `_method`, flash, sessão, i18n, estáticos, erros, 200 inserções concorrentes, lazy loading na view |

**Toda feature nova deve vir com teste.** Coloque no framework o que der para testar isolado, e no `SampleAppTest` o que depende do HTTP ou da integração, com um exemplo no `sample-app`.

## Documentação

**Toda funcionalidade nova e toda mudança de comportamento vêm documentadas no mesmo commit.** São dois públicos:

- **Quem usa o Grox:** o livro em `docs/livro/` (mdBook). O apêndice [Manter este livro](livro/src/apendices/manutencao.md) diz quais capítulos atualizar em cada tipo de mudança (recurso, chave de configuração, compatibilidade, comportamento, plugin Gradle, `grox-dsl`). Gere com `docs/livro/build.sh`, que também falha com link interno quebrado.
- **Quem evolui o framework:** [guia](guia.md) e [compatibilidade](compatibilidade.md) (fonte de referência rápida), [arquitetura](arquitetura.md) (decisões e fluxo), este documento (receitas e armadilhas) e o [roadmap](roadmap.md) (marque o item da checklist).

Refatorações e otimizações que não mudam nada para quem usa dispensam o livro.

## Receitas

### Nova tag `g:`
1. Adicione uma propriedade closure `def minhaTag = { attrs, body -> out << ... }` ou um método `def minhaTag(Map attrs, Closure body) { ... }` na TagLib nativa adequada, em `org.grox.view.taglib` (ou na TagLib do módulo dono: as tags `sec:` no plugin spring-security, as `asset:` no asset-pipeline, as de validação em `org.grox.web.taglib`; uma TagLib nova de módulo entra em `tagLibClasses` do módulo, que implementa o trait `GspContributor`). Se ela devolve um valor em vez de escrever, inclua o nome em `static returnObjectForTags`.
2. Codifique a saída com `Html.escape(...)` (`org.grox.http.Html`) e os atributos com `Html.attributes(map)`.
3. Adicione um teste em `GspTest` (ou use no `sample-app`) e atualize a tabela de tags no [guia](guia.md) e a [compatibilidade](compatibilidade.md).

Se for uma tag de **sintaxe** (estrutura de controle), ela fica em `GspCompiler.SYNTAX_TAGS` e em `syntaxTag()`.

### Novo método na API dos controllers
1. Declare o método em `org.grox.web.api.ControllerApi` e delegue para `ControllerSupport` (lógica com `WebRequest`, testável).
2. Obtenha a requisição com `RequestContext.current()` (no core, sem o módulo Grails: `CurrentRequest.orNull()`).
3. Cuidado com sobrecargas com `Map`: argumentos nomeados chegam **primeiro** (`respond x, status: 1` vira `respond([status: 1], x)`). Prefira um único método que decida pelo tipo, como `respond(Object, Object)`.

### Novo tipo de artefato
1. Registre o tipo em `artefactTypes` do módulo dono (`ArtefactType.suffix('Job')`, ou `ArtefactType.assignableTo(...)` para um marcador) e leia as classes com `runtime.application.getArtefacts('Job')`.
2. Se ele precisar de API injetada, crie um trait. Nos módulos do Grails, trate o diretório em `ArtefactTransformation.visit()`; num plugin, implemente `org.grox.ast.ArtefactTrait` (diretório, sufixo e trait) e registre em `META-INF/services/org.grox.ast.ArtefactTrait`, como o `JobArtefactTrait` do quartz.
3. Instancie e registre no módulo dono (`GrailsModule`, `GormModule`...) na fase adequada: veja a seção "Novo módulo".

### Novo módulo
1. Estenda `org.grox.core.GroxModule` no pacote do módulo e defina `order` (http 50, dados 100, segurança 200, views 300, assets 350, Grails 500, plugins 600+).
2. Sobrescreva só as fases que usa (`artefactTypes` para reconhecer um tipo novo de artefato nas classes da app, com `ArtefactType.suffix('Job')` ou `ArtefactType.assignableTo(...)`; `healthChecks` para o `/health`; `exceptionStatuses` para exceções que viram um status, como 403; `prepare`, `initialize`, `contributeBeans`, `beansReady`, `configureWeb`, `started`, `stop`, `release`...). Outros módulos ficam em `runtime.module(Tipo)`, e o mesmo módulo da geração anterior (recarga) em `previous(Tipo)`.
3. Registre a classe no `src/main/resources/META-INF/services/org.grox.core.GroxModule` do subprojeto (os registros de cada jar se somam). Se o módulo tem um `ScopedValue` que deve acompanhar services-ator ou tarefas assíncronas, registre também um `org.grox.core.ContextProvider`; para comportamento em volta dos métodos dos services (como a transação), um `org.grox.service.ServiceInterceptor`.
4. O `ArquiteturaTest` precisa continuar verde: um módulo só usa os módulos de que depende (ver [modularizacao.md](modularizacao.md)).

### Nova constraint
Implemente em `Validator.check()`, registre o código em `Validator.CODES` e adicione a mensagem padrão em `framework/grox-core/src/main/resources/org/grox/i18n/messages*.properties` (en, en explícito e pt). Teste em `GormTest`.

### Novo método do GORM
- **Estático:** declare no trait `GormEntity` e delegue para `GormStaticApi` (via `Gorm.api(this)`).
- **Instância:** delegue para `GormInstanceApi`.

Use `Persistence.withSession` ou `withTransaction`, nunca abra uma sessão diretamente. Nova forma de consulta com Criteria: reaproveite `GormStaticApi.query(params, count, unique) { cb, root -> predicate }`.

### Novo tipo de associação ou de propriedade
- Classificação: `DomainClass.resolveProperties()` e `DomainProperty.Kind`.
- XML: `OrmXml`, respeitando a ordem de elementos do XSD (id, basic, version, many-to-one, one-to-many, ..., transient).
- JSON: `DomainJson.entity()`; binding: `org.grox.data.binding.DataBinder`.

### Novo operador em where { }
Mapeie o token em `WhereQueryTransformation.OPERATORS` (`org.codehaus.groovy.syntax.Types`) para um método de `Where` e implemente o caso em `Where.Restriction.toPredicate`. Teste em `QueryTest`, incluindo o erro de compilação quando o uso for inválido.

### Nova restrição ou projeção em createCriteria
Adicione o método em `CriteriaBuilder.Delegate` (ou `ProjectionDelegate`) e, se necessário, o caso em `QueryRunner.projected`.

### Novo store de sessão
Implemente `org.grox.session.SessionRepository`: `find`, `create`, `save` (use `changed`, `removed` e `accessed` do `GroxSession`), `delete`, `changeId`. Registre-o em `GrailsModule.createSessionRepository()`.

### Novo mecanismo de autenticação ou expressão de acesso
- **Autenticação:** fica em `SecurityFilter.process()` (etapa 1). Crie uma `Authentication` com `method` próprio; se for sem estado, inclua o método em `Authentication.isStateless()`.
- **Expressões:** ficam em `AccessExpression.evaluate()`. Adicione casos em `SecurityTest`.

### Novo TenantResolver
Implemente `org.grails.datastore.mapping.multitenancy.TenantResolver` e configure `grails.gorm.multiTenancy.tenantResolverClass`. O resolver é instanciado com construtor sem argumentos e recebe injeção por nome. Ele roda **a cada abertura de sessão Hibernate**, então precisa ser barato.

### Nova chave de configuração
Leia com `config.getProperty('grox.x.y', Tipo, padrao)` no módulo dono (`GroxModule.config`) e documente na tabela do [guia](guia.md#configuração-applicationyml).

### Novo formato de resposta (ex. XML)
Mapeie o MIME em `Formats.MIME_TO_FORMAT` e `FORMAT_TO_MIME`, e trate o formato em `ControllerSupport.respond()`.

## Desempenho

- **Meça antes de otimizar:** use JFR (receita em [benchmark.md](benchmark.md)) e o `bench/run.sh`. Para pegar regressões, a rodada completa em A/B (`bench/rodada.sh`) vai para o [histórico](benchmark-historico.md).
- **Exceções no caminho de cada requisição** são o primeiro suspeito. `jfr summary` mostra `jdk.JavaExceptionThrow`, e o esperado é algo perto de zero, fora o `ResourceLeakDetector` do Netty.
- **`@CompileStatic`:** use em classes do framework sem dinamismo (utilitários, roteamento, registro de tags). Não use em traits injetados nem onde se usa `methodMissing`/`propertyMissing`.
- **Não crie estruturas por requisição que podem ser cacheadas por classe ou por expressão:** metaClass.properties, loggers, parsing.
- **Métodos chamados de closures** em classes que podem ser estendidas não devem ser `private`.

## Convenções de código

- Groovy dinâmico por padrão. Código quente pode usar `@CompileStatic` quando fizer diferença.
- Comentários e mensagens de erro em português. Identificadores do framework em inglês; o `sample-app` usa português.
- Classes de compatibilidade só em `grails.*`, com o **mesmo nome e pacote** do Grails. Todo o resto fica em `org.grox.*`.
- Estado de requisição e de renderização vai em `ScopedValue` (`RequestContext`, `GspRenderContext`), nunca em `ThreadLocal` ou campo estático.
- Nada de bloqueio no event loop do Netty. IO vai para ator ou virtual thread.

## Armadilhas do Groovy 6 (já encontradas)

| Sintoma | Causa e solução |
|---|---|
| `call(...)` dentro de closure chama o próprio `Closure.call` | Não nomeie métodos auxiliares como `call` (virou `callTag`) |
| Propriedade `x` retorna Boolean em vez de String | `isX()` e `getX()` na mesma classe disputam a propriedade. Use nomes distintos |
| Bloco `{ ... }` solto no código gerado não executa | Em Groovy isso é uma closure. Use `if (true) { }` ou declare as variáveis fora |
| Parâmetro com o mesmo nome em closures aninhadas | "current scope already contains a variable". O código gerado usa nomes únicos (`__body<N>`) |
| `super(Outer.this.x)` em classe interna | Use classe aninhada `static` recebendo o objeto externo por parâmetro |
| `respond(Map, Object)` e `respond(Object, Map)` ambíguos com dois mapas | Use um único método com `(Object, Object)` |
| Aspas simples dentro de `'...'` | O parser acusa "Missing ')'" em outra linha. Use `'''...'''` |
| `switch` com `->` como última instrução | Use `return switch (...) {}` explicitamente |
| Jars compilados com Groovy ≤ 5 (Grails, GORM) | `NoClassDefFoundError: org/codehaus/groovy/runtime/callsite/CallSite`. O call site caching foi removido no Groovy 6 |
| `groovy.typecheckers.SqlChecker` | No 6.0.0 se chama `SqlInjectionChecker` |
| Coerção `obj as Classe` para POGO | Não chama o construtor. `as JSON` funciona pelo extension module `JsonConversions` |
| `switch (classe) { case Date -> }` | `case Classe` testa **instância** (`isCase`), não igualdade de `Class`. Use `if (t == Date)` |
| `mapa[chave] ?: padrao` com valor `0` | Pela Groovy truth, 0 é falso e vira o padrão. Use `containsKey` (bug encontrado na aridade dos finders) |
| Variável chamada `def` | É palavra reservada: "Unexpected input" |
| `@DelegatesTo(value=Scope<A>)` | "Cannot select from a parameterized type": use `value=Scope` (encontrado numa biblioteca do app C) |
| Classe interna anônima num trait | "Cannot have non-static inner class inside a trait": use uma classe própria (`TriggerCollector`) |
| `owner` dentro de closure numa classe com campo `owner` | É o dono da closure, não o campo: capture `this.owner` antes (`ManyToOne.isBidirectional`) |
| Pontos ou `/` no nome de um teste (`void 'org.grox.lib ...'()`) | "Illegal method name" na JVM: troque por `_` ou reescreva |
| Método estático de trait | `this` é a **classe** que implementa o trait, o que permite `Gorm.api(this)`. `$static_methodMissing` também funciona em traits |
| `call(...)` dentro de closure (de novo!) | Aconteceu também em `Tenants`. Métodos auxiliares estáticos usados dentro de closures não podem se chamar `call` |
| `x ?: true` com `x == false` | Vira `true`. Use `x == null ? padrao : x` |
| `case 'a' -> { return v }` em `switch` com seta | Erro "switch expression does not support return". Use `if` |
| Testes que sobem a aplicação | O GORM, o tenancy e o datastore são estáticos. No `sample-app`, `forkEvery = 1` isola cada classe de teste numa JVM |
| `NotExpression` tratado como `BooleanExpression` na AST | `NotExpression` **é subclasse** de `BooleanExpression`: teste-o antes, senão o `!` é descartado |
| Bloco com várias instruções em `case ... -> { }` de switch-expressão | Termine com `yield valor` |
| `out << form(attrs, body)` dentro de uma tag | A tag `form` já escreve em `out`, e o retorno (o Writer) seria escrito de novo. Chame só `form(attrs, body)` |
| Entidade inválida gravada mesmo com `save()` devolvendo null | Auto-flush da consulta de `unique` durante o `validate()` e o commit da transação. Solução: flush MANUAL na consulta e `setReadOnly` na falha |
| `session.setReadOnly(e, false)` | O Hibernate tira um **novo snapshot**: as alterações feitas enquanto a entidade era somente leitura não geram update. Use detach e merge |
| Método `private` chamado de closure numa classe com subclasses | Funciona, mas passa por `MissingMethodException` (e `methodMissing`) a cada chamada. Use `protected` |
| Classe aninhada estática chamando método estático da externa sem qualificar | Idem: exceção interna a cada chamada. Qualifique (`Externa.metodo()`) |
| `alvo.invokeMethod(...)` com `alvo` tipado como `Object` | `IncompatibleClassChangeError` interno do indy. Use `InvokerHelper.invokeMethod(alvo, nome, args)` |
| `valor == false` com valor não booleano | Comparação por reflexão com exceção. Use `valor instanceof Boolean && !valor` |
| `Date.format()` | Está no módulo groovy-dateutil (não incluído). Use `java.time` |
| `HttpClient` do JDK em testes de `Connection: close` | Reenvia o GET numa conexão nova. Use socket cru para testar desligamento |
| Tarefa Gradle com `main.output.dir(builtBy: tarefa)` | A tarefa não pode depender de `classes` (ciclo). Dependa de `compileGroovy` + `processResources` |
| Configuration cache do Gradle | Não capture `Project`/`SourceSet` em closures de tarefa. Use um `CommandLineArgumentProvider` com campos privados |
| Nome de teste com `;`, `/` ou `.` (`void 'a; b'()`) | "Illegal method name" na JVM ao executar a classe. Evite `;`, `.`, `[`, `/` e `<` em nomes de métodos com aspas |
| `onlyIf { project.file(...) }` em tarefa Gradle | Quebra o configuration cache. Calcule o `File` antes e capture só ele |
| Bibliotecas Groovy de terceiros (GORM, asset-pipeline...) | Verifique o Groovy 6 antes de adotar: as compiladas com Groovy ≤ 5 costumam falhar com `NoClassDefFoundError: ...callsite/CallSite` |
| Log do Hibernate em formato JUL | Defina `org.jboss.logging.provider=slf4j` antes de carregar o Hibernate (o `Datastore` já faz isso) |
| `x ?: [:]` troca um mapa vazio por outro objeto | Mapa vazio é falso no Groovy. Era o caso do `params` vazio na GSP, que virava `LinkedHashMap` sem `params.int()`. Use `x != null ? x : padrão` |
| Método da própria classe chamado dentro de um closure com `DELEGATE_FIRST` vai para o `methodMissing` do delegate | Aconteceu no `findControllersInPackage` do UrlMappings, que virava uma rota. O builder recebe o `owner` e delega os métodos que ele declara |
| Sobrecargas `f(String, List)` e `f(String, Object[])` com argumento `null` | "Ambiguous method overloading" em chamada dinâmica. Implemente a API alternativa em outra classe (ex.: `SpringMessageSource`) |
| Variável local com o nome de um método do delegate (`def json = ...` e depois `json(x)` no closure) | A chamada vira `json.call(x)` na variável. Renomeie a variável (ex.: `headers` → `hubHeaders` no piloto) |
| Variável de ambiente homônima de um bloco do YAML (`MAIL`, `PATH`, `HOME`...) | A navegação `config.mail` devolvia a variável. A sobreposição agora vale só para valores-folha |
| `pkill -f padrão` em script de teste | O padrão casa com a linha de comando do próprio shell e o mata. Encerre pelo pid (`setsid` + `kill -- -$pid`) |
| AOT cache / CDS com diretórios no classpath | "Cannot have non-empty directory in paths": só funciona com JARs (por isso a tarefa `aotCache` usa o `installDist`) |
| `update` do Hibernate 7 contra banco existente | Faz `alter table ... modify column` quando tipo ou tamanho diferem (o Hibernate 5 não fazia). O Grox desliga isso por padrão (`SafeUpdateDialects`). Antes de apontar para um banco real, gere o DDL com `grox.schema.updateScript` + `scriptOnly` |
| Resolver de tenant que consulta o banco | Recursão infinita: o Hibernate pede o tenant ao abrir a sessão, e o resolver abre outra sessão. O resolver roda numa sessão isolada, com proteção de reentrância |

## Depuração

- **Mapeamento:** `app.datastore.ormXml` mostra o XML gerado. Se o Hibernate falhar na subida, o XML é logado. `dataSource.logSql: true` mostra o SQL.

- **Logs:** SLF4J. No `sample-app` é o `slf4j-simple`: `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug` mostra as actions registradas e as GSPs compiladas.
- **GSP gerada:** o `CompiledGsp.generatedSource` guarda o código Groovy produzido. Cada linha tem `//@gsp:N` com a linha original.
- **Página de erro:** em desenvolvimento o erro padrão (sem mapeamento `"500"`) inclui o stack trace.
