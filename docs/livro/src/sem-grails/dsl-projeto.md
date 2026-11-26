# DSL de projeto

A DSL de projeto declara a aplicação num lugar só: configuração padrão, segurança, rotas, beans e dados iniciais. Ela fica na classe passada a `GroxApp.run`, num closure estático `app`, ou num script com `@BaseScript GroxScript`. Este capítulo descreve os cinco blocos, a forma de script e a precedência sobre os arquivos de configuração. *(extensão Grox)*

Os módulos continuam escolhidos pelas dependências do build: a DSL não liga nem desliga módulos.

## Na classe da aplicação

```groovy
package livraria

import org.grox.GroxApp

class Aplicacao {

    static app = {
        config {                                     // valores padrão
            grox.routes.poolSize = 3
            grox { i18n { defaultLocale = 'pt-BR' } }
        }
        security {                                   // ativa a segurança
            user 'admin', password: '{noop}admin', roles: ['ROLE_ADMIN']
            user 'leitor', password: '{noop}leitor', roles: ['ROLE_USER']
            rule '/api/**', 'ROLE_ADMIN'
            rule '/relatorios/**', httpMethod: 'GET', 'isAuthenticated()'
            useBasicAuth = true                      // demais chaves de grails.plugin.springsecurity
            stateless = ['/api/**']
        }
        routes {
            get('/app/info', access: 'permitAll') {
                render json: [relogio: relogio.zone.id, livros: livroService.todos().size(),
                              poolSize: config.getProperty('grox.routes.poolSize', Integer)]
            }
            group('/admin', access: 'ROLE_ADMIN') {
                get('/painel') { render { h1 'Painel' } }
            }
            handlers LivrosApi                       // classes @Handler
            error('/app/**') { Throwable e -> render status: 503, json: [erro: e.message] }
        }
        beans {
            relogio = java.time.Clock.systemUTC()    // instância pronta
        }
        bootstrap {                                  // dados iniciais
            init { environments { development { livroService.salvar(titulo: 'Dom Casmurro') } } }
            destroy { log.info 'encerrando' }
        }
    }

    static void main(String[] args) {
        GroxApp.run(Aplicacao, args)
    }
}
```

O closure `app` é lido uma vez, na subida, antes de qualquer módulo ler a configuração.

## Os blocos

| Bloco | Comportamento |
|---|---|
| `config { }` | A mesma árvore do `application.yml`, com atribuições (`server.port = 8080`, `grox.routes.execution = 'actor'`) e blocos aninhados (`grox { routes { poolSize = 4 } }`). **Só preenche as chaves que os arquivos não definem** |
| `security { }` | Ativa a segurança. `user` e `rule` acrescentam usuários e regras de URL; as atribuições vão para `grails.plugin.springsecurity` como valores padrão |
| `routes { }` | As mesmas rotas de uma classe `Routes` (`get`, `post`, `put`, `patch`, `delete`, `group`, com as mesmas opções), mais `handlers X, Y` para registrar classes `@Handler` |
| `bootstrap { }` | `init { }` na subida e `destroy { }` no desligamento, como o `BootStrap` do Grails |
| `beans { }` | A sintaxe do `resources.groovy` (`nome(Classe)`, `nome(Classe, arg) { prop = ref('outro') }`) e também `nome = instancia` |

Os blocos podem se repetir: `routes { }` e `beans { }` se somam, e `config { }` e `security { }` mesclam na mesma árvore.

### `config { }`

```groovy
config {
    server.port = 8081
    grox.routes.execution = 'virtual'
    grox {
        routes { poolSize = 4 }
        session { timeout = 3600 }
    }
    dataSource {
        url = 'jdbc:h2:mem:livraria;DB_CLOSE_DELAY=-1'
        dbCreate = 'create-drop'
    }
}
```

Os valores são **padrões**. A precedência, da maior para a menor:

1. `-Dchave=valor`;
2. variável de ambiente (`server.port` → `SERVER_PORT`);
3. `runtime.groovy`;
4. `application.groovy`;
5. `application.yml`;
6. o `config { }` da DSL de projeto.

Placeholders `${NOME}` e `${NOME:padrão}` funcionam nos valores, como no yml. Assim o código traz padrões razoáveis, e cada ambiente ajusta pelos arquivos ou pelo ambiente.

### `security { }`

```groovy
security {
    user 'admin', password: '{noop}admin', roles: ['ROLE_ADMIN']
    user 'ana', password: '{bcrypt}$2a$10$...', roles: ['ROLE_USER']
    rule '/api/**', 'ROLE_ADMIN'                              // regra: caminho e acessos
    rule '/relatorios/**', httpMethod: 'GET', 'permitAll', 'isAuthenticated()'
    rejectIfNoRule = true
    useBasicAuth = true
    stateless = ['/api/**']
    rest.active = true
    rest.token.storage.jwt.secret = '${JWT_SECRET}'
}
```

| Instrução | Vai para |
|---|---|
| `user 'nome', password:, roles:` (também `enabled: false` e `tenantId:`) | Usuários em memória, somados aos `users` do yml |
| `rule 'padrão', 'acesso'...` (com `httpMethod:` opcional) | Regras de URL avaliadas **depois** das do `interceptUrlMap` e das `controllerAnnotations.staticRules` dos arquivos |
| atribuições (`useBasicAuth = true`, `csrf.enabled = false`...) | `grails.plugin.springsecurity.*`, como padrão (o yml vence) |

Use `{noop}` só em desenvolvimento. As chaves e o comportamento da segurança estão em [Segurança sem o plugin](seguranca.md).

### `routes { }`

As rotas do bloco formam um grupo com o nome da classe da aplicação em camelCase (`Aplicacao` → `aplicacao`), o "controller" dos links. O handler roda com o `RouteContext` como delegate, então enxerga `params`, `request`, `render`, `config` (a configuração da aplicação) e os services e beans pelo nome (`livroService`, `relogio`).

`access:` na rota ou no `group` vale como um `@Secured`, antes das regras de URL. O mesmo vale para `@Handler(access = ...)` e `@Get(access = ...)`.

`handlers LivrosApi, AutoresApi` registra classes `@Handler`; as do pacote da aplicação já são achadas pela varredura, e uma classe não é registrada duas vezes.

`before`, `after` e `error` na raiz deste bloco valem para **todas as rotas da aplicação**, inclusive as das classes `Routes` e `@Handler`; com um padrão (`before('/admin/**') { }`), só para as que casam. Ver [Interceptores](interceptores.md).

### `beans { }`

```groovy
beans {
    relogio = java.time.Clock.systemUTC()
    notificador(Notificador, 'https://api.exemplo.com') {
        timeout = 30
        livroService = ref('livroService')
    }
}
```

`nome(Classe)` usa o construtor sem argumentos, ou os argumentos dados. Para objetos criados por fábrica, como o `HttpClient`, use `nome = instancia`:

```groovy
beans {
    clienteHttp = java.net.http.HttpClient.newHttpClient()
}
```

- `ref('outro')` aponta para outro bean ou service; numa propriedade, pode ser um bean declarado depois.
- Os beans recebem injeção por nome, como os services, e ficam acessíveis pelo nome nos handlers.
- Um service com o mesmo nome prevalece sobre o bean.

O `config` do bloco `app` não é visível dentro das rotas: lá, `config` é a configuração da aplicação já mesclada.

### `bootstrap { }`

```groovy
bootstrap {
    init {
        if (Livro.count() == 0) livroService.salvar(titulo: 'Dom Casmurro', autor: 'Machado de Assis', ano: 1899)
        environments { development { livroService.salvar(titulo: 'Rascunho', autor: 'Teste', ano: 2000) } }
    }
    destroy { log.info 'encerrando' }
}
```

- `init` roda na subida, depois dos services e antes de o servidor aceitar requisições, com uma sessão de dados aberta. `destroy` roda no desligamento.
- Os closures enxergam os services e beans pelo nome, `config`, `log` e `environments { }` (o bloco do ambiente atual: `development`, `test`, `production` ou outro nome).
- Uma classe `BootStrap` no pacote da aplicação também funciona, como no Grails ([Dados com @Entity](dados.md#dados-iniciais)). O bloco da DSL roda antes dela.
- Na recarga leve nada roda de novo, porque os dados continuam.

## Como script

O mesmo pode ser escrito como script, com a base `org.grox.dsl.GroxScript`. Executar o script sobe a aplicação:

```groovy
// src/main/groovy/livraria/app.groovy
package livraria

@groovy.transform.BaseScript org.grox.dsl.GroxScript base

grox {
    config { grox.routes.execution = 'virtual' }
    routes {
        get('/') { render { h1 'Olá' } }
    }
}
```

```groovy
// build.gradle
application { mainClass = 'livraria.app' }
```

- O pacote do script é o varrido (domain classes, services, rotas).
- Na subida, o Grox roda o script de novo só para ler o `grox { }`, sem subir outra vez. Evite efeitos colaterais fora do bloco `grox { }`.
- Os argumentos da linha de comando chegam ao `GroxApp.run`.

## Veja também

- [Rotas por closure](rotas-closure.md)
- [Segurança sem o plugin](seguranca.md)
- [Configuração](../referencia/configuracao.md)
- [Beans e resources.groovy](../recursos/beans.md)
