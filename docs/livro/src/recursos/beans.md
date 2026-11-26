# Beans e resources.groovy

Este capítulo cobre os beans que não são services: o `resources.groovy`, o `doWithSpring()` da classe `Application`, os descritores de plugin `*GrailsPlugin`, `@PostConstruct`, eventos da aplicação (`ApplicationListener`, `publishEvent`) e a lista de beans que o próprio framework oferece. Não há Spring: o Grox interpreta o subconjunto do DSL de beans que os projetos usam e injeta tudo **por nome**.

## `resources.groovy`

```groovy
// grails-app/conf/spring/resources.groovy
import app.ApiCliente
import app.UserPasswordEncoderListener

beans = {
    userPasswordEncoderListener(UserPasswordEncoderListener)
    cliente(ApiCliente, 'https://api.exemplo.com') {        // argumentos do construtor
        timeout = 30
        mailService = ref('mailService')                    // outro bean
    }
    relogio = java.time.Clock.systemUTC()                    // instância pronta (extensão Grox)
}
```

| Forma | Efeito |
|---|---|
| `nome(Classe)` | Instancia com o construtor sem argumentos |
| `nome(Classe, arg1, arg2)` | Argumentos do construtor (podem ser `ref('x')`) |
| `nome(Classe) { prop = valor }` | Propriedades |
| `ref('outro')` | Outro bean. Numa propriedade, pode apontar para um bean declarado depois (resolvido no fim do bloco); no construtor, o bean referenciado precisa vir antes |
| `{ bean -> bean.autowire = true; bean.scope = 'singleton' }` | Aceito e ignorado: o Grox sempre injeta por nome, e tudo é singleton |
| `nome = instancia` *(extensão Grox)* | Registra o objeto pronto. O BeanBuilder do Spring não tem |

Outros recursos do DSL do Spring (namespaces XML, `bean.initMethod`, fábricas, aliases, perfis) não são suportados.

## Onde declarar beans

A ordem de registro é:

1. os descritores de plugin (`class XGrailsPlugin extends grails.plugins.Plugin`) das bibliotecas `org.grox.lib`, pelo `doWithSpring()`;
2. o `doWithSpring()` da classe `Application` (que estende `GrailsAutoConfiguration`);
3. o `resources.groovy`;
4. nas apps do `grox-dsl`, o bloco `beans { }` da [DSL de projeto](../sem-grails/dsl-projeto.md).

```groovy
class Application extends GrailsAutoConfiguration {
    Closure doWithSpring() {{ ->
        cacheManager(MeuCacheManager)
    }}
    static void main(String[] args) { GroxApp.run(Application, args) }
}
```

Um service da aplicação prevalece sobre um bean de módulo com o mesmo nome.

### Descritores de plugin

```groovy
class RelatoriosGrailsPlugin extends grails.plugins.Plugin {
    Closure doWithSpring() {{ -> geradorRelatorio(GeradorRelatorio) }}
    void doWithDynamicMethods() { ... }
    void doWithApplicationContext() { ... }      // contexto pronto
    void onShutdown(Map event) { ... }
}
```

O Grox chama `doWithSpring()`, `doWithDynamicMethods()`, `doWithApplicationContext()` e `onShutdown(event)`. Os metadados do plugin (versão, dependências, `loadAfter`) são ignorados, e `doWithWebDescriptor` não existe. O bean `pluginManager` (`GrailsPluginManager`: `allPlugins`, `hasGrailsPlugin(nome)`) lista os descritores; beans que implementam `PluginManagerAware` o recebem.

## Injeção

Os beans recebem injeção por nome como os services: qualquer propriedade Groovy com o nome de um bean e tipo compatível. E ficam disponíveis para injeção nos demais artefatos e em:

```groovy
Holders.applicationContext.getBean('cliente')
grailsApplication.mainContext.getBean(ApiCliente)
applicationContext.getBean(org.hibernate.SessionFactory)
```

`@Autowired` (do Spring) é aceito como marcador; a injeção continua sendo por nome.

## `@PostConstruct`

```groovy
import jakarta.annotation.PostConstruct

class CacheService {
    def dados
    @PostConstruct
    void carregar() { dados = Config.list() }
}
```

Métodos `@PostConstruct` de services e beans rodam depois que todos estão injetados, antes do BootStrap.

## Eventos da aplicação

```groovy
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener

class PedidoCriado extends ApplicationEvent {
    PedidoCriado(Object pedido) { super(pedido) }
}

class NotificacaoListener implements ApplicationListener<PedidoCriado> {
    void onApplicationEvent(PedidoCriado e) { ... }
}
// resources.groovy: notificacaoListener(NotificacaoListener)

// em qualquer artefato
def applicationContext
applicationContext.publishEvent(new PedidoCriado(pedido))
```

A entrega é síncrona e filtrada pelo tipo genérico do listener. Uma falha num listener vai para o log e não interrompe os demais. O spring-security-rest usa o mesmo mecanismo para o `RestTokenCreationEvent` (ver [REST, JWT e tokens](seguranca-rest.md#evento-de-criação-de-token)).

## Beans do framework

| Bean | O que é |
|---|---|
| `grailsApplication` | A aplicação (`config`, `xxxClasses`, `mainContext`) |
| `groxApplication` | A aplicação do core, sem a fachada do Grails |
| `groxRuntime` | O runtime (módulos, `runtime.module(Tipo)`) |
| `messageSource` | API do Spring: `getMessage(code, args, default, locale)`; mensagem inexistente com `default` `null` devolve `null` |
| `groovyPageRenderer` | `render(template:/view:, model:)` para String, dentro ou fora de requisições |
| `assetResourceLocator` | `findAssetForURI('logo.png')?.inputStream` |
| `grailsResourceLocator` | Recursos pelo caminho do asset (`/images/x.png`); recusa `..` |
| `sessionFactory` | `currentSession` é a sessão corrente |
| `hibernateDatastore` | `HibernateDatastore` do GORM; `mappingContext` dá o modelo de mapeamento (somente leitura) |
| `transactionManager` | `PlatformTransactionManager` do Spring sobre a sessão corrente |
| `dataSource` | O pool JDBC (HikariCP), para `groovy.sql.Sql` |
| `localeResolver` | `SessionLocaleResolver`: `resolveLocale(request)` |
| `springSecurityService` | Com segurança ativa e o módulo `grox-grails-plugins` |
| `jwtService` | Com JWT ativo (também do `grox-grails-plugins`) |
| `authenticationTrustResolver` | Com segurança ativa e o `grox-grails-plugins` |
| `pluginManager` | Descritores `*GrailsPlugin` |
| `redisService`, `rabbitMessagePublisher`, `pdfRenderingService`, `auditRequestResolver`, `consoleService` | Dos plugins, quando ativos (ver [Plugins reimplementados](plugins.md)) |

O `sessionRegistry` (`SessionRegistryImpl`) não é criado pelo framework: a aplicação o declara no `resources.groovy` (`sessionRegistry(SessionRegistryImpl)`), como no spring-security-core.

Lista de referência em [Beans do framework](../referencia/beans.md).

## Diferenças para o Grails

| Recurso | Grox |
|---|---|
| Injeção | Só por nome (sem tipo, qualifiers nem construtor) |
| Escopos (`prototype`, `request`, `session`) | ❌: tudo singleton |
| `autowire`, `scope` no DSL | Aceitos e ignorados |
| `nome = instancia` | *(extensão Grox)* |
| `doWithWebDescriptor`, metadados de plugin | ❌ / ignorados |
| Listeners assíncronos | ❌ (entrega síncrona) |

## Veja também

- [Services e injeção de dependências](services.md)
- [Estrutura de uma aplicação](estrutura.md)
- [Plugins reimplementados](plugins.md)
- [Beans do framework](../referencia/beans.md)
