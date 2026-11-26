# Build paralelo no repositório Grails

Este capítulo mostra como compilar e rodar uma app Grails sobre o Grox **dentro do próprio repositório dela**, ao lado do build do Grails e sem mudar nada nele. É o jeito recomendado de manter uma migração em andamento: o time continua no Grails, e o build do Grox acompanha cada commit, sem copiar código.

## A ideia

Um subdiretório `grox/` guarda um segundo build Gradle, com o plugin `org.grox.app` (ou `org.grox.lib`) apontando para os diretórios originais da app:

```
minha-app/
  settings.gradle, build.gradle    ← build do Grails (inalterado)
  grails-app/ src/main/
  grox/
    settings.gradle                ← Grox por composite build
    gradle.properties              ← groxDir=/caminho/do/grox
    gradlew                        ← delega ao gradlew do Grox
    build.gradle                   ← grox { source('..') }
    grails-app/conf/application-grox.yml
    grails-app/init/.../BootStrapH2.groovy      ← arquivos só do Grox
    patches/                       ← ajustes no código, aplicados só neste build
    run-h2.sh
```

O build do Grails não inclui `grox/` (o `settings.gradle` dele não o lista), e o build do Grox grava só em `grox/build/`. Os dois usam versões diferentes de tudo (Groovy 5 e Grails de um lado, Groovy 6 e Java 27 do outro) sem conflito, porque nenhum enxerga o classpath do outro.

## O build

```groovy
// grox/settings.gradle
pluginManagement {
    def grox = providers.environmentVariable('GROX_DIR').orElse(providers.gradleProperty('groxDir')).get()
    includeBuild("${grox}/gradle-plugin")   // org.grox.app / org.grox.lib
}
plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

def groxDir = providers.environmentVariable('GROX_DIR').orElse(providers.gradleProperty('groxDir')).get()
includeBuild(groxDir) {
    dependencySubstitution {
        substitute module('org.grox:framework') using project(':framework')
    }
}
dependencyResolutionManagement {
    versionCatalogs {
        libs { from(files("${groxDir}/gradle/libs.versions.toml")) }   // as versões do Grox
    }
}
rootProject.name = 'minha-app-grox'
```

```groovy
// grox/build.gradle
plugins { id 'org.grox.app' }

grox {
    source('..') {                                    // o grails-app/ e o src/main/ da app
        exclude 'grails-app/conf/logback-spring.xml'
    }
    patches.from('patches')                           // os *.patch do diretório
}

repositories { mavenCentral() }
dependencies {
    implementation 'org.grox:framework'
    runtimeOnly libs.mysql
    runtimeOnly libs.h2
    runtimeOnly libs.slf4j.simple
}
java { toolchain { languageVersion = JavaLanguageVersion.of(27) } }
application { mainClass = 'minhaapp.Application' }
```

```sh
#!/usr/bin/env sh
# grox/gradlew: o Gradle do Grox, neste diretório
DIR=$(cd "$(dirname "$0")" && pwd)
GROX_DIR=${GROX_DIR:-$(sed -n 's/^groxDir=//p' "$DIR/gradle.properties")}
exec "$GROX_DIR/gradlew" -p "$DIR" "$@"
```

O composite build compila o Grox junto, então não é preciso publicá-lo para trabalhar localmente. Para CI ou outros desenvolvedores, publique o framework e troque o `includeBuild` pela coordenada.

## Raízes, overrides e patches

`grox { source(dir) }` *(extensão Grox)* adiciona uma raiz com o layout do Grails: `grails-app/{controllers,services,domain,...}`, `conf`, `i18n`, `views` e `src/main/{groovy,java,resources,templates}`. Pode haver várias, e os padrões `include`/`exclude` de cada uma são relativos a ela:

```groovy
// um projeto Grox que junta dois módulos Grails (app + plugin de TagLibs)
grox {
    source('../../app-c-web') {
        exclude 'src/main/java/br/com/empresa/app C/aot/**'     // AOT do Spring
    }
    source('../../app C-taglibs') {
        include 'grails-app/taglib/**', 'src/main/groovy/**'    // só as TagLibs e o descritor
    }
}
```

A precedência é: as raízes, na ordem declarada; depois o próprio projeto (`grox/`); depois os fontes com patch. **Um arquivo de mesmo caminho relativo numa posição posterior esconde o das anteriores**, desde que os padrões da raiz posterior o aceitem: uma raiz com `include 'grails-app/taglib/**'` não esconde o `grails-app/conf/application.yml` das anteriores, mesmo que tenha um. Assim:

- **Arquivo novo só do Grox** (um BootStrap que cria dados no H2): crie-o em `grox/grails-app/init/...`.
- **Arquivo substituto** (um descritor de plugin que o Grox não consegue usar): crie em `grox/` com o mesmo caminho do original, por exemplo `grox/src/main/groovy/org/grails/plugins/swagger/GrailsSwaggerGrailsPlugin.groovy`.
- **Ajuste pequeno num arquivo** (uma linha de HQL, um import): prefira um patch, que continua valendo quando o resto do arquivo muda.

Os patches são diffs unificados com caminhos relativos à raiz:

```diff
--- a/grails-app/services/minhaapp/RelatorioService.groovy
+++ b/grails-app/services/minhaapp/RelatorioService.groovy
@@ -10,1 +10,1 @@
-        Cliente.executeQuery("from Cliente where arquivo_morto = false")
+        Cliente.executeQuery("from Cliente c where c.arquivoMorto = false")
```

A tarefa `groxPatch` copia o original (da última raiz que o tem) para `build/grox-patched` e aplica o patch (`patch -p1`). O original não muda. Quando o original muda e o patch deixa de se aplicar, **o build falha** com a saída do `patch`, e você atualiza o patch:

```bash
diff -u --label a/grails-app/services/minhaapp/RelatorioService.groovy \
        --label b/grails-app/services/minhaapp/RelatorioService.groovy \
        ../grails-app/services/minhaapp/RelatorioService.groovy RelatorioService.groovy > patches/RelatorioService.patch
```

Patches que também funcionam no Grails (HQL com nomes de propriedade, uma classe do POI renomeada) podem ir para o projeto quando o time quiser; aí o patch sai.

## Configuração: o perfil `grox`

O build usa o `application.yml` da app. Os ajustes do Grox vão para `grails-app/conf/application-grox.yml` (ou `.groovy`) em `grox/`, que o Grox lê por último, acima de todos os outros arquivos *(extensão Grox)*. O Grails não o lê: no Spring Boot, ele só valeria com o perfil `grox` ativo.

```yaml
# grox/grails-app/conf/application-grox.yml
grails:
    plugin:
        springsecurity:
            csrf:
                enabled: false        # o spring-security-core do Grails não tem CSRF; os formulários não enviam _csrf
dataSource:
    dbCreate: "${APP_DB_CREATE:none}" # nunca update por padrão contra o banco real
grox:
    session:
        store: "${APP_SESSION_STORE:redis}"
```

Para subir com H2, use variáveis de ambiente, que vencem qualquer arquivo:

```bash
#!/usr/bin/env bash
# grox/run-h2.sh
set -euo pipefail
cd "$(dirname "$0")"
export DATASOURCE_DRIVERCLASSNAME=org.h2.Driver
export DATASOURCE_URL="jdbc:h2:mem:app;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE,KEY,COUNT"
export DATASOURCE_USERNAME=sa DATASOURCE_PASSWORD=
export APP_DB_CREATE=update APP_SESSION_STORE=memory
exec ./gradlew -q run "$@"
```

## Views e assets

Com raízes externas, o `processResources` reúne as views de todas as raízes (com overrides e patches) e o `compileGsp` as pré-compila de lá. Os assets vêm do projeto ou da última raiz que os tem; no `run`, o plugin passa `-Dgrox.assets.dir` com o diretório original, e editar um asset vale na hora.

## Recarga com vários subprojetos

Num build com vários subprojetos (o app-c: `app-c-core` com domains e services, usado por `app-c-web`, `app-c-api` e `app-c-jobs`), a recarga do `run` de uma app acompanha também as bibliotecas `org.grox.lib` de que ela depende, inclusive as transitivas. Editar um service em `app C-services` recarrega o `app-c-web` como se fosse dele. Como as raízes externas ficam fora da raiz do build, onde o Gradle contínuo não enxerga, a app observa os fontes originais e roda o Gradle a cada mudança (ver [Fontes fora da raiz do build](../recursos/recarga.md#fontes-fora-da-raiz-do-build)). As views das raízes externas ainda não recarregam: elas chegam ao classpath pelo `processResources`. Quando uma biblioteca muda, a app inteira é recompilada (no app-c, cerca de 3 minutos por mudança no `app-c-core`) (ver [Recarga em desenvolvimento](../recursos/recarga.md#bibliotecas-orggroxlib)).

## No CI

Rode os dois builds: `./gradlew build` (Grails) e `cd grox && ./gradlew build` (Grox). Uma mudança feita pensando só no Grails pode cair numa [armadilha do Groovy 6](../apendices/groovy6.md), e o build do Grox avisa no mesmo commit.

## Veja também

- [Passo a passo](passo-a-passo.md)
- [Casos reais: apps A, B e C](casos-reais.md)
- [Plugins e tarefas Gradle](../referencia/gradle.md)
- [Configuração](../referencia/configuracao.md)
