# Implantação em produção

Como empacotar, configurar e operar uma aplicação Grox em produção. O artefato é uma distribuição comum do plugin `application` do Gradle (scripts em `bin/` e jars em `lib/`), sem container de Servlet: o Netty embutido atende as requisições.

## Empacotar

```bash
./gradlew installDist                 # build/install/minha-app/{bin,lib}
./gradlew installDist aotCache        # com o AOT cache do JDK (subida mais rápida)
./gradlew distZip                     # build/distributions/minha-app.zip
```

- O build pré-compila as GSPs (`compileGsp`) e os assets (`assetCompile`). Um erro de GSP ou um `require` de asset inexistente falha o build.
- **AOT cache:** a tarefa `aotCache` faz uma execução de treino da app instalada, que sobe, encerra ao ficar pronta e grava `lib/grox.aot`. O `bin/minha-app` o usa quando existe. O treino usa a configuração do ambiente (inclusive o banco), então rode com as variáveis de produção, e rode `aotCache` por último: um `installDist` depois apaga o cache. O cache vale para aquele JDK e aquele classpath.
- **Imagem de container:** copie `build/install/minha-app` para a imagem e use `bin/minha-app` como entrada. Gere o AOT cache no mesmo JDK da imagem.

## JVM e memória

### Onde passar as opções

- **Na execução:** `JAVA_OPTS` ou `MINHA_APP_OPTS` (o nome da app em maiúsculas), lidas pelo `bin/minha-app`.
- **No build:** `application { applicationDefaultJvmArgs = ['-Xmx384m'] }` no `build.gradle`; vão para o script gerado.
- **AOT cache:** a tarefa `aotCache` usa o `JAVA_OPTS` do ambiente no treino. Rode-a com as mesmas opções de produção.

### Quanto de memória

Números de referência, medidos numa app pequena (a mesma app de livros nas duas pilhas, com `-Xmx512m`; detalhes em `docs/benchmark.md`):

| | Grails 8 | Grox |
|---|---:|---:|
| Heap vivo depois de subir (após GC completo) | 101 MB | 76 MB |
| Heap vivo depois de carga | 110 MB | 89 MB |
| Metaspace | 93 MB | 70 MB |
| Threads sob carga | 113 | 58 |
| Menor `-Xmx` que aguentou a carga sem erros | 96 MB | 64 MB |
| RSS do processo, parado → sob carga | 639 → 732 MB | 470 → 675 MB |

- **Heap:** comece com 2 a 3 vezes o heap vivo da sua app depois de carga. Para uma app pequena, `-Xmx256m` a `-Xmx512m`. Apps grandes (centenas de domain classes e controllers) carregam muito mais classes, e o heap cresce junto: no Groovy, os maiores ocupantes são as metaclasses (`MetaMethodIndex$Cache`, `CachedMethod`), não o Hibernate. Meça a sua, como abaixo.
- **O RSS não é o heap.** Com heap pequeno, o processo ainda ocupa de 300 a 500 MB: metaspace, code cache do JIT, estruturas do GC, pilhas das threads, buffers diretos do Netty e arenas nativas. Num contêiner, o limite de memória precisa cobrir tudo isso, não só o `-Xmx`.
- **Sessões em memória** ficam no heap. Com muito tráfego anônimo (cada formulário com CSRF cria uma), use `grox.session.store: redis`.

### Em contêiner

Sem `-Xmx`, a JVM usa 1/4 do limite do contêiner como heap. Defina o heap explicitamente e limite também o que não é heap, deixando folga para o RSS:

```bash
# ponto de partida para uma app pequena num contêiner de 768 MB; meça a sua antes de fixar os valores
JAVA_OPTS="-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=128m -XX:MaxDirectMemorySize=64m" bin/minha-app
```

- `-XX:MaxMetaspaceSize`: o dobro do metaspace medido. Se faltar, a app cai com `OutOfMemoryError: Metaspace`.
- `-XX:ReservedCodeCacheSize`: baixo demais faz o JIT parar de compilar, e o desempenho cai sem erro. Não desça de 64 MB.
- `-XX:MaxDirectMemorySize`: limita os buffers diretos do Netty (o padrão é o tamanho do heap).
- Alternativa ao `-Xmx` fixo: `-XX:MaxRAMPercentage=50`, que acompanha o limite do contêiner.

### Coletor de lixo

- O padrão (G1) serve na maioria dos casos.
- Em instâncias pequenas (1 CPU ou menos de 2 GB), a JVM já escolhe o Serial sozinha. Para forçá-lo, `-XX:+UseSerialGC`: menos memória e threads do GC, com pausas maiores.
- Para heaps grandes e latência baixa, `-XX:+UseZGC`. Meça antes de trocar.

### Como medir a sua app

```bash
JAVA_OPTS="-Xmx512m -XX:NativeMemoryTracking=summary" bin/minha-app &
# depois de exercitar as páginas principais (ou de uma rodada de carga):
jcmd <pid> GC.run && jcmd <pid> GC.heap_info          # heap vivo
jcmd <pid> VM.native_memory summary scale=MB          # metaspace, code cache, threads, GC
ps -o rss= -p <pid>                                   # RSS em KB
```

O `bench/run-mem.sh` do repositório do Grox faz essa medição para as apps de referência.

## Ambiente

Sem configuração, o artefato sobe em **`production`**. Não defina `GRAILS_ENV=development` em produção: liga o console web, o reload de views e as mensagens de exceção nas páginas de erro.

## Configuração por variáveis

Não grave credenciais no `application.yml`. Use placeholders, e passe os valores por variáveis de ambiente ou `-D`:

```yaml
environments:
  production:
    dataSource:
      url: "${DB_URL}"
      username: "${DB_USER}"
      password: "${DB_PASSWORD}"
      dbCreate: none
    grails:
      plugin:
        springsecurity:
          rest:
            token:
              storage:
                jwt:
                  secret: "${JWT_SECRET}"
```

Qualquer chave também pode vir de uma variável de ambiente (`server.port` → `SERVER_PORT`) ou de `JAVA_OPTS="-Dchave=valor"`.

## Banco de dados

- **`dbCreate: none` ou `validate` em produção.** Nunca `create-drop` nem `create`.
- **Antes de apontar para um banco existente**, veja o que o `update` faria, sem executar nada:

  ```bash
  JAVA_OPTS="-Dgrox.schema.updateScript=/tmp/ddl.sql -Dgrox.schema.scriptOnly=true" bin/minha-app
  ```

  Revise o DDL e aplique por migração (Flyway, Liquibase ou à mão).
- Se usar `update`, mantenha `grox.schema.update: safe` (padrão): cria tabelas, colunas e índices, mas nunca altera colunas existentes. O Hibernate 7 puro (`full`) faz `alter table ... modify column`, que num banco grande pode truncar dados ou segurar a subida.
- Ajuste o pool do HikariCP (`dataSource.properties.maximumPoolSize`, `connectionTimeout`).

## Vários nós

Uma aplicação com mais de uma instância atrás de um balanceador precisa de estado compartilhado:

| Estado | Configuração |
|---|---|
| Sessão HTTP | `grox.session.store: redis` e `grox.session.redis.url` |
| JWT revogados no logout | `grox.security.tokenRevocation.store: redis` (padrão: o mesmo da sessão) |
| Segredo do JWT | `rest.token.storage.jwt.secret` igual em todos os nós (com ao menos 32 bytes). Sem ele, cada nó gera um aleatório e os tokens só valem no nó que os emitiu |
| Cache de tokens GORM | Por nó: um logout invalida na hora só no nó que o recebeu; nos outros, depois de `cacheSeconds` |
| Jobs | Rodam em todos os nós. Ligue em um só (`grox.jobs.enabled: false` nos outros) ou numa app separada só de jobs |
| Limite de tentativas de login | Por nó |

Com sessão no Redis, todo atributo precisa ser `Serializable`, e só as classes dos pacotes da app (e de `grox.session.serialization.allow`) são aceitas na leitura.

## Proxy reverso e HTTPS

O Grox fala HTTP. TLS normalmente termina num proxy reverso (nginx, HAProxy, o balanceador da nuvem).

- **Proxies confiáveis:** os cabeçalhos `X-Forwarded-For`, `X-Forwarded-Proto` e `X-Forwarded-Host` só são aceitos de endereços em `grox.http.trustedProxies` (padrão: loopback e redes privadas). Se o proxy estiver em outro endereço, inclua-o; se a aplicação estiver exposta direto à internet, defina `''` para não aceitar de ninguém.
- **Cookie `Secure`:** `grox.session.cookie.secure: auto` (padrão) marca o cookie como `Secure` quando a requisição é HTTPS, inclusive pelo `X-Forwarded-Proto` de um proxy confiável. Para forçar, `true`.
- **HSTS:** acrescente pelo mapa de cabeçalhos:

  ```yaml
  grox:
    http:
      securityHeaders:
        Strict-Transport-Security: "max-age=31536000; includeSubDomains"
  ```

- **Links absolutos:** defina `grails.serverURL` (ou `grox.serverURL` nas apps do `grox-dsl`) com a URL pública.
- **Limites de conexão:** `grox.http.requestTimeout`, `idleTimeout`, `maxConnections` e `maxContentLength` têm padrões seguros; ajuste o `maxContentLength` ao maior upload esperado.

## Saúde e orquestração

| Endpoint | Uso |
|---|---|
| `GET /actuator/health` | Readiness e liveness. `200` com `UP`; `503` se o banco ou o Redis estiverem fora; `OUT_OF_SERVICE` durante o desligamento |
| `GET /actuator/info` | Versões e horário de início |
| `GET /actuator/metrics`, `/actuator/prometheus` | Métricas (exigem `ROLE_ADMIN` com segurança, ou `grox.management.metricsAccess`) |

Em Kubernetes, aponte o `readinessProbe` para `/actuator/health`. Com `rejectIfNoRule: true`, o `/health` e o `/info` continuam públicos.

## Desligamento gracioso

No `SIGTERM`, a aplicação:
1. muda o `/health` para `OUT_OF_SERVICE`;
2. para de aceitar conexões;
3. espera as requisições em andamento terminarem de ser escritas, até `grox.shutdown.timeout` (padrão 30 s), respondendo com `Connection: close`;
4. encerra jobs, atores, sessões e o banco.

Configure o tempo de término do orquestrador (`terminationGracePeriodSeconds`) acima do `grox.shutdown.timeout`.

## Logs

- O framework loga por SLF4J. Inclua um backend (Logback, `slf4j-simple`...) como `runtimeOnly`.
- **Access log:** `grox.accessLog.enabled: true`, com `format: json` para agregadores. As linhas vão para o logger `grox.access`.
- **Console web:** registra cada execução no logger `org.grox.plugins.console.WebConsole`.
- Fora de development, as páginas de erro e o `/health` não mostram mensagens de exceção; elas continuam no log.

## Checklist de produção

- [ ] Ambiente `production` (sem `GRAILS_ENV=development`)
- [ ] Credenciais por variáveis, nada no repositório
- [ ] `dbCreate: none` ou `validate`; DDL revisado com `grox.schema.updateScript`
- [ ] `jwt.secret` fixo com ao menos 32 bytes (se usar JWT)
- [ ] Sessão e revogação no Redis se houver mais de um nó
- [ ] Jobs ligados em um nó só
- [ ] `grox.http.trustedProxies` conforme a rede; HSTS no proxy ou em `securityHeaders`
- [ ] Console web desligado (`grox.console.enabled` só em development)
- [ ] `/actuator/health` no readiness; `terminationGracePeriodSeconds` > `grox.shutdown.timeout`
- [ ] Backend de log e access log configurados
- [ ] Heap (`-Xmx` ou `MaxRAMPercentage`), metaspace e code cache definidos, com folga para o RSS no limite do contêiner
- [ ] `installDist aotCache` no mesmo JDK e com as mesmas opções da execução

## Veja também

- [Segurança: checklist](seguranca.md)
- [Desempenho](desempenho.md)
- [Operação: gestão, logs e desligamento](../recursos/operacao.md)
- [Plugins e tarefas Gradle](../referencia/gradle.md)
- [Configuração](../referencia/configuracao.md)
