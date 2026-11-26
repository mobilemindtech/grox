# Beans do framework

Beans que o Grox e os plugins registram. Todos podem ser injetados por nome de propriedade (como no Grails), aparecem em `grailsApplication.mainContext.getBean(...)`, em `Holders.applicationContext` e no `ctx` do console web. Um service ou bean da aplicação com o mesmo nome prevalece sobre o do framework.

## Sempre presentes

| Bean | O que é |
|---|---|
| `groxRuntime` | O `GroxRuntime`: módulos (`runtime.module(GormModule)`), configuração, tempos de subida (`startupPhases`) |
| `groxApplication` | A aplicação do core (`config`, `environmentName`, artefatos por tipo), sem a fachada do Grails |
| `messageSource` | API do Spring: `getMessage(code, args, default, locale)`. Mensagem inexistente com `default` `null` devolve `null`, como no Spring |
| `<nome>Service` | Cada service da aplicação, em camelCase (`UsuarioService` → `usuarioService`) |

## Módulo Grails (`grox-grails`)

| Bean | O que é |
|---|---|
| `grailsApplication` | `GrailsApplication`: `config`, `mainContext`, `controllerClasses`, `domainClasses`, `serviceClasses`... |
| `groovyPageRenderer` | `grails.gsp.PageRenderer`: `render(template:/view:, model:)` para `String`, dentro ou fora de requisições |
| `pluginManager` | `GrailsPluginManager` com os descritores `*GrailsPlugin` das bibliotecas (`allPlugins`, `hasGrailsPlugin`) |
| `localeResolver` | `SessionLocaleResolver` (`resolveLocale(request)`), se a app não declarar outro |

## GORM (`grox-gorm`), quando há domain classes ou `dataSource`

| Bean | O que é |
|---|---|
| `dataSource` | O pool JDBC (HikariCP), para `groovy.sql.Sql` |
| `sessionFactory` | `currentSession` é a sessão corrente; também por `getBean(org.hibernate.SessionFactory)` |
| `hibernateDatastore` | Implementa o `HibernateDatastore` do GORM. `mappingContext.getPersistentEntity(nome)` dá o modelo de mapeamento, somente leitura. Usado como `ref('hibernateDatastore')` por listeners `AbstractPersistenceEventListener` |
| `transactionManager` | `PlatformTransactionManager` do Spring sobre a sessão corrente (REQUIRED, SUPPORTS, MANDATORY) |
| `<nome>Service` | Cada GORM Data Service (`@Service(Dominio) interface`) |

## Segurança (plugin spring-security, módulo `grox-grails-plugins`)

Com `grails.plugin.springsecurity` configurado e o `grox-grails-plugins` no classpath. Só com o `grox-security` (sem esse módulo) a segurança funciona, mas estes beans não existem: uma propriedade `springSecurityService` fica `null`, como qualquer bean ausente, e a subida avisa no log quais classes a declaram:

| Bean | O que é |
|---|---|
| `springSecurityService` | `currentUser`, `principal`, `loggedIn`, `authentication`, `encodePassword()`, `reauthenticate()` |
| `authenticationTrustResolver` | `isAnonymous(auth)`, `isRememberMe(auth)`, como no Spring Security |
| `jwtService` | JWT, quando o REST está ativo e os tokens não estão no GORM |

Beans que **a aplicação** pode declarar e o Grox usa:

| Bean | Efeito |
|---|---|
| `userDetailsService` | Fonte de usuários própria (`org.grox.security.UserDetailsService` ou o estilo Spring: `UserDetailsService`, `GrailsUserDetailsService`) |
| `passwordEncoder` | `PasswordEncoder` do Spring Security no lugar de `password.algorithm` |
| `userDetailsPasswordService` | Atualiza o hash no login quando o encoder pede `upgradeEncoding` |
| `sessionRegistry` (`SessionRegistryImpl`) | Lê as sessões autenticadas ativas (memória ou Redis) |
| `ApplicationListener<RestTokenCreationEvent>` | Recebe o evento de cada token emitido em `/api/login` |

## Assets e plugins

| Bean | Quando | O que é |
|---|---|---|
| `assetResourceLocator` | Plugin asset-pipeline | `findAssetForURI('logo.png')?.inputStream` |
| `grailsResourceLocator` | Plugin asset-pipeline | `Resource` de arquivos da app (sem `..`) |
| `consoleService` | Plugin console | `eval(codigo)`. Existe mesmo com o console desligado |
| `redisService`, `redisService<Nome>`, `redisPool` | `jedis` no classpath | API do plugin redis |
| `rabbitMessagePublisher` | `rabbitmq.connections` e `amqp-client` | `send(...)` |
| `pdfRenderingService`, `xhtmlDocumentService` | `flying-saucer-pdf` no classpath | PDF a partir de GSP em XHTML |
| `auditRequestResolver` | `grails.plugin.auditLog.auditDomainClassName` | Autor e URI da auditoria; uma subclasse no `resources.groovy` substitui |

## Declarando beans

Na app Grails, em `grails-app/conf/spring/resources.groovy`, no `doWithSpring()` da `Application` ou dos descritores `*GrailsPlugin`:

```groovy
beans = {
    userPasswordEncoderListener(UserPasswordEncoderListener)
    cliente(ApiCliente, 'https://api.exemplo.com') { timeout = 30; mailService = ref('mailService') }
}
```

Nas apps do `grox-dsl`, no bloco `beans { }` da DSL de projeto, que também aceita `nome = instancia`. Veja [Beans e resources.groovy](../recursos/beans.md).

## Veja também

- [Beans e resources.groovy](../recursos/beans.md)
- [Services e injeção de dependências](../recursos/services.md)
- [Plugins reimplementados](../recursos/plugins.md)
