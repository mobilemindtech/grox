package bench.livros

class UrlMappings {
    static mappings = {
        "/"(redirect: '/livros')
        "/ola/$nome"(controller: 'ola', action: 'index')
        "/livros"(controller: 'livro', action: 'index')
        "/api/livros/$id"(controller: 'livroApi', action: 'show')
        "404"(view: '/notFound')
        "500"(view: '/error')
    }
}
