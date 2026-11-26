# Jobs agendados

Este capítulo cobre os jobs, o substituto do plugin Quartz do Grails: classes `*Job` com `static triggers` em cron ou intervalo fixo, executadas em virtual threads pelo agendador próprio do Grox.

O Quartz não é usado: o uso real dos projetos era `cron` e `simple`, e um `ScheduledExecutorService` com virtual threads resolve sem a dependência nem o seu modelo de threads.

## Um job

```groovy
// grails-app/jobs/app/ContasReceberJob.groovy
class ContasReceberJob {
    def mailWriterService                 // injeção por nome, como nos services

    static concurrent = false             // não dispara de novo enquanto a execução anterior roda

    static triggers = {
        cron name: 'contasReceber', cronExpression: '0 0 7 * * ?'        // seg min hora dia mês dia-da-semana
        simple name: 'fila', repeatInterval: 60000L, startDelay: 5000L   // a cada 60 s, começando em 5 s
    }

    def execute() {
        log.info 'enviando cobranças'
        mailWriterService.enviarCobrancas()
    }
}
```

- As classes ficam em `grails-app/jobs`, terminam em `Job` e ganham `log` e injeção por nome.
- `static triggers` aceita vários `cron` e `simple`.
- `static concurrent = false` impede execuções sobrepostas do mesmo job.

## Cron

O formato é o do Quartz, com segundos:

```
segundo minuto hora dia-do-mês mês dia-da-semana
0       0      7    *           *   ?
```

Aceita `*`, `?`, listas (`1,15`), intervalos (`1-5`), passos (`0/15`) e nomes (`MON-FRI`, `JAN`). `L`, `W` e `#` não são suportados.

| Expressão | Quando |
|---|---|
| `0 0 7 * * ?` | Todo dia às 7h |
| `0 0/15 * * * ?` | A cada 15 minutos |
| `0 30 8 ? * MON-FRI` | Dias úteis às 8h30 |
| `0 0 0 1 * ?` | Dia 1 de cada mês |

## Execução

- Cada execução roda numa **virtual thread**, com sessão de dados disponível (o GORM funciona direto).
- Não há usuário logado: para dados multi-tenant, use `Tenants.withId`:

  ```groovy
  def execute() {
      Empresa.list().each { e ->
          Tenants.withId(e.tenantId) { processar() }
      }
  }
  ```

- Os jobs começam **depois do BootStrap**.
- Uma exceção vai para o log e não cancela os próximos disparos.

## Ligar e desligar

```yaml
grox:
  jobs:
    enabled: false     # padrão true
```

`quartz.autoStartup: false` (ou `grails.plugin.quartz.autoStartup`), a chave do plugin, também desliga. É comum rodar os jobs só numa instância dedicada (como o `app-c-jobs` dos candidatos) e subir as demais com os jobs desligados.

## Telas de status

Como no plugin Quartz, cada job é um bean com o nome completo da classe, injetado mesmo com o agendador desligado, e `grailsApplication.getArtefacts(JobArtefactHandler.TYPE)` devolve um `GrailsJobClass` por job. Uma tela lista os jobs com o agendamento e executa um deles à mão:

```groovy
import grails.plugins.quartz.GrailsJobClass
import grails.plugins.quartz.JobArtefactHandler

class JobExecutionController {
    def jobs() {
        def classes = grailsApplication.getArtefacts(JobArtefactHandler.TYPE) as List<GrailsJobClass>
        [jobs: classes.collect { GrailsJobClass job ->
            [nome: job.shortName,
             agenda: job.triggers.values()*.triggerAttributes*.cronExpression.findAll().join(', ')]
        }]
    }

    def exec() {
        def job = (grailsApplication.getArtefacts(JobArtefactHandler.TYPE) as List<GrailsJobClass>).find { it.shortName == params.job }
        grailsApplication.mainContext.getBean(job.fullName).execute()   // o bean, com as dependências injetadas
        redirect action: 'jobs'
    }
}
```

O `GrailsJobClass` traz `shortName`, `fullName`, `group`, `concurrent`, `sessionRequired`, `enabled` (`static jobEnabled`), `description` e `triggers`: o mapa nome do trigger → objeto com `triggerAttributes` (`name`, `cronExpression` ou `repeatInterval`...). Um trigger sem `name` recebe o nome completo do job seguido de um contador, como no plugin.

`job.triggers()`, no próprio job, devolve o último trigger declarado (um `Expando` com `triggerAttributes`).

## Disparar na hora

Em testes e no console web, o agendador dispara um job fora do horário, pelo nome do trigger ou da classe:

```groovy
runtime.module(org.grox.plugins.quartz.JobsModule).jobs.triggerNow('ContasReceberJob')
```

## Recarga

Em desenvolvimento, jobs estão na camada recarregável: salvar um job troca a classe sem reiniciar (ver [Recarga em desenvolvimento](recarga.md)).

## Diferenças para o plugin Quartz

| Recurso | Grox |
|---|---|
| `cron` (sem `L`, `W`, `#`) e `simple` | ✅ |
| `static concurrent` | ✅ |
| Jobs como beans (nome completo da classe) e `GrailsJobClass` em `getArtefacts(JobArtefactHandler.TYPE)` | ✅ |
| Trigger `custom` | ❌ |
| `JobExecutionContext` como argumento de `execute` | ❌ |
| Clustering (JDBC job store) | ❌ |
| `MyJob.triggerNow()` estático e agendamento dinâmico pela API do Quartz | ❌ (há o `triggerNow(nome)` do agendador) |

## Veja também

- [Services e injeção de dependências](services.md)
- [Multi-tenancy](multi-tenancy.md)
- [Plugins reimplementados](plugins.md)
- [Configuração](../referencia/configuracao.md)
