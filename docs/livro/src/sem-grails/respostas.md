# Respostas: render, respond e redirect

As rotas do `grox-dsl`, por closure ou por anotação, respondem com a mesma API, no estilo dos controllers do Grails: `render`, `respond` e `redirect`. Este capítulo descreve cada forma, o modelo devolvido sem `render`, a leitura da requisição (parâmetros, JSON, sessão, flash, locale) e os status de erro.

## `render`

| Forma | Resposta |
|---|---|
| `render view: 'livros/index', model: [livros: l]` | A GSP `views/livros/index.gsp` com o modelo, decorada pelo layout. A barra inicial é opcional |
| `render json: obj` | `application/json` com o objeto serializado |
| `render text: 'ok'` | `text/plain` |
| `render text: csv, contentType: 'text/csv'` | O texto com o tipo dado |
| `render 'ok'` | O mesmo que `render text: 'ok'` |
| `render status: 204` | Só o status |
| `render(layout: 'main') { h1 'Livros' }` | HTML montado pela [DSL de HTML](views.md#dsl-de-html), com o layout |
| `render { h1 'Olá' }` | HTML sem layout explícito (vale a escolha padrão de layout) |
| `render(contentType: 'application/json') { titulo 'X'; autor { nome 'Ana' } }` | JSON montado pelo `JsonBuilder` |
| `render(contentType: 'application/xml') { livro(id: 1) { titulo 'X' } }` | XML pelo `MarkupBuilder` puro |

- `status:` combina com qualquer forma: `render view: 'livros/index', model: m, status: 422`.
- `render` sem `view`, `json`, `text` nem `status` lança `IllegalArgumentException`.
- **JSON das entidades:** domain classes saem rasas (as propriedades, mais `{id}` nas associações). Erros de validação (`livro.errors`) saem como `{"errors": [{"object", "field", "rejected-value", "message"}]}`, com as mensagens traduzidas. O `</` é escapado, então o JSON pode ir dentro de `<script>`.
- **Views:** os nomes são relativos a `views/` no classpath (`src/main/resources/views`). Ver [Views e HTML](views.md).

## Modelo devolvido (`view:` da rota)

Nas rotas por closure com a opção `view:`, um `Map` devolvido pelo handler, sem `render`, vira o modelo dessa view:

```groovy
get('/livros/{id}', name: 'show', view: 'livros/show') {
    Livro livro = livroService.buscar(params.long('id'))
    if (!livro) return render(status: 404)
    [livro: livro]
}
```

Se o handler já respondeu (`render`, `respond`, `redirect`), o retorno é ignorado. Sem `view:` e sem resposta, sai uma resposta vazia com o status corrente.

## `respond`

`respond` decide entre HTML e JSON pelo que o cliente pede:

```groovy
respond livro                                   // JSON
respond livro, status: 201                      // JSON, 201
respond livro.errors, status: 422               // {"errors": [...]}, 422
respond livros, view: 'livros/index'            // HTML se o cliente pede HTML, senão JSON
respond livro, view: 'livros/show', model: [livro: livro, autores: autores]
respond null                                    // 404
```

| Situação | Resposta |
|---|---|
| Valor `null` (sem `status:`) | `404` |
| Com `view:` e o cliente prefere HTML | A view; o modelo é o `model:` dado ou, sem ele, `[livro: x]` para um objeto e `[livroList: [...]]` para uma coleção (nome da classe do primeiro elemento; `list` se a coleção estiver vazia) |
| Demais casos | `render json: valor` |

"Prefere HTML" quer dizer: o caminho não termina em `.json` e o `Accept` pede `text/html`, ou não há `Accept`. Clientes de API que mandam `Accept: application/json` recebem JSON.

## `redirect`

| Forma | Destino |
|---|---|
| `redirect '/livros'` | A URL dada (302) |
| `redirect url: 'https://exemplo.com'` ou `redirect uri: '/livros'` | Idem |
| `redirect controller: 'rotas', action: 'show', id: 7` | O link reverso da rota `show` da classe `Rotas` (`/livros/7`) |
| `redirect url: '/novo', permanent: true` | 301 (a forma curta `redirect '/novo'` é sempre 302) |

O "controller" de uma rota é o nome da classe (`Rotas` → `rotas`, `LivrosApi` → `livrosApi`; as rotas da DSL de projeto usam o nome da classe da aplicação) e o "action" é a opção `name:` ou o nome do método.

## Lendo a requisição

```groovy
post('/livros') {
    String titulo = params.titulo                      // formulário ou query
    int ano = params.int('ano', 0)
    List<Long> ids = params.list('ids')*.toLong()
    def arquivo = request.getFile('capa')              // multipart: originalFilename, bytes, contentType...
    String token = request.getHeader('X-Token')
    ...
}

post('/api/livros') {
    Map dados = request.JSON as Map                    // corpo JSON (ou request.json)
    ...
}
```

| Fonte | Acesso |
|---|---|
| Query, formulário, arquivos, variáveis do caminho | `params` (o caminho vence o formulário, que vence a query). Conversões: `int`, `long`, `double`, `boolean`, `list`, `date(nome, formato)`. Chaves com ponto têm acesso aninhado (`params.endereco.cidade`) |
| Corpo JSON | `request.JSON` ou `request.json` (Map ou List, parseado uma vez) |
| Cabeçalhos, cookies, método, caminho | `request.getHeader('X')`, `request.cookies`, `request.method`, `request.path` |
| Usuário autenticado | `request.userPrincipal` (`name`, `authorities`) |
| Upload | `request.getFile('campo')`, `request.getFiles('campo')`, ou o próprio `params.campo`. Limite: `grox.http.maxContentLength` (acima, `413`) |

## Sessão, flash e locale

```groovy
post('/carrinho') {
    (session.carrinho = session.carrinho ?: []) << params.long('livro')
    flash.message = 'Livro adicionado'
    redirect '/carrinho'
}
```

- `session` é a sessão HTTP (cookie `GROXSESSIONID`, memória ou Redis, `grox.session.*`). No Redis, todo valor precisa ser `Serializable`; guarde ids, não entidades.
- `flash` dura até a próxima requisição. Nas GSPs: `${flash.message}`.
- `locale` vem de `?lang=en` (guardado na sessão), da sessão, do `Accept-Language` ou de `grox.i18n.defaultLocale`. As mensagens ficam em `messages*.properties` na raiz do classpath (`src/main/resources`).

## Status de erro

| Status | Quando |
|---|---|
| `404` | Nenhuma rota casa com o caminho; `respond null`; `render status: 404` |
| `405` | O caminho existe, mas não com esse método HTTP |
| `422` | Convenção para erros de validação: `respond livro.errors, status: 422` ou `render view: ..., status: 422` |
| `401`/`403` | Segurança: sem login (ou sem token) e sem permissão |
| `400` | Caminho com `.` ou `..`, charset ou multipart inválido |
| `413` | Corpo maior que `grox.http.maxContentLength` |
| `500` | Exceção no handler. Em development a página de erro mostra o stack trace; fora dele, só o status |

As páginas de erro padrão saem em HTML ou JSON, conforme o `Accept`. Fora de development, a mensagem da exceção não aparece na resposta (só no log).

## Veja também

- [Rotas por closure](rotas-closure.md)
- [Rotas por anotação](rotas-anotacao.md)
- [Views e HTML](views.md)
- [Sessão HTTP](../recursos/sessao.md)
- [Internacionalização](../recursos/i18n.md)
