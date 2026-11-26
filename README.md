# Grox

[![CI](https://github.com/mobilemindtech/grox/actions/workflows/ci.yml/badge.svg)](https://github.com/mobilemindtech/grox/actions/workflows/ci.yml)
[![Documentação](https://img.shields.io/badge/docs-livro-blue)](https://mobilemindtech.github.io/grox/)
[![Licença](https://img.shields.io/badge/licen%C3%A7a-Apache--2.0-green)](LICENSE)

**A API do Grails, sem o Spring, sobre os atores do Groovy 6.**

O seu código Grails (controllers, services, domain classes, GSP e TagLibs) roda no Grox sem mudanças, sobre Netty e Hibernate 7. Ou use só o núcleo, com rotas em closures, sem o layout `grails-app/`.

## Mais rápido

A mesma aplicação (o mesmo `grails-app/`) no Grails 8 e no Grox, com 64 conexões:

| | Grails 8 | Grox | Grox sem a camada Grails |
|---|---:|---:|---:|
| Texto | 9.478 req/s | 18.474 req/s (**2×**) | 24.148 req/s (**2,5×**) |
| Página GSP com layout | 1.182 req/s | 1.676 req/s (**1,4×**) | 2.403 req/s (**2×**) |
| JSON | 10.695 req/s | 12.612 req/s (**1,2×**) | 13.627 req/s (**1,3×**) |
| Subida | 18 a 23 s | **8 s** | **7 a 8 s** |

Em resumo: sem Spring nem servlet container; cada controller e service é um ator; o I/O fica no event loop do Netty. A metodologia e os números completos estão em [`docs/benchmark.md`](docs/benchmark.md).

## Código Grails, como sempre

```groovy
// grails-app/domain/livraria/Livro.groovy
class Livro {
    String titulo
    Integer ano
    static belongsTo = [autor: Autor]
    static constraints = { titulo blank: false }
}

// grails-app/services/livraria/LivroService.groovy
@Transactional
class LivroService {
    List<Livro> recentes(String nome) {
        Livro.where { ano >= 2000 && autor.nome == nome }.list(sort: 'titulo')
    }
}

// grails-app/controllers/livraria/LivroController.groovy
class LivroController {
    LivroService livroService

    def index(String nome) {
        respond livroService.recentes(nome)        // HTML (GSP) ou JSON, como no Grails
    }
}
```

## Ou sem Grails

```groovy
@Entity
class Livro {
    String titulo
    String autor
}

class Aplicacao {
    static app = {
        security {
            user 'admin', password: '{noop}admin', roles: ['ROLE_ADMIN']
        }
        routes {
            get('/ola/{nome}', access: 'permitAll') { render text: "Olá, ${params.nome}!" }
            get('/livros') { render view: 'livros/index', model: [livros: Livro.list()] }
            post('/api/livros', access: 'ROLE_ADMIN') {
                def livro = new Livro(request.json).save()
                respond livro, status: 201
            }
        }
    }

    static void main(String[] args) { GroxApp.run(Aplicacao, args) }
}
```

## O que vem junto

- **GORM** sobre Hibernate 7: dynamic finders, `where { }` conferido na compilação, criteria, HQL, Data Services e multi-tenancy
- **GSP** com layouts SiteMesh, TagLibs e asset pipeline; também views em Pug e Markdown
- **Segurança** no estilo spring-security (form login, JWT, `@Secured`, CSRF) com a mesma configuração dos plugins
- **Recarga** de controllers, services e views em desenvolvimento, sem reiniciar a JVM
- Plugins do Grails mais usados: quartz, redis, rabbitmq, console e excel-export

## Começar

```bash
git clone https://github.com/mobilemindtech/grox.git && cd grox
./gradlew :sample-app:run        # app no estilo Grails: http://localhost:8080 (admin/admin)
./gradlew :sample-plain:run      # app sem Grails
```

Precisa só de um JDK recente; o Gradle baixa o JDK 27 e o Groovy 6.

📖 **Documentação:** https://mobilemindtech.github.io/grox/ (migração de apps Grails, uso sem Grails e referência).

> O Grox é um **protótipo**: já roda aplicações Grails 8 reais, mas a API ainda pode mudar. Veja a [matriz de compatibilidade](https://mobilemindtech.github.io/grox/referencia/compatibilidade.html).

## Licença

[Apache License 2.0](LICENSE)
