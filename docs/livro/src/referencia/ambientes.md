# Ambientes

Como no Grails, o Grox tem três ambientes padrão: `development`, `test` e `production`. O ambiente escolhe o bloco de `environments` da configuração e muda alguns padrões do framework, como o reload de views e o console.

## Como o ambiente é escolhido

Na ordem, vale o primeiro definido:

1. `-Dgrails.env`
2. `-Dgrox.env`
3. variável `GRAILS_ENV`
4. variável `GROX_ENV`
5. `-Dgrox.env.default`
6. `production`

`dev` e `prod` são aceitos como abreviações. Qualquer outro nome vira um ambiente personalizado (por exemplo `homologacao`), com o seu bloco em `environments`.

O padrão é **`production`**, como no Grails: o artefato empacotado (`installDist`, jar) sobe em produção sem configuração. O plugin `org.grox.app` define `-Dgrox.env.default=development` na tarefa `run` e `test` nas tarefas de teste. A subida registra no log quando o ambiente veio do padrão.

## Configuração por ambiente

```yaml
server:
  port: 8080
---
environments:
  development:
    dataSource:
      dbCreate: create-drop
      url: jdbc:h2:mem:dev;DB_CLOSE_DELAY=-1
  test:
    server:
      port: 0
  production:
    dataSource:
      dbCreate: none
      url: "${DB_URL}"
```

Nos arquivos Groovy (`application.groovy`, `runtime.groovy`):

```groovy
environments {
    development { grox.console.enabled = true }
}
```

No `BootStrap`:

```groovy
def init = { servletContext ->
    environments {
        development { criarDadosDeExemplo() }
    }
}
```

## O que muda em cada ambiente

| Comportamento | development | test | production |
|---|---|---|---|
| `grox.gsp.reload` (views, estáticos e assets dos diretórios de fontes) | ligado | desligado | desligado |
| Recarga de classes no `run` | ligada | | |
| Console web (`grox.console.enabled`) | ligado | desligado | desligado (ligar gera aviso no log) |
| Página de erro padrão | com mensagem e stack trace | só o status | só o status |
| Mensagem da falha no `/health` | mostrada | omitida | omitida |
| GSPs | compiladas sob demanda, recompiladas ao mudar | pré-compiladas, se houver | pré-compiladas, se houver |

Fora de development, as páginas e o JSON de erro padrão não mostram a mensagem da exceção (SQL, tabelas, caminhos), e o `/health` omite a mensagem da falha do banco ou do Redis. As duas continuam no log.

## Lendo o ambiente no código

| API | Onde |
|---|---|
| `grails.util.Environment.current`, `Environment.currentName`, `Environment.developmentMode`, `Environment.executeForCurrentEnvironment { }` | Código no estilo Grails |
| `org.grox.core.Env.currentName`, `Env.development`, `Env.defaulted` | Qualquer módulo, inclusive apps sem Grails |
| `groxApplication.environmentName` | Pelo bean `groxApplication` |
| `<g:if env="development">` | Nas GSPs |

O `grails.util.Environment` delega para o `org.grox.core.Env`.

## Veja também

- [Configuração](configuracao.md)
- [Plugins e tarefas Gradle](gradle.md)
- [Implantação em produção](../recomendacoes/implantacao.md)
