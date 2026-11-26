# UrlMappings

Este capítulo descreve como as URLs chegam aos controllers: a classe `UrlMappings`, a sintaxe dos padrões, verbos HTTP, rotas REST, mapeamentos de erro, prioridade entre mapeamentos e a geração reversa de links.

## A classe `UrlMappings`

Fica em `grails-app/controllers/<pkg>/UrlMappings.groovy` (ou sem pacote, em apps antigas):

```groovy
class UrlMappings {
    static mappings = {
        "/$controller/$action?/$id?(.$format)?" {
            constraints { id(matches: /\d+/) }
        }
        "/"(controller: 'home')                        // ou (view: '/index')
        "/ola/$nome?"(controller: 'home', action: 'ola')
        "/antigo"(redirect: '/novo')
        "/ajuda"(controller: 'site', action: 'pagina') { pagina = 'ajuda' }   // params extras
        "/livros/$id"(controller: 'livro') { action = [GET: 'show', DELETE: 'delete'] }
        post "/login"(controller: 'auth', action: 'entrar')
        "/relatorio"(controller: 'relatorio', action: 'gerar', method: 'POST')
        group "/api", {
            "/livros"(resources: 'livro', format: 'json')   // rotas REST + params.format fixo
        }
        "/arquivos/$path**"(controller: 'arquivo', action: 'baixar')
        "404"(controller: 'erro', action: 'naoEncontrado')
        "500"(controller: 'erro', action: 'interno')        // request.exception disponível
    }
}
```

Sem nenhuma classe `UrlMappings`, vale só o mapeamento padrão `/$controller/$action?/$id?(.$format)?`.

## Sintaxe dos padrões

| Elemento | Significado |
|---|---|
| `$nome` | Variável de um segmento, vira `params.nome` |
| `$nome?` | Variável opcional |
| `(.$format)?` | Extensão opcional (`/livro/show/1.json` → `params.format = 'json'`) |
| `**` | Qualquer quantidade de segmentos |
| `$path**` | Captura o resto do caminho em `params.path` |
| `$controller`, `$action`, `$id` | Escolhem o controller e a action pela URL |

## Opções de um mapeamento

| Opção | Efeito |
|---|---|
| `controller:`, `action:` | Destino |
| `view: '/index'` | Renderiza uma view sem controller |
| `redirect: '/novo'` | Redireciona |
| `method: 'POST'` ou `post "/x"(...)` (também `get`, `put`, `patch`, `delete`) | Restringe o verbo. Com outro verbo, a resposta é `405` se o mapeamento levaria a um controller e action existentes; senão, segue para os próximos e termina em `404` (um `GET /nao-existe` com os mapeamentos REST genéricos `delete "/$controller/$id"`... dá `404`) |
| `action = [GET: 'show', DELETE: 'delete']` | Action por verbo |
| `format: 'json'` | Fixa `params.format` |
| `{ pagina = 'ajuda' }` | Parâmetros extras |
| `constraints { id(matches: /\d+/) }` | Restrição por regex (outras constraints não existem) |
| `group "/api", { }` | Prefixo comum |
| `resources: 'livro'` | Rotas REST (abaixo) |

## Rotas REST com `resources:`

`"/livros"(resources: 'livro')` gera:

| Método e caminho | Action |
|---|---|
| `GET /livros` | `index` |
| `GET /livros/create` | `create` |
| `POST /livros` | `save` |
| `GET /livros/$id` | `show` |
| `GET /livros/$id/edit` | `edit` |
| `PUT /livros/$id` | `update` |
| `PATCH /livros/$id` | `patch` |
| `DELETE /livros/$id` | `delete` |

Todas aceitam `.json`. `resource:` (singular) e recursos aninhados não existem.

## Mapeamentos de erro

`"404"`, `"500"`, `"403"` e outros códigos apontam para um controller ou view. Um `sendError(404)`, uma exceção (500) ou um acesso negado (403, inclusive tenant ausente) redespacha a requisição para o destino. No `500`, `request.exception` traz a exceção. `exception:` (mapear por tipo de exceção) não existe.

## Prioridade

Mapeamentos mais específicos (mais texto literal) têm prioridade sobre os genéricos, independentemente da ordem no arquivo.

## Métodos simulados

Formulários HTML só enviam GET e POST. Para PUT, PATCH e DELETE, use o campo `_method` (o `<g:form method="PUT">` já o gera, inclusive em multipart) ou o cabeçalho `X-HTTP-Method-Override`.

## Links reversos

`createLink`, `<g:link>`, `<g:form>` e `redirect` geram URLs a partir dos mapeamentos:

```groovy
createLink(controller: 'livro', action: 'show', id: 3)     // /livro/show/3
createLink(controller: 'livro', action: 'show', id: 3, absolute: true)   // usa grails.serverURL
createLink(resource: livro, action: 'update')                // /livro/update/3
```

Um mapeamento que fixa parâmetros (como `format: 'json'`) só é usado no reverso se o link pedir o mesmo valor.

## Chamando métodos da própria classe

O closure `mappings` pode chamar métodos estáticos da própria classe `UrlMappings` (como um `findControllersInPackage` de apps antigas): eles são executados, não viram rotas.

## Caminhos inseguros

Caminhos com segmentos `.` ou `..` recebem `400` antes de qualquer roteamento. `/admin.json` e `/admin/` roteiam como `/admin`.

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| Mapeamentos nomeados (`name x: ...`), `uri:` | Ausentes |
| `resource:` singular, aninhamento | Ausentes |
| `exception:` | Ausente |
| Constraints além de `matches` | Ausentes |

## Veja também

- [Controllers](controllers.md)
- [Rotas por closure](../sem-grails/rotas-closure.md)
- [Segurança: regras de acesso](seguranca-regras.md)
