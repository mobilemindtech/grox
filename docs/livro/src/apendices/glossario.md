# Glossário

Termos usados no livro, em ordem alfabética.

| Termo | Significado |
|---|---|
| **Action** | Método público (ou propriedade closure) de um controller que atende uma requisição |
| **Ambiente** | `development`, `test`, `production` ou um nome próprio. Escolhe o bloco de `environments` e alguns padrões. Veja [Ambientes](../referencia/ambientes.md) |
| **AOT cache** | Cache de classes carregadas e linkadas do JDK 25+, gerado pela tarefa `aotCache`, que acelera a subida em produção |
| **Artefato** | Classe reconhecida pelo framework pelo diretório e pelo sufixo: controller, service, TagLib, domain class, interceptor, job, `UrlMappings`, `BootStrap` |
| **AST** | Transformação do compilador Groovy. O Grox aplica a API dos artefatos (traits) e reescreve os closures de `where { }` em tempo de compilação |
| **Ator** | Objeto do `groovy.concurrent` que processa mensagens uma por vez, numa virtual thread. Controllers e services-ator rodam em atores |
| **Binding** | Preenchimento de um objeto a partir de `params` ou do JSON, com conversão de tipos |
| **BootStrap** | Classe com `init` e `destroy`, executados na subida e no desligamento |
| **Cadeia de segurança** | Etapas que a requisição atravessa antes da action: autenticação (sessão, token, Basic), CSRF, autorização. Configurável por `filterChain.chainMap` |
| **Camada estável / recarregável** | Divisão das classes na recarga em desenvolvimento: as recarregáveis (controllers, services, TagLibs...) trocam numa recarga leve; as estáveis (domain classes, `src/main/groovy`) pedem a completa |
| **Command object** | Classe usada como parâmetro de action, preenchida por binding e validada |
| **Constraint** | Regra de validação declarada em `static constraints` |
| **CSRF** | Ataque em que outro site faz o navegador do usuário enviar um formulário. O Grox exige o token `_csrf` nas requisições com sessão |
| **Data Service** | Interface anotada com `@grails.gorm.services.Service(Dominio)`, implementada pelo framework por convenção de nomes |
| **DetachedCriteria** | Consulta imutável e encadeável, criada por `where { }` |
| **DISCRIMINATOR** | Modo de multi-tenancy em que todas as linhas ficam nas mesmas tabelas, separadas por uma coluna de tenant |
| **Domain class** | Classe persistente do GORM, em `grails-app/domain` ou com `@Entity` |
| **Dynamic finder** | Método estático resolvido pelo nome (`findAllByNomeLike`) |
| **Event loop** | Thread do Netty que lê e escreve nas conexões. Nunca bloqueia: o trabalho vai para atores e virtual threads |
| **Flash** | Escopo que dura até a próxima requisição, usado em mensagens depois de um redirect |
| **Geração** | Cada versão da aplicação carregada pela recarga em desenvolvimento, com classloaders próprios |
| **GORM** | API de persistência do Grails. O Grox a reimplementa sobre o Hibernate 7.4 |
| **GSP** | Groovy Server Pages: templates HTML com `${}` e tags |
| **`grox-dsl`** | Módulo de rotas por closure e por anotação, para apps sem o layout `grails-app/` |
| **Handler** | Classe anotada com `@Handler` cujos métodos `@Get`, `@Post`... são rotas do `grox-dsl` |
| **Interceptor** | Classe `*Interceptor` que roda antes e depois das actions que casam com os seus critérios |
| **JWT** | Token assinado (HS256) usado pelo login REST: `Authorization: Bearer <token>` |
| **Layout** | GSP que decora as páginas (SiteMesh 3) |
| **Módulo** | Subprojeto `grox-*` do framework, e a classe `GroxModule` que ele registra para participar das fases de subida |
| **Multi-tenancy** | Vários clientes (tenants) na mesma aplicação e no mesmo banco, com os dados separados |
| **Open session in view** | A sessão Hibernate fica aberta durante a action e a renderização da view, para associações lazy funcionarem na GSP |
| **Pool (concorrência)** | Modo padrão dos controllers: N atores, cada um com sua instância |
| **Promise** | Resultado futuro de uma tarefa assíncrona (`grails.async.Promise`) |
| **Proxy de service** | Subclasse gerada que envolve um service com `@Transactional`, anotações de tenant ou `static actor = true` |
| **Recarga leve / completa** | Leve: troca só os artefatos recarregáveis, mantendo Hibernate e sessões. Completa: recria o Hibernate e roda o BootStrap de novo |
| **Resolver de tenant** | Classe que diz qual é o tenant atual (`TenantResolver`) |
| **`ScopedValue`** | Valor do Java 25+ vinculado a um trecho de execução. O Grox guarda nele a requisição, a sessão de dados, a autenticação e o tenant, em vez de `ThreadLocal` |
| **Service-ator** | Service com `static actor = true`: as chamadas são serializadas por um ator *(extensão Grox)* |
| **SiteMesh** | Biblioteca de decoração de páginas usada pelos layouts |
| **Stateless** | Caminho sem sessão nem CSRF, autenticado só por token ou Basic |
| **TagLib** | Classe `*TagLib` que define tags GSP |
| **Tenant** | Cliente ou organização dona de um conjunto de dados |
| **Trait** | Mixin do Groovy. A AST aplica traits (`ControllerApi`, `GormEntity`...) aos artefatos |
| **UrlMappings** | Classe que declara as rotas dos controllers |
| **Virtual thread** | Thread leve do Java 21+. Bloquear nela (JDBC, chamadas a services-ator) é barato |

## Veja também

- [A arquitetura em uma página](../introducao/arquitetura.md)
- [O que é o Grox](../introducao/o-que-e.md)
