# Projeto do zero

Este capítulo monta uma aplicação sem `grails-app/`, do `settings.gradle` à primeira requisição: build, pastas, configuração e a classe de entrada. O resultado é a base do [tutorial](tutorial.md) e tem a mesma forma do `sample-plain/` do repositório.

## Requisitos

- JDK 27, que o Gradle baixa pelo toolchain (plugin `foojay-resolver`).
- Groovy 6.0.0, que vem como dependência do framework.
- Gradle 9 (use o wrapper do repositório).

## `settings.gradle`

Dentro do repositório do Grox, a app é mais um subprojeto e depende dos módulos por `project(...)`:

```groovy
plugins {
    // baixa o JDK do toolchain automaticamente
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

rootProject.name = 'grox'
include('framework:grox-core', 'framework:grox-gorm', 'framework:grox-gsp',
        'framework:grox-security', 'framework:grox-dsl', 'livraria')
```

Fora do repositório, troque `project(':framework:grox-...')` pela coordenada publicada de cada módulo.

## `build.gradle`

```groovy
plugins {
    id 'groovy'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation project(':framework:grox-dsl')        // traz core, gorm, gsp e security
    // implementation project(':framework:grox-security-gorm')   // usuários em domain classes
    // implementation project(':framework:grox-assets')          // /assets/**
    runtimeOnly libs.slf4j.simple                         // ou outro backend do SLF4J
    runtimeOnly libs.h2                                   // o driver JDBC do seu banco

    testImplementation libs.groovy.test.junit6
    testImplementation libs.groovy.http.builder     // cliente HTTP dos testes (groovy.http.HttpBuilder)
    testImplementation libs.junit.jupiter
    testRuntimeOnly libs.junit.launcher
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(27)
    }
}

// os handlers por anotação recebem os parâmetros pelo nome
tasks.withType(GroovyCompile).configureEach {
    groovyOptions.parameters = true
}

application {
    mainClass = 'livraria.Aplicacao'
}

// gradle run sobe em development (sem isso, o padrão é production)
tasks.named('run') {
    systemProperty 'grox.env.default', 'development'
}

tasks.named('test') {
    useJUnitPlatform()
    // cada classe de teste sobe a própria aplicação: o GORM é global na JVM
    forkEvery = 1
}
```

Pontos importantes:

- **`groovyOptions.parameters = true`** é obrigatório para rotas por anotação com parâmetros (`def show(Long id)`). Sem ele, a primeira requisição falha com "compile com os nomes dos parâmetros".
- **`grox.env.default`**: o ambiente padrão do Grox é `production`, como no Grails. Na tarefa `run`, defina `development`; nos testes, use `grails.env=test` (ver [Testes](testes.md)).
- **`forkEvery = 1`**: o GORM, o tenancy e o datastore são estáticos na JVM. Duas aplicações na mesma JVM de teste disputam esse estado.

O plugin Gradle `org.grox.app` é do layout `grails-app/` e não se aplica aqui. Um plugin próprio para apps sem `grails-app/` (`org.grox.plain`, com `grox { modules 'gorm', 'gsp', 'security' }`) está no roadmap; por enquanto o build é escrito à mão, como acima. Sem o plugin também não há pré-compilação de GSPs (`compileGsp`) nem `assetCompile`: as GSPs são compiladas na primeira vez que são usadas.

## Pastas

```
livraria/
  build.gradle
  src/main/groovy/livraria/
    Aplicacao.groovy            classe de entrada (e DSL de projeto, opcional)
    Livro.groovy                domain class (@Entity)
    LivroService.groovy         service (*Service)
    Rotas.groovy                rotas por closure (implements Routes)
    LivrosApi.groovy            rotas por anotação (@Handler)
  src/main/resources/
    application.yml             configuração
    application.groovy          configuração em Groovy (opcional, mesclada ao yml)
    messages.properties         mensagens (i18n), também messages_en.properties...
    views/                      GSPs: livros/index.gsp, layouts/main.gsp, login/auth.gsp
    public/                     estáticos, servidos em /static/**
  src/test/groovy/livraria/     testes de integração
```

| Pasta | No classpath | Uso |
|---|---|---|
| `src/main/resources/application.yml` | `application.yml` | Configuração, com `environments:` e documentos `---` |
| `src/main/resources/application.groovy`, `runtime.groovy` | idem | Configuração no formato do `ConfigSlurper` |
| `src/main/resources/views/` | `views/` | Views GSP e layouts |
| `src/main/resources/public/` | `public/` | Estáticos em `/static/**` |
| `src/main/resources/messages*.properties` | raiz | Mensagens, em UTF-8 |

## A classe de entrada

```groovy
package livraria

import org.grox.GroxApp

class Aplicacao {
    static void main(String[] args) {
        GroxApp.run(Aplicacao, args)
    }
}
```

O pacote da classe passada a `GroxApp.run` (e os subpacotes) é varrido na subida. O que é reconhecido:

| Classe | Reconhecida por |
|---|---|
| Domain class | `@grails.gorm.annotation.Entity` (ou a interface `GormEntity`) |
| Service | sufixo `Service` |
| Rotas por closure | implementa `org.grox.dsl.Routes` |
| Rotas por anotação | `@org.grox.dsl.Handler` |

A mesma classe pode declarar a aplicação inteira num closure estático `app` (configuração, segurança, rotas e beans): veja [DSL de projeto](dsl-projeto.md).

## `application.yml` mínimo

```yaml
server:
  port: 8080

grox:
  i18n:
    defaultLocale: pt-BR
  routes:
    execution: virtual      # virtual (padrão) ou actor

dataSource:
  driverClassName: org.h2.Driver
  username: sa
  password: ''
  dbCreate: create-drop

---
environments:
  development:
    grox:
      gsp:
        viewsDir: src/main/resources/views     # views recarregadas ao salvar
    dataSource:
      url: jdbc:h2:mem:livrariaDev;DB_CLOSE_DELAY=-1
  test:
    server:
      port: 0                                   # porta aleatória nos testes
    dataSource:
      url: jdbc:h2:mem:livrariaTest;DB_CLOSE_DELAY=-1
```

O reload de views em development lê o diretório de `grox.gsp.viewsDir`, cujo padrão é `grails-app/views`. Numa app sem `grails-app/`, aponte para `src/main/resources/views`, como acima; senão as views vêm do classpath e só mudam depois de um novo build. A mesma regra vale para os estáticos (`grox.static.dir`, padrão `src/main/resources/public`, que já é o diretório certo).

## Primeira rota

```groovy
package livraria

import org.grox.dsl.Routes

class Rotas implements Routes {
    Closure routes = {
        get('/') { render text: 'Olá, Grox' }
    }
}
```

```bash
./gradlew :livraria:run
curl http://localhost:8080/          # Olá, Grox
curl http://localhost:8080/actuator/health
```

O log da subida mostra o tempo de cada fase (`Subida por fase`) e quantas rotas foram registradas.

## Veja também

- [Rotas por closure](rotas-closure.md)
- [DSL de projeto](dsl-projeto.md)
- [Configuração](../referencia/configuracao.md)
- [Ambientes](../referencia/ambientes.md)
- [Tutorial: uma livraria completa](tutorial.md)
