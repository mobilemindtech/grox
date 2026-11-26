# Passo a passo

Este capítulo descreve a migração de uma aplicação Grails, do levantamento até a produção, com os comandos e arquivos usados nos projetos reais.

## 1. Levantamento

Antes de mexer no código, faça um inventário. Ele mostra cedo o que falta no Grox:

| Item | Como levantar |
|---|---|
| Tamanho | Número de controllers, services, domain classes (e quantas multi-tenant), jobs, TagLibs, GSPs, assets |
| Plugins | `build.gradle` do projeto: cada `org.grails.plugins:*` (compare com [Plugins do Grails](plugins.md)) |
| Bibliotecas | Dependências que não são do Grails; as Groovy precisam rodar no Groovy 6 |
| GORM | Finders, `createCriteria` com projeções, HQL, `executeUpdate`, Data Services, listeners, `static mapping` (`formula`, `sqlType`, índices), herança, `embedded` |
| Web | `respond` (com `domainSuffix`?), `render` com closure, `request.JSON`, `RestfulController`, `withFormat`, uploads |
| GSP | Tags usadas (`grep -oh '<g:[a-zA-Z]*' -r grails-app/views | sort | uniq -c`), TagLibs próprias |
| Segurança | `interceptUrlMap` ou `staticRules`, `@Secured`, REST (JWT ou GORM), `chainMap`, `LoginController` próprio, `passwordEncoder` |
| Sessão | spring-session-redis, cookie-session |
| Imports | Classes importadas que não resolvem: imprima o classpath (`printClasspath` no build do candidato) e compile para ver a lista |

Compare o resultado com a [Compatibilidade com o Grails](../referencia/compatibilidade.md).

## 2. Build

O plugin `org.grox.app` substitui o plugin do Grails: aplica `groovy` e `application`, mapeia o layout `grails-app/` e adiciona as tarefas `compileGsp` e `assetCompile`.

```groovy
plugins {
    id 'org.grox.app'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation project(':framework')         // todos os módulos, inclusive os plugins
    implementation libs.groovy.sql               // módulos do Groovy que a app usa
    implementation libs.groovy.http.builder      // substituto do HTTPBuilder (ver Armadilhas)
    implementation libs.poi.ooxml                // a app declara as dependências pesadas dos plugins (excel-export)
    runtimeOnly libs.mysql
    runtimeOnly libs.h2
    runtimeOnly libs.slf4j.simple
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(27) }
}

application {
    mainClass = 'br.com.exemplo.Application'
}

// os testes do projeto original (Spock + grails-testing) não rodam no Grox
sourceSets.test.groovy.srcDirs = []
```

- **Dependências dos plugins:** os plugins reimplementados não trazem as bibliotecas pesadas. A aplicação declara `poi-ooxml` (excel-export), `jedis` (redis), `amqp-client` (rabbitmq-native), `flying-saucer-pdf` (rendering).
- **Projetos grandes:** o compilador pode precisar de mais memória (`groovyOptions.forkOptions.memoryMaximumSize = '4g'`). Com pouca memória o worker do Gradle estoura e fica pendurado.
- **Projetos com vários módulos:** artefatos compartilhados (domains, services) vão para um subprojeto com o plugin `org.grox.lib`, do qual as aplicações dependem (veja [Estrutura de uma aplicação](../recursos/estrutura.md)).

## 3. Importar o código

> Se você tem acesso ao repositório da app, prefira o [build paralelo](build-paralelo.md): um diretório `grox/` no próprio repositório compila o código original sem cópia, e a configuração fica num `application-grox.yml`, sem sanitizar o `application.yml`. A importação abaixo é para quando o build do Grox precisa viver em outro repositório.

Copie `grails-app/` e `src/main` do projeto. No modelo dos candidatos, um `import.sh` faz isso, aplica `overrides/` e os `patches/`:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SRC="${APP_DIR:-/caminho/do/projeto}"
rm -rf grails-app src && mkdir -p src
# o application.yml original tem credenciais: nunca é copiado
rsync -a --exclude 'conf/application.yml' --exclude 'conf/logback-spring.xml' "$SRC/grails-app/" grails-app/
rsync -a "$SRC/src/main" src/
rsync -a overrides/ ./
for p in patches/*.patch; do patch -p1 --no-backup-if-mismatch < "$p"; done
```

A classe `Application` pode continuar estendendo `GrailsAutoConfiguration` e chamando `GrailsApp.run(Application, args)`: as duas existem no Grox (`grails.boot`). O `doWithSpring()` dela é lido.

## 4. Credenciais e configuração

Não versione credenciais. Troque os valores sensíveis do `application.yml` por placeholders, resolvidos na carga a partir de propriedades de sistema, variáveis de ambiente ou outras chaves:

```yaml
environments:
  development:
    dataSource:
      url: "${APP_DEVELOPMENT_DATASOURCE_URL:jdbc:h2:mem:app;MODE=MySQL;DATABASE_TO_LOWER=TRUE}"
      username: "${APP_DEVELOPMENT_DATASOURCE_USERNAME:sa}"
      password: "${APP_DEVELOPMENT_DATASOURCE_PASSWORD:}"
      dbCreate: "${APP_DB_CREATE:none}"
```

No repositório do Grox, `candidates/sanitize-yml.py PREFIXO origem.yml destino.yml` gera esse arquivo: cada valor de chave sensível (password, secret, username, host, token, key...) vira `${PREFIXO_CAMINHO:}`, e o `dataSource` de cada ambiente passa a `dbCreate ${PREFIXO_DB_CREATE:none}` com H2 em memória (modo MySQL) como padrão. Ele troca pelo nome da chave, então **revise o resultado** antes de versionar.

Qualquer chave também pode vir de variável de ambiente (`server.port` → `SERVER_PORT`) ou de `-Dchave=valor`.

## 5. Compilar

```bash
./gradlew :minha-app:compileGroovy :minha-app:compileGsp :minha-app:assetCompile
```

- **Groovy:** os erros comuns são imports de plugins não reimplementados, bibliotecas com `CallSite` e as [armadilhas do Groovy 6](../apendices/groovy6.md). Erros de Groovy dinâmico (métodos inexistentes) só aparecem na execução.
- **GSP:** o `compileGsp` falha o build com arquivo e linha. Tags com vírgula entre atributos ou sem `>` antes do próximo `<` são aceitas com aviso.
- **Assets:** um `require` inexistente só gera aviso, como no asset-pipeline.

## 6. Subir com H2

Suba em development com banco em memória e sem integrações:

```bash
#!/usr/bin/env bash
# run-h2.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
export DATASOURCE_DRIVERCLASSNAME=org.h2.Driver
export APP_DB_CREATE="${APP_DB_CREATE:-update}"     # H2 em memória: schema criado pelo Hibernate
export SERVER_PORT="${SERVER_PORT:-8089}"
export GROX_JOBS_ENABLED="${GROX_JOBS_ENABLED:-false}"
export APP_SESSION_STORE="${APP_SESSION_STORE:-memory}"
exec ./gradlew -q :minha-app:run "$@"
```

- **Dados mínimos:** um `BootStrap` só do ambiente de migração (em `overrides/`, por exemplo `PilotBootStrap` ou `CandidateBootStrap`) cria tenant, roles e um usuário de teste. Só rode em development com H2. Dados multi-tenant são criados dentro de `Tenants.withId(...) { }`.
- **Jobs desligados** (`grox.jobs.enabled: false`) até a aplicação estar estável.
- **Dependências externas** (Flyway, Solr, serviços HTTP): desligue ou troque por no-op num `resources.groovy` de `overrides/`.

O log mostra o tempo de cada fase da subida e os artefatos encontrados.

## 7. Navegar

Faça uma varredura: login, depois uma busca em largura pelos links internos (GET), anotando o status de cada página. Repita anônimo e com cada perfil importante.

Para cada 500, decida se é do Grox ou da aplicação. Nos candidatos, a maioria dos erros restantes era da aplicação no ambiente de teste:

- parâmetro ou corpo JSON ausente (página que só funciona por POST ou AJAX);
- integração externa sem configuração;
- dados que não existem num banco vazio;
- código que também falha no Grails moderno (`session.createSQLQuery`, removido no Hibernate 6; HQL com nomes de coluna).

Respostas 302, 400, 401, 404 e 405 são esperadas para actions POST-only, sem view ou protegidas.

## 8. CSRF

O Grox liga CSRF por padrão; o spring-security-core do Grails não tem CSRF. Se os formulários (`<form>` puro) e os AJAX da aplicação não enviam o token, escolha:

- **Desligar** (o que os candidatos fizeram, para não alterar as views):

  ```yaml
  grails:
    plugin:
      springsecurity:
        csrf:
          enabled: false
  ```

- **Manter ligado:** use `<g:form>` (já inclui `_csrf`), `<sec:csrfInput/>` em formulários puros e o cabeçalho `X-CSRF-TOKEN` (valor de `<sec:csrfToken/>`) no AJAX.

## 9. Banco real

```bash
APP_DEVELOPMENT_DATASOURCE_USERNAME=... APP_DEVELOPMENT_DATASOURCE_PASSWORD=... candidates/minha-app/run-mysql.sh
```

- **Comece com `dbCreate: none`.** Antes de deixar o Grox mexer no schema, veja o que ele faria:

  ```bash
  -Dgrox.schema.updateScript=/tmp/ddl.sql -Dgrox.schema.scriptOnly=true
  ```

  Com `dbCreate: update`, o arquivo recebe o DDL **sem executar nada**, e `scriptOnly` encerra a aplicação logo depois, antes do BootStrap. O esperado num banco que já funcionava com o Grails é um arquivo vazio. Diferenças costumam indicar tamanhos de coluna ou nomes que divergem: corrija o mapeamento da domain.
- **`update` seguro:** mesmo com `update`, o Grox só cria tabelas, colunas, chaves e índices, e nunca altera colunas existentes (`grox.schema.update: safe`, padrão), como o GORM sobre Hibernate 5. Vale também com `hibernate.dialect` explícito de MySQL, MariaDB, H2 ou PostgreSQL.
- **Nomes físicos:** iguais aos do GORM (snake_case, regra do `ImprovedNamingStrategy`; palavras reservadas citadas). Se aparecerem tabelas ou colunas novas no DDL, é uma divergência a investigar.
- **Senhas:** os hashes do spring-security (`{bcrypt}`, `{SHA-256}`, `$2a$` sem prefixo) são lidos como estão.
- **Sem URL configurada**, um yml com o driver do MySQL e URL do H2 faz o Hibernate falhar com "Unable to determine Dialect": o `run-mysql.sh` define a URL local.

## 10. Sessão no Redis

O Grox não lê `server.servlet.session` nem `spring.session.redis`. Troque por:

```yaml
grox:
  session:
    store: "${APP_SESSION_STORE:redis}"
    cookieName: APPSESSION
    timeout: 3600
    redis:
      url: "${APP_SESSION_REDIS_URL:redis://localhost:6379}"   # com senha: redis://:senha@host:6379
      keyPrefix: 'app:grox:session:'
```

Chaves e serialização são diferentes das do Spring Session: os dois convivem no mesmo Redis, mas as sessões não são migradas (quem estiver logado entra de novo). Todo atributo de sessão precisa ser `Serializable`; guarde ids, não entidades.

## 11. Integrações, tokens e jobs

- **spring-security-rest:** confira `/api/login`, o armazenamento do token (JWT ou `useGorm`), o cabeçalho (`useBearerToken`, `headerName`) e o `chainMap`.
- **Jobs:** ligue (`GROX_JOBS_ENABLED=true`) e confira cada um, principalmente os que usam `Tenants.withId`.
- **E-mail, Excel, PDF, Redis, RabbitMQ:** teste ponta a ponta.
- **Escrita:** formulários de CRUD (save, update, delete) e uploads com dados reais.
- **Visual:** compare páginas com a versão Grails (layouts, assets).

## 12. Produção

```bash
./gradlew :minha-app:installDist :minha-app:aotCache
build/install/minha-app/bin/minha-app
```

- O artefato empacotado sobe em **production** (padrão sem `GRAILS_ENV`/`GROX_ENV`), como no Grails.
- As GSPs vêm pré-compiladas e os assets com digest.
- O AOT cache (JDK 25+) acelera a subida; rode `aotCache` por último, com as variáveis de produção.
- Veja [Implantação em produção](../recomendacoes/implantacao.md) e o [checklist de segurança](../recomendacoes/seguranca.md).

## Veja também

- [O que muda em relação ao Grails](diferencas.md)
- [Armadilhas da migração](armadilhas.md)
- [Casos reais: apps A, B e C](casos-reais.md)
- [Configuração](../referencia/configuracao.md)
- [Plugins e tarefas Gradle](../referencia/gradle.md)
