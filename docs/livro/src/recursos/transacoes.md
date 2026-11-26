# Sessões, transações e eventos

Este capítulo explica quando existe uma sessão do Hibernate, como as transações funcionam (`save`, `withTransaction`, `@Transactional`, `transactionManager`), o que é gravado e o que não é, e como reagir a eventos de persistência (`beforeInsert`, `@Listener`, dirty checking).

## A sessão de dados

| Onde | Sessão |
|---|---|
| Action de controller ou rota do `grox-dsl` | **Uma por invocação**, aberta no primeiro acesso ao banco e mantida até o fim da **renderização da view** (open session in view): associações lazy funcionam na GSP |
| Página de login e views de erro | Também têm sessão (layout ou TagLib que lê coleção lazy funciona) |
| Service-ator chamado pela action | Usa a sessão de quem chamou (quem chama fica bloqueado, então nunca há duas threads na mesma sessão) |
| Tarefa assíncrona (`task { }`) | Sessão própria, aberta sob demanda: entidades carregadas na action chegam desanexadas |
| BootStrap, jobs, testes, threads próprias | Cada chamada do GORM abre e fecha a sua. Para agrupar, use `withTransaction` ou `withNewSession` |

A sessão é **preguiçosa**: uma action que não toca o banco não paga a abertura nem a resolução do tenant.

## `save()` e `delete()`

```groovy
livro.save()                              // valida, grava; null se inválido
livro.save(flush: true)                   // força o flush
livro.save(failOnError: true)             // ValidationException se inválido
livro.delete(flush: true)
```

`save()` aplica os timestamps, chama `beforeInsert`/`beforeUpdate`, valida e faz `persist` (ou `merge`, se a entidade estiver destacada). Tudo dentro de `withTransaction`: participa da transação aberta ou abre uma e faz commit ao final.

Violações de restrição do banco viram `DataIntegrityViolationException` (o nome do Spring) em todos os pontos onde o SQL sai: no `save` (com id `IDENTITY`, o `INSERT` sai na hora), no `delete`, no `flush`, no `executeUpdate` e no commit (alterações feitas só por dirty checking).

### Alterações sem `save()`

Uma entidade carregada e alterada **só é gravada por `save()`** ou dentro de uma transação que faça commit. No fim da requisição não há flush automático fora de transação.

```groovy
def l = Livro.get(1)
l.titulo = 'Novo'            // não é gravado sozinho numa action comum
l.save()                     // agora sim
```

Depois de uma validação que falhou, chame `discard()` antes de seguir, para que outra transação na mesma requisição não grave o estado inválido.

Outros métodos de instância: `refresh()`, `discard()`, `attach()`, `merge()`, `isAttached()`, `ident()`.

## Transações programáticas

```groovy
Livro.withTransaction { status ->
    new Livro(titulo: 'A').save()
    if (algoErrado) status.setRollbackOnly()
}
Livro.withNewTransaction { ... }           // sessão e transação novas
Livro.withSession { session -> session.flush() }
Livro.withNewSession { session -> ... }
```

`withTransaction` participa de uma transação já aberta ou abre uma nova. Como no Spring e no Grails, uma participante que lança exceção (ou chama `status.setRollbackOnly()`) marca a transação **inteira**: mesmo que quem chamou capture a exceção, a externa desfaz tudo no fim e lança `UnexpectedRollbackException`. Só o `setRollbackOnly()` da própria transação externa desfaz sem exceção.

```groovy
Livro.withTransaction {
    new Livro(titulo: 'A').save()
    try {
        pagamentoService.debitar()   // @Transactional: participa; grava e depois lança
    } catch (e) {
        log.warn 'falhou'           // capturar não salva o commit: nada é gravado, e sai UnexpectedRollbackException
    }
}
```

## Services transacionais

```groovy
import grails.gorm.transactions.Transactional
import grails.gorm.transactions.ReadOnly

@Transactional
class PedidoService {
    Pedido fechar(Long id) { ... }

    @ReadOnly
    List<Pedido> abertos() { ... }
}
```

- Na classe ou em métodos. Cada chamada **externa** roda em transação; chamadas internas (`this.x()`) não passam pelo proxy.
- O `@org.springframework.transaction.annotation.Transactional` também vale.
- `propagation = Propagation.REQUIRES_NEW` roda em sessão e transação novas. `SUPPORTS`, `NOT_SUPPORTED` e `NEVER` não abrem transação; `MANDATORY` exige uma em andamento.
- `isolation`, `timeout`, `rollbackFor` e `noRollbackFor` são aceitos, mas ainda sem efeito. `@ReadOnly` e `readOnly = true` abrem a transação com a sessão em `FlushMode.MANUAL` e as entidades carregadas como somente leitura: alterações feitas nelas não vão ao banco. Participando de uma transação de escrita já aberta, vale o modo dela, como no Spring. `static transactional` não é suportado.
- `TransactionAspectSupport.currentTransactionStatus()` devolve o status corrente (`NoTransactionException` sem transação).
- Métodos sem anotação num service que tem outros transacionais só delegam ao objeto real, com as dependências injetadas.

Detalhes do proxy em [Services e injeção de dependências](services.md).

## `transactionManager`

Para código que usa a API do Spring:

```groovy
def transactionManager

def status = transactionManager.getTransaction(new DefaultTransactionDefinition())
try {
    ...
    transactionManager.commit(status)
} catch (e) {
    transactionManager.rollback(status)
    throw e
}
```

Opera sobre a sessão corrente. No uso programático, só `REQUIRED`, `SUPPORTS` e `MANDATORY`. O bean `sessionFactory` também existe: `sessionFactory.currentSession` é a sessão corrente.

## Eventos da domain class

```groovy
class Usuario {
    String senha
    def beforeInsert() { senha = Passwords.encode(senha) }
    def beforeUpdate() { if (isDirty('senha')) senha = Passwords.encode(senha) }
    def beforeDelete() { ... }
}
```

São chamados pelo `save()` e pelo `delete()` do Grox. `after*` e `beforeValidate` ainda não existem.

## Listeners

Para lógica fora da entidade, como o `UserPasswordEncoderListener` gerado pelo spring-security:

```groovy
import grails.events.annotation.gorm.Listener
import org.grails.datastore.mapping.engine.event.PreUpdateEvent

class UserPasswordEncoderListener {
    SpringSecurityService springSecurityService

    @Listener(User)
    void onPreUpdateEvent(PreUpdateEvent event) {
        User u = event.entityObject as User
        if (u.isDirty('password')) event.entityAccess.setProperty('password', springSecurityService.encodePassword(u.password))
    }
}
// resources.groovy: userPasswordEncoderListener(UserPasswordEncoderListener)
```

- O bean é registrado no `resources.groovy`, ou o método `@Listener` fica num service.
- Eventos (pacote `org.grails.datastore.mapping.engine.event`): `PreInsertEvent`, `PreUpdateEvent`, `PreDeleteEvent` e os `PostInsertEvent`, `PostUpdateEvent`, `PostDeleteEvent`.
- `event.cancel()` num `Pre*` cancela a gravação.
- Também funcionam listeners no estilo do GORM, que estendem `AbstractPersistenceEventListener` (`onPersistenceEvent`, `supportsEventType`), registrados no `resources.groovy` com `ref('hibernateDatastore')` no construtor ou como services:

```groovy
class AuditoriaListener extends AbstractPersistenceEventListener {
    AuditoriaListener(Datastore ds) { super(ds) }
    protected void onPersistenceEvent(AbstractPersistenceEvent e) { ... }
    boolean supportsEventType(Class<? extends ApplicationEvent> t) { t in [PreInsertEvent, PreUpdateEvent] }
}
// resources.groovy: auditoriaListener(AuditoriaListener, ref('hibernateDatastore'))
```

Os eventos vêm do `save()`/`delete()`: uma alteração gravada só pelo flush automático, sem `save()`, não dispara `PreUpdate`.

## Dirty checking

```groovy
u.isDirty()                   // alguma propriedade mudou
u.isDirty('email')
u.getPersistentValue('email') // valor carregado do banco
u.dirtyPropertyNames          // ['email', ...]
```

A comparação é com o estado carregado do banco. Uma instância nova ou destacada conta como alterada.

## Diferenças para o Grails

| Recurso | Grox |
|---|---|
| Flush no fim da requisição de entidades alteradas sem `save()` | Não acontece fora de transação |
| `after*`, `beforeValidate` | ❌ |
| `PreUpdate` no flush automático | Só no `save()` |
| `static transactional` | ❌ |
| `isolation`/`timeout`/`rollbackFor` | Aceitos, sem efeito |
| Sessão em tarefas assíncronas | Própria da tarefa (a Session do Hibernate não é thread-safe) |

## Veja também

- [Domain classes](domain.md)
- [Services e injeção de dependências](services.md)
- [Actions assíncronas](async.md)
- [Concorrência: atores e virtual threads](concorrencia.md)
- [Plugins reimplementados](plugins.md) (auditoria)
