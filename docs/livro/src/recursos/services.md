# Services e injeção de dependências

Este capítulo cobre os services (singletons, transacionais ou atores), a injeção de dependências por nome, os beans que o framework oferece e os limites do contêiner do Grox.

## Um service

```groovy
// grails-app/services/sample/LivroService.groovy
class LivroService {
    def mailService                        // injeção por nome (def ou tipado)

    List<Livro> recentes() { Livro.list(max: 10, sort: 'dateCreated', order: 'desc') }
}
```

- Services são **singletons**, instanciados na subida e injetados por nome (`LivroService` → `livroService`).
- A AST aplica o trait `ServiceApi`, que dá `log`.
- Um service comum é compartilhado por todas as requisições ao mesmo tempo: precisa ser thread-safe (sem estado mutável em campos, ou com sincronização).

## Injeção de dependências

É por **nome de propriedade**, como no Grails.

- **Quem recebe:** qualquer propriedade Groovy (com setter) de nome igual a um bean e tipo compatível (`def` ou o tipo do bean). Recebem controllers (cada instância), services, TagLibs, interceptors, BootStrap, jobs, command objects, beans do `resources.groovy` e o `tenantResolverClass`.
- **Domain classes não recebem**, como no Grails 3.3+.
- **Circularidade:** dependências circulares funcionam, porque todos os services são criados antes da injeção.
- `@Autowired` e `@Lazy` do Spring compilam; a injeção continua sendo por nome.
- Também por nome: `Holders.applicationContext.getBean('livroService')`, `applicationContext.getBean(LivroService)`.

Beans disponíveis além dos services:

| Bean | O que é |
|---|---|
| `grailsApplication` | A aplicação (config, `xxxClasses`, `mainContext`) |
| `groxApplication`, `groxRuntime` | A aplicação do core e o runtime *(extensão Grox)* |
| `messageSource` | `getMessage(code, args, default, locale)` |
| `groovyPageRenderer` | Renderiza views e templates para String |
| `springSecurityService` | Com a segurança ativa |
| `sessionFactory`, `hibernateDatastore`, `transactionManager`, `dataSource` | Dados |
| `assetResourceLocator`, `localeResolver`, `jwtService`, `pluginManager` | Outros |

A lista completa está em [Beans do framework](../referencia/beans.md).

## Transações: `@Transactional` e `@ReadOnly`

```groovy
import grails.gorm.transactions.Transactional
import grails.gorm.transactions.ReadOnly

@Transactional
class PedidoService {
    Pedido fechar(Long id) {
        def p = Pedido.get(id)
        p.status = 'FECHADO'
        p.save()
        estoqueService.baixar(p)          // participa da mesma transação
        p
    }

    @ReadOnly
    List<Pedido> abertos() { Pedido.findAllByStatus('ABERTO') }
}
```

- Na classe ou em métodos. Cada chamada **externa** roda em `withTransaction`: participa de uma transação aberta ou abre uma nova, com commit ao final e rollback numa exceção.
- O service vira um **proxy-subclasse**: a injeção tipada continua funcionando. Chamadas internas (`this.x()`) não passam pelo proxy, então não abrem transação.
- Métodos sem anotação num service com outros transacionais rodam na instância real, com as dependências injetadas.
- `@org.springframework.transaction.annotation.Transactional` também vale.
- `propagation = Propagation.REQUIRES_NEW` roda em sessão e transação novas (`withNewTransaction`). `SUPPORTS`, `NOT_SUPPORTED` e `NEVER` não abrem transação; `MANDATORY` exige uma em andamento.
- `isolation`, `timeout`, `rollbackFor`, `noRollbackFor` são aceitos, mas ainda **sem efeito**.
- `@ReadOnly` não muda o comportamento da sessão.
- `TransactionAspectSupport.currentTransactionStatus()` devolve o status da transação corrente (`NoTransactionException` sem transação), por exemplo para `setRollbackOnly()`.
- `static transactional = true` (Grails 2) não é lido.

Programaticamente, o bean `transactionManager` (`PlatformTransactionManager`) oferece `getTransaction(new DefaultTransactionDefinition())`, `commit(status)` e `rollback(status)` sobre a sessão corrente (REQUIRED, SUPPORTS, MANDATORY). Veja [Sessões, transações e eventos](transacoes.md).

## Anotações de tenant

`@WithoutTenant`, `@Tenant({ 'acme' })` e `@CurrentTenant` funcionam em services (classe ou método), combináveis com `@Transactional`, também pelo proxy. Elas são **opcionais**: veja [Multi-tenancy](multi-tenancy.md).

## Services-ator *(extensão Grox)*

```groovy
class ContadorService {
    static actor = true        // chamadas serializadas por um ator
    private int total           // sem sincronização: só o ator acessa
    int incrementar() { ++total }
}
```

- A instância real fica atrás de um ator. Quem injeta recebe um proxy-subclasse; cada chamada vira uma mensagem e **bloqueia a virtual thread** de quem chamou até a resposta.
- Exceções chegam para quem chamou.
- O estado interno dispensa sincronização: o ator processa uma mensagem por vez.
- A sessão de dados, a autenticação e o tenant de quem chama vão junto na mensagem. Como quem chama fica bloqueado, a sessão Hibernate nunca é usada por duas threads ao mesmo tempo.
- **Evite ciclos**: A (ator) → B (ator) → A trava.
- Um ator serializa as chamadas: bom para estado, ruim para throughput. Use só onde precisa.

Mais em [Concorrência](concorrencia.md).

## GORM Data Services

Interfaces anotadas com `@grails.gorm.services.Service(Dominio)` em `grails-app/services` viram beans com a implementação gerada por convenção (`get`, `list`, `count`, `save`, `delete`, `findBy*`...). Veja [Domain classes](domain.md#gorm-data-services).

## Inicialização

Métodos `@PostConstruct` (`jakarta.annotation`) de services e beans rodam depois que todos estão injetados. Para lógica que depende do banco pronto, use o [BootStrap](estrutura.md#bootstrap).

## Limites

- Não há injeção por construtor, qualifiers nem escopos (`static scope`): tudo é singleton, exceto controllers no modo `request`.
- Proxies (ator, transacional, tenant) só atuam em chamadas externas.

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| Contêiner Spring | Injeção própria, só por nome |
| `static scope` (prototype, request, session) | Ausente |
| `static transactional` | Ausente: use `@Transactional` |
| `isolation`/`timeout`/`rollbackFor` | Aceitos sem efeito |
| Não existe | `static actor = true` |

## Veja também

- [Concorrência: atores e virtual threads](concorrencia.md)
- [Sessões, transações e eventos](transacoes.md)
- [Beans e resources.groovy](beans.md)
- [Beans do framework](../referencia/beans.md)
