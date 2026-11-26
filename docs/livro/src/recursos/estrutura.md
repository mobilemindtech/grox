# Estrutura de uma aplicação

Este capítulo mostra como uma aplicação Grox no layout do Grails é organizada: as pastas de `grails-app/`, o plugin Gradle `org.grox.app`, a classe `Application`, a descoberta dos artefatos e as bibliotecas de artefatos (`org.grox.lib`). O `sample-app/` do repositório é a referência executável de tudo o que está aqui.

## Layout de pastas

```
minha-app/
  build.gradle
  grails-app/
    conf/application.yml        configuração (também application.groovy e runtime.groovy)
    conf/spring/resources.groovy beans
    controllers/<pkg>/          *Controller.groovy, *Interceptor.groovy, UrlMappings.groovy
    services/<pkg>/             *Service.groovy
    domain/<pkg>/               domain classes (GORM)
    taglib/<pkg>/               *TagLib.groovy
    jobs/<pkg>/                 *Job.groovy
    init/<pkg>/                 Application.groovy, BootStrap.groovy
    utils/<pkg>/                classes utilitárias
    views/                      <controller>/<action>.gsp, layouts/, templates _x.gsp
    i18n/                       messages.properties, messages_en.properties...
    assets/                     javascripts/, stylesheets/, images/
  src/main/groovy/              classes comuns
  src/main/resources/public/    arquivos estáticos (servidos em /static/**)
```

O diretório importa: a transformação AST do Grox decide o papel de cada classe pelo **diretório do fonte**, sem herança nem anotações no seu código.

O nome `grails-app/` pode ser trocado por `grox-app/`, detectado sem configuração, ou por outro nome com `grox { appDir = 'app' }` no `build.gradle` *(extensão Grox)*. Os subdiretórios são os mesmos. Uma app migrada do Grails pode continuar com `grails-app/`.

| Diretório | O que a AST aplica |
|---|---|
| `grails-app/controllers/*Controller` | Trait `ControllerApi` (render, redirect, params...) e os nomes dos parâmetros das actions |
| `grails-app/controllers/*Interceptor` | Trait `InterceptorApi` e as constantes de `Ordered` |
| `grails-app/services/*Service` | Trait `ServiceApi` (`log`) |
| `grails-app/taglib/*TagLib` | Trait `TagLibApi` (`out`, `g`, `params`...) |
| `grails-app/domain/*` | Trait `GormEntity`, `id`, `version`, associações, construtores `()` e `(Map)` |
| `grails-app/jobs/*Job` | Trait dos jobs (`log`) |

Fora de `grails-app/` (por exemplo numa app sem o módulo Grails), uma domain class usa `@grails.gorm.annotation.Entity`. Veja [Dados com @Entity](../sem-grails/dados.md).

## Autocomplete no IntelliJ IDEA

O IDEA não roda a AST do Grox, então não enxergaria sozinho o que ela acrescenta. Os jars do framework trazem descritores GDSL (`*.gdsl`), que o IDEA carrega do classpath sem configuração *(extensão Grox)*:

| Classe | O IDEA passa a completar |
|---|---|
| Domain (`grails-app/domain`, `grox-app/domain` ou `@Entity`) | A API do GORM com o tipo da classe (`Livro.get(1)` é um `Livro`, `Livro.list()` um `List<Livro>`, `where { }`), `save`/`delete`/`validate`, `id`, `version`, as coleções do `hasMany` com `addTo*`/`removeFrom*` e os dynamic finders de cada propriedade (`findByTitulo`, `findAllByTituloIlike`, `countByAnoBetween`, `listOrderByAno`...) |
| `*Controller`, `*Interceptor`, `*Service`, `*TagLib`, `init/`, `*Job` | A API da trait de cada um (`params`, `render`, `respond`, `log`...); com `static scaffold = X`, também as actions do `RestfulController` |
| Classes `@Handler` (grox-dsl) | `render`, `respond`, `params`, `request`... |

Os finders combinados (`findByTituloAndAno`) não aparecem na lista, mas funcionam. Com um nome de diretório próprio (`grox { appDir }`) o descritor não reconhece as classes; nesse caso, ou para ter os tipos sem o GDSL (em outro editor ou em código `@CompileStatic`), declare a trait na classe:

```groovy
import org.grox.data.GormEntity

class Livro implements GormEntity<Livro> {      // opcional: a AST aplica a mesma trait
    String titulo
}

class LivroController implements org.grox.web.api.ControllerApi { }
```

A AST não aplica a trait de novo quando a classe já a declara, e o código continua compatível com o Grails, onde o `implements GormEntity<Livro>` também existe (`org.grails.datastore.gorm.GormEntity`; troque o import ao voltar).

## `build.gradle` com o plugin `org.grox.app`

```groovy
plugins { id 'org.grox.app' }
dependencies { implementation project(':framework') }   // ou a coordenada publicada do framework
application { mainClass = 'sample.Application' }
grox {
    appDir = 'grails-app'     // padrão: grox-app se existir, senão grails-app
    precompileGsp = true      // padrão: pré-compila as GSPs no build
    fastDevStartup = true     // padrão: run com JIT só C1
    reload = true             // padrão: recarga de classes no run
}
```

O plugin aplica `groovy` e `application` e:

- mapeia `grails-app/{controllers,services,domain,init,jobs,taglib,utils}` como fontes Groovy;
- mapeia `grails-app/conf` e `grails-app/i18n` como resources, e `grails-app/views` como `views/` no classpath;
- configura o `run` no diretório do projeto (reload de views) e com `development` como ambiente padrão; as tarefas de teste usam `test`;
- liga a [recarga de classes](recarga.md) no `run` e a compilação incremental do Groovy;
- adiciona as tarefas `compileGsp` (pré-compila as views; erro de GSP falha o build com arquivo e linha), `assetCompile` (assets com digest), `groxPackageIndex` (índice de pacotes) e `aotCache` (AOT cache para produção).

Detalhes de cada tarefa e opção em [Plugins e tarefas Gradle](../referencia/gradle.md).

## A classe `Application`

```groovy
// grails-app/init/sample/Application.groovy
package sample
import org.grox.GroxApp

class Application {
    static void main(String[] args) { GroxApp.run(Application, args) }
}
```

`GroxApp.run` devolve o runtime, que você pode fechar com `close()` (útil em testes). Por compatibilidade, `grails.boot.GrailsApp.run(Application, args)` e `Application extends GrailsAutoConfiguration` também funcionam; `doWithSpring()` da `Application` contribui beans (veja [Beans e resources.groovy](beans.md)).

## Descoberta dos artefatos

Os artefatos são procurados:

1. no **pacote da classe `Application`** e nos subpacotes;
2. nos pacotes listados em `META-INF/grox/packages` no classpath.

O índice `META-INF/grox/packages` é gerado pela tarefa `groxPackageIndex` dos plugins `org.grox.app` e `org.grox.lib`, com os pacotes dos fontes de `grails-app/`. Assim, domains e services em outros pacotes (`br.com.x.pay` numa app `br.com.x.app C`) também são encontrados, sem configuração. Classes sem `package` (um `UrlMappings` ou `LoginController` no pacote padrão, comum em apps antigas) entram no índice como `class:` e também são achadas.

A classificação é pelo tipo de artefato, na ordem dos marcadores e depois dos sufixos: `*Controller`, `*Interceptor`, `*Service`, `*TagLib`, `*UrlMappings`, `*BootStrap`, `*Job`, `*GrailsPlugin`, domain classes e Data Services.

## Bibliotecas de artefatos (`org.grox.lib`)

Domains, services, TagLibs e jobs compartilhados por várias aplicações (o papel de um plugin do Grails, ou de um `core` usado por app, API e jobs) ficam num projeto com o plugin `org.grox.lib`, no mesmo layout `grails-app/` e sem `Application`:

```groovy
// core/build.gradle
plugins { id 'org.grox.lib' }
dependencies { api project(':framework') }

// app/build.gradle
dependencies { implementation project(':core') }
```

- O plugin aplica `groovy` e `java-library`, mapeia `grails-app/` como na aplicação e gera o índice de pacotes. A AST aplica a API de cada artefato pelo diretório, como na app.
- A aplicação descobre os artefatos da biblioteca pelo índice.
- As `messages*.properties` da biblioteca são mescladas com as da aplicação; a aplicação prevalece.
- Descritores `*GrailsPlugin` (`doWithSpring`, `doWithApplicationContext`, `onShutdown`) da biblioteca são executados.
- Views, assets, `application.yml` e a recarga de classes continuam só na aplicação: as classes da biblioteca não são recarregadas no `run`.

O `sample-lib/` do repositório é um exemplo, usado pelo `sample-app/`.

## BootStrap

```groovy
// grails-app/init/sample/BootStrap.groovy
class BootStrap {
    UsuarioService usuarioService            // injeção por nome

    def init = { servletContext ->           // servletContext é sempre null
        environments {
            development { usuarioService.salvar(nome: 'Ana') }
            production { log.info 'subiu' }
        }
    }
    def destroy = { }
}
```

- `init` roda depois que tudo está pronto (beans, Hibernate), **numa sessão de dados**; os jobs só começam depois dele.
- `environments { }` executa o bloco do ambiente atual, como no Grails.
- `log` está disponível.
- Não há usuário logado: para dados multi-tenant, use `Tenants.withId('acme') { ... }`.
- `destroy` roda no desligamento (e na recarga completa).

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| `grails` CLI, profiles, scaffolding | Não há. O plugin Gradle cobre layout e build |
| Plugins do Grails | Bibliotecas `org.grox.lib` e plugins reimplementados em `org.grox.plugins.*` |
| `servletContext` no BootStrap | Sempre `null` (não há container de Servlet) |
| Artefatos achados pelo Spring | Pacote da `Application` mais o índice `META-INF/grox/packages` |

## Veja também

- [Plugins e tarefas Gradle](../referencia/gradle.md)
- [Configuração](../referencia/configuracao.md)
- [Beans e resources.groovy](beans.md)
- [Recarga em desenvolvimento](recarga.md)
- [Plugins do Grails](../migracao/plugins.md)
