# Início rápido

Este capítulo coloca o Grox para rodar: primeiro os dois exemplos do repositório, depois uma aplicação mínima no estilo Grails e outra sem Grails.

## Requisitos

- **JDK:** qualquer JDK recente para rodar o Gradle. O JDK 27 usado pelo build é baixado pelo toolchain do Gradle.
- **Groovy:** não precisa instalar; o Groovy 6.0.0 vem pelas dependências.
- **Redis** (opcional): só para sessão no Redis e para os testes que o usam (`redis-server` no PATH; sem ele, esses testes são pulados).

O framework ainda não é publicado num repositório Maven. Por enquanto, uma aplicação é um subprojeto do build do Grox (incluída no `settings.gradle`) e depende de `project(':framework')` ou dos módulos `project(':framework:grox-*')`.

## Rodar os exemplos

```bash
./gradlew build                  # compila e roda todos os testes
./gradlew :sample-app:run        # exemplo no layout Grails: http://localhost:8080
./gradlew :sample-plain:run      # exemplo sem Grails: http://localhost:8080
```

`sample-app` (usuários `admin`/`admin`, `maria`/`maria`, `joao`/`joao`):

| Rota | O que mostra |
|---|---|
| `/` | Página inicial |
| `/usuario` | CRUD HTML com GORM, validação, paginação, upload |
| `/api/usuarios` | API REST em JSON |
| `/ola/Nome` | Texto simples |
| `/actuator/health` | Saúde da aplicação |

`sample-plain` (usuários `admin`/`admin`, `leitor`/`leitor`):

| Rota | O que mostra |
|---|---|
| `/livros` | Páginas GSP por rotas de closure |
| `/api/livros` | API JSON por rotas de anotação (`@Handler`) |
| `/sobre` | HTML pela DSL de HTML, com layout |
| `/app/info` | Rota da DSL de projeto |

No `run`, o ambiente é `development`: views, assets e estáticos recarregam ao salvar, e controllers, services e domain classes recarregam sem reiniciar a JVM.

## Primeira aplicação no estilo Grails

1. Crie o subprojeto e inclua no `settings.gradle`:

   ```groovy
   // settings.gradle
   include('minha-app')
   ```

2. `minha-app/build.gradle`:

   ```groovy
   plugins { id 'org.grox.app' }

   repositories { mavenCentral() }

   dependencies {
       implementation project(':framework')
       runtimeOnly libs.slf4j.simple
       runtimeOnly libs.h2
   }

   java { toolchain { languageVersion = JavaLanguageVersion.of(27) } }
   application { mainClass = 'livraria.Application' }
   ```

3. `grails-app/init/livraria/Application.groovy`:

   ```groovy
   package livraria

   import org.grox.GroxApp

   class Application {
       static void main(String[] args) { GroxApp.run(Application, args) }
   }
   ```

4. Uma domain class, um controller e uma view:

   ```groovy
   // grails-app/domain/livraria/Livro.groovy
   package livraria

   class Livro {
       String titulo
       Integer ano
       static constraints = { titulo blank: false; ano nullable: true }
   }
   ```

   ```groovy
   // grails-app/controllers/livraria/LivroController.groovy
   package livraria

   class LivroController {
       static allowedMethods = [save: 'POST']

       def index() { [livros: Livro.list(sort: 'titulo')] }

       def save() {
           def livro = new Livro(params)
           if (!livro.save()) return render(view: 'index', model: [livros: Livro.list(), novo: livro])
           flash.message = "Livro ${livro.titulo} criado"
           redirect action: 'index'
       }
   }
   ```

   ```html
   <!-- grails-app/views/livro/index.gsp -->
   <html><head><title>Livros</title></head>
   <body>
     <g:if test="${flash.message}"><p>${flash.message}</p></g:if>
     <ul><g:each in="${livros}" var="l"><li>${l.titulo}</li></g:each></ul>
     <g:form action="save">
       <g:textField name="titulo"/> <g:submitButton name="criar" value="Criar"/>
     </g:form>
   </body></html>
   ```

5. `grails-app/conf/application.yml`:

   ```yaml
   dataSource:
     dbCreate: create-drop
     url: jdbc:h2:mem:livraria;DB_CLOSE_DELAY=-1
   ```

6. Rode com `./gradlew :minha-app:run` e abra `http://localhost:8080/livro`. Sem classe `UrlMappings`, vale o mapeamento padrão `/$controller/$action?/$id?(.$format)?`. O `<g:form>` já envia o token CSRF.

## Primeira aplicação sem Grails

1. `minha-api/build.gradle`:

   ```groovy
   plugins { id 'groovy'; id 'application' }

   repositories { mavenCentral() }

   dependencies {
       implementation project(':framework:grox-core')
       implementation project(':framework:grox-gorm')
       implementation project(':framework:grox-dsl')
       runtimeOnly libs.slf4j.simple
       runtimeOnly libs.h2
   }

   java { toolchain { languageVersion = JavaLanguageVersion.of(27) } }
   tasks.withType(GroovyCompile).configureEach { groovyOptions.parameters = true }   // parâmetros por nome
   application { mainClass = 'api.Aplicacao' }
   tasks.named('run') { systemProperty 'grox.env.default', 'development' }
   ```

2. `src/main/groovy/api/Aplicacao.groovy`:

   ```groovy
   package api

   import grails.gorm.annotation.Entity
   import org.grox.GroxApp

   @Entity
   class Nota {
       String texto
       static constraints = { texto blank: false }
   }

   class Aplicacao {
       static app = {
           routes {
               get('/notas') { render json: Nota.list() }
               post('/notas') {
                   def nota = new Nota(request.JSON as Map)
                   nota.save() ? respond(nota, status: 201) : respond(nota.errors, status: 422)
               }
           }
       }

       static void main(String[] args) { GroxApp.run(Aplicacao, args) }
   }
   ```

3. `src/main/resources/application.yml`:

   ```yaml
   dataSource:
     dbCreate: create-drop
     url: jdbc:h2:mem:notas;DB_CLOSE_DELAY=-1
   ```

4. `./gradlew :minha-api:run`, depois:

   ```bash
   curl -X POST -H 'Content-Type: application/json' -d '{"texto":"oi"}' localhost:8080/notas
   curl localhost:8080/notas
   ```

O `grox-dsl` já traz o `grox-gsp` e o `grox-security` como dependências. A segurança fica desligada até existir `grails.plugin.springsecurity` na configuração ou um bloco `security { }` na DSL de projeto (veja [Segurança sem o plugin](../sem-grails/seguranca.md)).

## Veja também

- [Estrutura de uma aplicação](../recursos/estrutura.md)
- [Projeto do zero](../sem-grails/projeto-do-zero.md)
- [Tutorial: uma livraria completa](../sem-grails/tutorial.md)
- [Plugins e tarefas Gradle](../referencia/gradle.md)
