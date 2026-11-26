# Operação: gestão, logs e desligamento

Este capítulo cobre o que importa para rodar a aplicação: os endpoints de gestão (health, info, métricas, Prometheus), o access log, o desligamento gracioso e a subida rápida (JIT no desenvolvimento e AOT cache em produção).

## Endpoints de gestão

No estilo do Spring Boot Actuator, em `grox.management.basePath` (padrão `/actuator`):

| Endpoint | Conteúdo | Acesso |
|---|---|---|
| `/actuator/health` | `{"status": "UP", "checks": {"db": {...}, "redis": {...}}}`. Devolve 503 se algo estiver DOWN, e `OUT_OF_SERVICE` durante o desligamento | público |
| `/actuator/info` | Aplicação, ambiente, versões do Groovy e do Java, horário de início | público |
| `/actuator/metrics` | Requisições ativas e totais, contagem por classe de status, por rota com `count`, `errors`, `avgMs` e `maxMs`, e memória da JVM | `grox.management.metricsAccess` |
| `/actuator/prometheus` | As mesmas métricas no formato de exposição do Prometheus | idem |

```yaml
grox:
  management:
    enabled: true             # padrão
    basePath: /actuator       # padrão
    metricsAccess: [ROLE_ADMIN]   # padrão com segurança ativa
```

- Cada módulo contribui com as suas verificações no `/health`: o GORM com `db`, a sessão no Redis com `redis`.
- Fora de development, o `/health` omite a mensagem da falha (só o status), para não expor detalhes do banco.
- As rotas das métricas são `controller.action` (ou o nome da rota no `grox-dsl`), `static`, `management` e `security`.
- O formato é próprio, não o do Micrometer.
- O acesso às métricas é conferido mesmo em cadeias do `chainMap` sem autorização.

Exemplo de coleta pelo Prometheus:

```yaml
scrape_configs:
  - job_name: app
    metrics_path: /actuator/prometheus
    basic_auth: {username: monitor, password: '...'}   # com useBasicAuth e um usuário ROLE_ADMIN
    static_configs: [{targets: ['app:8080']}]
```

## Access log

```yaml
grox:
  accessLog:
    enabled: true      # padrão false
    format: text       # ou json
```

Uma linha por requisição no logger `grox.access`:

```
127.0.0.1 admin "GET /usuario/index" 200 12ms usuario.index
```

Com `format: json`, uma linha JSON por requisição. O IP é o do cliente quando a requisição vem de um proxy confiável (`grox.http.trustedProxies`), e o usuário vem de `request.userPrincipal`.

## Logs

O Grox usa SLF4J; a aplicação escolhe a implementação (`slf4j-simple`, Logback...). Loggers úteis:

| Logger | |
|---|---|
| `grox.access` | Access log |
| `org.grox.plugins.console.WebConsole` | Execuções do console |
| `org.grox` em debug | Actions registradas, GSPs compiladas |

`dataSource.logSql: true` (e `formatSql`) mostra o SQL. A subida registra o tempo de cada fase (`Subida por fase`).

## Desligamento gracioso

O `close()` da aplicação, chamado também pelo shutdown hook no `SIGTERM`, faz em ordem:

1. muda o `/health` para `OUT_OF_SERVICE` (o balanceador para de mandar tráfego);
2. para de aceitar conexões;
3. espera as requisições em andamento **terminarem de ser escritas**, até `grox.shutdown.timeout` (padrão 30 s); nesse período as respostas saem com `Connection: close`;
4. encerra jobs, `BootStrap.destroy`, atores, services, sessões e banco.

Em Kubernetes, deixe o `terminationGracePeriodSeconds` maior que `grox.shutdown.timeout`.

## Subida rápida

### Desenvolvimento

O `gradle run` usa JIT só C1 (`-XX:TieredStopAtLevel=1`): a subida fica de 25% a 40% mais rápida. `grox { fastDevStartup = false }` no `build.gradle` desliga (por exemplo, para medir desempenho).

O Hibernate e os metadados das domain classes sobem em paralelo com o resto (`grox.startup.parallel`, padrão `true`), e só as entidades alvo de many-to-one ganham proxy (`grox.data.proxies: auto`).

### Produção: AOT cache (JDK 25+)

```bash
./gradlew installDist aotCache
```

O `aotCache` faz uma execução de treino da aplicação instalada: ela sobe numa porta aleatória, encerra ao ficar pronta e deixa o cache em `build/install/<app>/lib/grox.aot`.

- O script `bin/<app>` usa o cache quando o arquivo existe.
- O treino usa a configuração do ambiente, inclusive o banco: rode com as mesmas variáveis de produção.
- Qualquer `installDist` depois disso apaga o cache: rode `aotCache` por último.
- O cache vale para aquele JDK e aquele classpath. Com outro, a JVM avisa e sobe sem ele.
- Só funciona com classpath de JARs, por isso fica na distribuição e não no `run`.

## Diferenças para o Grails

| Recurso | Grox |
|---|---|
| Actuator | Formato próprio (não Micrometer); health, info, metrics e prometheus |
| Access log | `grox.accessLog` (no Grails, do container) |
| Desligamento | Gracioso por padrão, com `OUT_OF_SERVICE` |
| AOT cache | Tarefa `aotCache` *(extensão Grox)* |

## Veja também

- [Implantação em produção](../recomendacoes/implantacao.md)
- [Desempenho](../recomendacoes/desempenho.md)
- [Configuração](../referencia/configuracao.md)
- [Plugins e tarefas Gradle](../referencia/gradle.md)
- [Recarga em desenvolvimento](recarga.md)
