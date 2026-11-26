# Plugins e tarefas Gradle

O Grox traz dois plugins Gradle para o layout `grails-app/`: `org.grox.app`, para aplicações, e `org.grox.lib`, para bibliotecas de artefatos compartilhados (o papel de um plugin do Grails). Apps sem `grails-app/` (só `grox-dsl`) ainda configuram o build à mão.

## Usando os plugins

No repositório do Grox, o `gradle-plugin/` é um build incluído:

```groovy
// settings.gradle
pluginManagement {
    includeBuild('gradle-plugin')
}
```

A dependência do framework continua no build de cada projeto: os plugins só cuidam do layout e das tarefas.

```groovy
// app/build.gradle
plugins { id 'org.grox.app' }
dependencies {
    implementation project(':framework')     // ou a coordenada publicada do framework
    runtimeOnly 'com.h2database:h2:2.5.252'
}
application { mainClass = 'minhaapp.Application' }
grox {
    appDir = 'grails-app'     // padrão: grox-app se existir, senão grails-app
    precompileGsp = true      // padrão
    fastDevStartup = true     // padrão
    reload = true             // padrão
}
```

## `org.grox.app`

Aplica os plugins `groovy` e `application` e:

| O quê | Detalhe |
|---|---|
| Fontes | `grails-app/{controllers,services,domain,init,jobs,taglib,utils}` (ou o `appDir`) e `src/main/groovy` |
| Resources | `grails-app/conf` e `grails-app/i18n` no classpath; `grails-app/views` em `views/` no classpath |
| Compilação incremental | `options.incremental` e `incrementalAfterFailure` em todas as `GroovyCompile` (a recarga depende disso) |
| Índice de pacotes | Tarefa `groxPackageIndex` grava `META-INF/grox/packages` |
| `run` | Roda no diretório do projeto (views com reload), com `-Dgrox.env.default=development`, JIT só C1 e recarga de classes |
| `test` | Todas as tarefas `Test` com `-Dgrox.env.default=test` |
| `startScripts` | O script `bin/<app>` usa `lib/grox.aot` quando existe |
| Tarefas novas | `compileGsp`, `assetCompile`, `groxReloadStamp`, `aotCache` |

### Extensão `grox { }`

| Propriedade | Padrão | Efeito |
|---|---|---|
| `appDir` | `grox-app` se existir, senão `grails-app` | Diretório do layout. Com outro nome, o plugin passa `-Dgrox.appDir` à compilação (a AST reconhece os artefatos por ele), ao `run` e aos testes. Também no `org.grox.lib` *(extensão Grox)* |
| `precompileGsp` | `true` | Pré-compila as GSPs no build. Um erro de GSP falha o build com arquivo e linha |
| `fastDevStartup` | `true` | `run` com `-XX:TieredStopAtLevel=1` (JIT só C1): sobe de 25% a 40% mais rápido. Desligue para medir desempenho |
| `reload` | `true` | `run` com recarga de classes em development (passa as propriedades `grox.reload.*` à app) |
| `source(dir) { include/exclude }` | nenhuma | Raiz de código fora do projeto, com o layout do Grails (`grails-app/` e `src/main/`); padrões relativos à raiz. Um arquivo de mesmo caminho numa raiz posterior (que os padrões dela aceitem) ou no projeto substitui o da raiz. Também no `org.grox.lib` *(extensão Grox)*. Ver [Build paralelo](../migracao/build-paralelo.md) |
| `patches` | vazio | Diffs unificados (arquivos ou diretórios de `*.patch`, caminhos relativos à raiz) aplicados a cópias em `build/grox-patched`. Também no `org.grox.lib` *(extensão Grox)* |

### Tarefas

| Tarefa | Grupo | O que faz |
|---|---|---|
| `compileGsp` | build | Pré-compila `grails-app/views` (classe `org.grox.view.gsp.GspPrecompiler`) para `build/gsp-classes`, que entra no classpath e no jar. Pulada sem o diretório de views ou com `precompileGsp = false` |
| `assetCompile` | build | Processa `grails-app/assets` (diretivas `require`, digest, `manifest.properties`) para `build/assets` (`META-INF/assets` no classpath). Pulada sem o diretório |
| `groxPackageIndex` | | Lista em `META-INF/grox/packages` os pacotes dos fontes de `grails-app/`, e `class:Nome` para fontes sem `package` |
| `groxPatch` | | Só com `patches`: copia os originais alterados para `build/grox-patched` e aplica os patches (`patch -p1`). Um patch que não se aplica mais falha o build |
| `groxReloadStamp` | | Compila e grava `build/grox-reload/stamp`. É a tarefa que o Gradle contínuo da recarga executa; o carimbo novo dispara a recarga |
| `aotCache` | distribution | Depende de `installDist`. Roda `bin/<app>` uma vez em modo de treino (`-XX:AOTCacheOutput`, `-Dgrox.aot.training=true`, porta aleatória): a app sobe, encerra ao ficar pronta e deixa `lib/grox.aot` |

Com o plugin `application`, também valem `run`, `installDist`, `distZip`/`distTar` e `startScripts`.

### Recarga no `run`

O `run` passa à app, por propriedades de sistema, os diretórios de classes e resources e, se houver `gradlew` na raiz, o comando do Gradle contínuo:

| Propriedade | Valor |
|---|---|
| `grox.reload.classes` | Diretórios de classes do `main` e das bibliotecas `org.grox.lib` do mesmo build |
| `grox.reload.resources` | Diretórios de resources do `main` e dessas bibliotecas |
| `grox.reload.gradlew` | Caminho do `gradlew` |
| `grox.reload.tasks` | `:<projeto>:groxReloadStamp` |
| `grox.reload.stamp` | `build/grox-reload/stamp` |
| `grox.reload.sources` | Só quando algum diretório de fontes (da app ou das bibliotecas) está fora da raiz do build: todos os diretórios de fontes. A app os observa e roda o Gradle sob demanda, no lugar do contínuo |

O classpath do `run` traz a saída dessas bibliotecas (classes e resources) no lugar dos jars (o `compileGroovy` da app usa os jars, e o Gradle os reescreveria com a JVM rodando), e o `groxReloadStamp` também as compila. Sem `gradlew`, ou com `-Dgrox.reload.compile=false`, a IDE compila. Veja [Recarga em desenvolvimento](../recursos/recarga.md).

### AOT cache

```bash
./gradlew installDist aotCache
build/install/minha-app/bin/minha-app       # usa lib/grox.aot quando existe
```

- O treino usa a configuração do ambiente (inclusive o banco). Rode com as mesmas variáveis de produção.
- Qualquer `installDist` depois apaga o cache: rode `aotCache` por último.
- O cache vale para aquele JDK e aquele classpath. Com outro, a JVM avisa e sobe sem ele.
- Exige classpath de JARs (por isso usa o `installDist`, e não o `run`).

## `org.grox.lib`

Aplica `groovy` e `java-library` e o mesmo layout `grails-app/` da aplicação (fontes, resources, compilação incremental e `groxPackageIndex`). Não tem `Application`, `run`, `compileGsp`, `assetCompile` nem `aotCache`.

```groovy
// core/build.gradle
plugins { id 'org.grox.lib' }
dependencies { api project(':framework') }

// app/build.gradle
dependencies { implementation project(':core') }
```

- A app descobre os artefatos da biblioteca pelo índice `META-INF/grox/packages`, sem configuração.
- As `messages*.properties` da biblioteca são mescladas com as da app, e a app prevalece.
- Views, assets e `application.yml` continuam só na aplicação.
- No `run` da app, as classes da biblioteca (do mesmo build) recarregam como as da app. Ver [Recarga em desenvolvimento](../recursos/recarga.md#bibliotecas-orggroxlib).

## Apps sem `grails-app/`

Ainda não há plugin para as apps do `grox-dsl` (o `org.grox.plain` está no roadmap). Configure à mão, como o `sample-plain`:

```groovy
plugins {
    id 'groovy'
    id 'application'
}
dependencies {
    implementation project(':framework:grox-core')
    implementation project(':framework:grox-gorm')
    implementation project(':framework:grox-gsp')
    implementation project(':framework:grox-security')
    implementation project(':framework:grox-dsl')
    runtimeOnly libs.slf4j.simple
    runtimeOnly libs.h2
}
java { toolchain { languageVersion = JavaLanguageVersion.of(27) } }
// handlers por anotação recebem os parâmetros pelo nome
tasks.withType(GroovyCompile).configureEach { groovyOptions.parameters = true }
application { mainClass = 'livraria.Aplicacao' }
tasks.named('run') { systemProperty 'grox.env.default', 'development' }
tasks.named('test') {
    useJUnitPlatform()
    systemProperty 'grox.env.default', 'test'
    forkEvery = 1           // cada classe de teste sobe a própria aplicação
}
```

Sem o plugin, não há `compileGsp` (as GSPs compilam na primeira requisição), `assetCompile`, recarga de classes nem `aotCache`. As views ficam em `src/main/resources/views/`.

## Veja também

- [Estrutura de uma aplicação](../recursos/estrutura.md)
- [Projeto do zero](../sem-grails/projeto-do-zero.md)
- [Implantação em produção](../recomendacoes/implantacao.md)
- [Configuração](configuracao.md)
