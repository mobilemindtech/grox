minha ideia é a seguinte, que fazer um protótipo de framework web leve baseado nos atores do groovy-6. Quero que ele seja compativel com o grails, ou seja, dado controllers, services, models e view (gsp) ele consiga fazer essas peças funcionarem. Isso é viável?

**Sim, a ideia é viável e muito interessante para um protótipo**, mas exige decisões claras de arquitetura.

Como o Grails é uma coleção de convenções fortemente acopladas ao ecossistema do **Spring (Spring Web MVC, Spring Beans)** e ao **GORM**, o segredo para o seu framework "leve" não é reescrever o Grails do zero, mas **emular a sua interface pública (as anotações, convenções e assinaturas)** usando um modelo de atores por baixo.

---

## 1. Como a Arquitetura de Atores se Encaixa

No Grails tradicional, cada requisição HTTP bloqueia uma *thread* da *pool* do Servlet Container (Tomcat/Jetty) e chama métodos síncronos no Controller, Service e GORM.

No seu modelo baseado nos **GPars / Groovy Actors** (integrados/compatíveis com o Groovy 6):

```
                               ┌──────────────────────────────────────────────────────────┐
                               │                    POOL DE ATORES                        │
                               │                                                          │
 HTTP Request                 ┌┴───────────────┐     sendAndReceive     ┌────────────────┐│
────────────► [ HTTP Server ] │ Router Actor   ├───────────────────────►│ Controller     ││
              (Netty/Helidon) └────────────────┘                        │ Actor          ││
                                                                        └───────┬────────┘│
                                                                                │         │
                                                                async call      │         │
                                                                                ▼         │
                                                                        ┌────────────────┐│
                                                                        │ Service        ││
                                                                        │ Actor          ││
                                                                        └────────────────┘│
                                                                                          │
                               ┌──────────────────────────────────────────────────────────┘
                               ▼
                        [ GSP Engine ] ──► HTTP Response

```

1. **Router Actor:** Recebe o evento da requisição HTTP (via Netty, Undertow ou Javalin leve) e repassa como uma mensagem para o ator responsável.
2. **Controller Actor:** Em vez de ser uma classe gerenciada pelo Spring, cada Controller é um Ator que processa mensagens de requisição e responde com uma estrutura de modelo/visão.
   3. **Service Actor:** Services viram atores que processam tarefas assíncronas ou isoladas (stateful ou stateless).

---

## 2. A Estratégia para a Compatibilidade com o Grails

Para que um projeto existente do Grails (ou escrito na sintaxe dele) rode no seu framework, você precisará tratar cada uma das quatro peças:

### A. Controllers (Atores com DSL)

No Grails, um controller expõe ações que retornam um `Map`, invocam `redirect()` ou chamam `render()`.

* **Como fazer no seu framework:** Crie uma classe base (ou trait) `GrailsControllerActor` que intercepta chamadas de ação e transforma a execução de um método de Controller em uma mensagem do Ator.

```groovy
// Código no estilo Grails do usuário
class UsuarioController {
    def usuarioService

    def index() {
        def usuarios = usuarioService.listarTodos()
        return [usuarios: usuarios] // Ator responde com o Map do modelo
    }
}

```

### B. Views (GSP)

Conforme discutido anteriormente, para fazer o GSP rodar sem o Spring MVC completo do Grails, você precisará inicializar o `GroovyPagesTemplateEngine` standalone e alimentá-lo com o `Map` retornado pelo **Controller Actor**.

### C. Services (Injeção de Dependências & Transações)

No Grails, os serviços têm injeção automática e suporte a `@Transactional`.

* No seu framework, a injeção de dependência pode ser feita via metamemória no Groovy (AST Transformation ou inicializador de reflexão que injeta a referência dos Atores dos serviços nos Controllers na subida da aplicação).

### D. Models (GORM)

O GORM já é **100% compatível fora do Grails** (`gorm-hibernate5` ou `gorm-mongodb` standalone).

* Você só precisa rodar o `HibernateDatastore` durante o *bootstrap* do seu framework para registrar as classes `@Entity`.
* **Cuidado importante:** O Hibernate depende do contexto de *Session* por Thread (`ThreadLocal`). Como Atores mudam de thread constantemente durante o processamento de mensagens, você precisará garantir que a sessão do GORM seja aberta e fechada dentro do bloco de execução do ator:
```groovy
Pessoa.withTransaction {
    // Código do GORM aqui dentro do Ator
}

```



---

## 3. Principais Desafios do Protótipo

| Componente | Desafio Técnico | Como Mitigar no Protótipo |
| --- | --- | --- |
| **GORM + Atores** | O GORM é síncrono e usa `ThreadLocal` para sessões do Hibernate. | Envolva chamadas ao banco com `withTransaction` ou isole a persistência em **Atores de E/S dedicados** (Worker Actors). |
| **GSP Standalone** | Dependência leve de Mocks do Spring Web para resolver tags. | Crie um *wrapper* utilitário que simula a requisição ao passar o modelo para o `GroovyPagesTemplateEngine`. |
| **Metaprogramação (AST)** | Grails injeta dynamic finders (`findBy...`) via AST em tempo de compilação. | Use o próprio jar do GORM (`grails-datastore-gorm`) para não ter que reescrever a metaprogramação. |

---

## 4. Roteiro Sugerido para Construção do Protótipo

Para validar a ideia rapidamente em **3 passos**:

1. **Passo 1 (HTTP + Ator + GSP):**
* Suba um servidor HTTP ultra-leve (como Netty ou Javalin).
* Faça a requisição acionar um Ator Groovy que carrega um arquivo `.gsp` simples via `GroovyPagesTemplateEngine` e devolve o HTML.


2. **Passo 2 (Controller + Service):**
* Crie uma convenção para mapear URLs para métodos de um Ator (`/usuario/index` -> `UsuarioController.index()`).
* Faça o Controller chamar um `ServiceActor` via mensagem (`sendAndReceive`) para buscar dados antes de renderizar a tela.


3. **Passo 3 (GORM Standalone):**
* Inicialize o `HibernateDatastore` no boot da aplicação e persista um objeto dentro da ação do Controller.



Se você limitar o escopo inicial às convenções fundamentais (Roteamento simples, GORM Standalone, Controllers que retornam `Map` e GSP para renderizar), **é um protótipo totalmente viável e com excelente desempenho assíncrono.**

Vamos usar a ultima versão do SiteMesh  e ultima versão do hibernate. Como servidor http vamos usar o netty.