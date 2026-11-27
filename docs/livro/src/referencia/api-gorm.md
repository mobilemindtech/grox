# API do GORM

Todos os métodos que as domain classes ganham. As classes de `grails-app/domain` (ou anotadas com `@grails.gorm.annotation.Entity`) recebem o trait `GormEntity<Livro>` (genérico: `get`, `find`, `save` devolvem a domain e `list`, `findAll*` um `List<Livro>`), `Long id`, `Long version`, as coleções de `hasMany`, as referências de `belongsTo` e os construtores `()` e `(Map)`. A API é a do GORM, sobre Hibernate 7.4.

## Métodos estáticos

### Carga e listagem

| Método | Retorno | Observação |
|---|---|---|
| `get(id)` | instância ou `null` | Id convertido para o tipo da chave |
| `read(id)` | instância ou `null` | Somente leitura |
| `load(id)` | proxy ou instância | Proxy lazy só nas entidades alvo de many-to-one (`grox.data.proxies: auto`) |
| `exists(id)` | `boolean` | Um `count` pelo id, sem carregar a entidade |
| `getAll([ids])` | `List` | Uma consulta só; na ordem dos ids, com `null` para os inexistentes |
| `list()`, `list(max:, offset:, sort:, order:)` | `List` | Com `max`, devolve `PagedResultList` (`totalCount`) |
| `count()` | `long` | |
| `first()`, `first('prop')`, `first(sort:, ...)` | instância | Pelo id, ou pela propriedade |
| `last()`, `last('prop')`, `last(sort:, ...)` | instância | |

### Consultas por exemplo e HQL

| Método | Observação |
|---|---|
| `findWhere([nome: 'x'])`, `findAllWhere([..])`, `findAllWhere([..], [max: ..])` | Igualdade em cada propriedade |
| `find(hql)`, `find(hql, params)` | Primeiro resultado |
| `findAll()`, `findAll(hql)`, `findAll(hql, params)`, `findAll(hql, params, [max:, offset:])` | |
| `executeQuery(hql)`, `executeQuery(hql, params)`, `executeQuery(hql, params, opções)` | |
| `executeUpdate(hql)`, `executeUpdate(hql, params)` | Devolve o número de linhas |

- **Parâmetros:** nomeados (`[n: 'x']` com `:n`), posicionais (`[1]` com `?1`) ou um valor solto (`executeQuery(hql, id)`). `?0` e `?` sem número, do Hibernate 5, são renumerados para `?1, ?2...`. `GString` vira `String`.
- **Opções:** `max`, `offset`, `lock`, `readOnly`, `cache`, `fetchSize`, `timeout`, `flushMode`, no mapa de opções ou junto dos parâmetros nomeados.

### Dynamic finders

| Prefixo | Retorno |
|---|---|
| `findBy<Expr>` | Primeiro resultado ou `null` |
| `findAllBy<Expr>` | `List` |
| `countBy<Expr>` | `long` |
| `listOrderBy<Prop>` | `List` ordenada |
| `findAll<Bool>()`, `findAllNot<Bool>()`, `find<Bool>By<Expr>` | Finders booleanos |

Operadores (sufixo da propriedade): `Equal` (padrão), `NotEqual`, `LessThan`, `LessThanEquals`, `GreaterThan`, `GreaterThanEquals`, `Like`, `Ilike`, `NotLike`, `InList`, `NotInList`, `Between` (2 argumentos), `InRange` (Range), `IsNull`, `IsNotNull`, `IsEmpty`, `IsNotEmpty`.

- Combine termos com `And` **ou** `Or` (não os dois).
- Um `Map` no fim aceita `max`, `offset`, `sort` e `order`.
- Argumentos `String` são convertidos para o tipo da propriedade: `findAllByIdadeGreaterThan(params.idade)`.
- Nos finders booleanos, a propriedade vale `true` (`false` com `Not`) e não consome argumento. Com `Or`, continua obrigatória (`ativo AND (a OR b)`).

```groovy
Usuario.findByEmail('a@x.com')
Usuario.findAllByNomeLikeAndIdadeGreaterThan('A%', 18, [max: 10, sort: 'nome'])
Usuario.countByGrupoIsNull()
Usuario.findAllAtivo()
```

### `where`, critérios e `DetachedCriteria`

| Método | Observação |
|---|---|
| `where { }` | `DetachedCriteria` (closure reescrito pela AST) |
| `whereAny { }` | Linhas combinadas com OR |
| `findAll { }`, `findAll(max: 5) { }`, `find { }` | Atalhos de `where` |
| `createCriteria()` | `CriteriaBuilder`: `list { }`, `list(max:, offset:) { }`, `listDistinct { }`, `get { }`, `count { }` |
| `withCriteria { }`, `withCriteria(params) { }` | `createCriteria().list` |
| `new grails.gorm.DetachedCriteria(D)` | `.distinct('prop').list()` |

`DetachedCriteria` (imutável, encadeável e iterável):

| Método | |
|---|---|
| `where { }`, `whereAny { }` | Acrescenta critérios |
| `sort('prop', 'desc')`, `max(n)`, `offset(n)` | |
| `list(params)`, `findAll(params)` | `List` (com `max`, `PagedResultList`) |
| `find()`, `get()` | Um resultado |
| `count()`, `exists()` | |
| `deleteAll()`, `updateAll(prop: valor)` | Em lote; devolvem o número de linhas |

Operadores do `where { }`:

| Operador | Critério |
|---|---|
| `==`, `!=` | igual / diferente (`== null` vira `isNull`) |
| `>`, `>=`, `<`, `<=` | comparação |
| `==~ 'G%'` | `like` |
| `=~ 'g%'` | `ilike` |
| `in [..]`, `in 1..5`, `!in` | `inList`, `between`, `notInList` |
| `&&`, `\|\|`, `!`, parênteses | E, OU, NÃO |
| `ativo` sozinho | `ativo == true` |
| `autor.nome == x` | join pela associação |
| `preco > custo` | comparação entre propriedades |

Expressões não suportadas (`nome.size() > 3`, funções, subqueries) e propriedades inexistentes são **erro de compilação**.

Restrições do `createCriteria`: `eq`, `ne`, `gt`, `ge`/`gte`, `lt`, `le`/`lte`, `like`, `ilike`, `between`, `'in'`/`inList`, `notIn`, `isNull`, `isNotNull`, `isEmpty`, `isNotEmpty`, `idEq`, `eqProperty`, `neProperty`, `gtProperty`, `geProperty`, `ltProperty`, `leProperty`; `and`, `or`, `not`; `order`, `maxResults`, `firstResult`, `cache` (ignorado); `createAlias(caminho, alias)`; associação aninhada (`autor { eq('nome', 'Ana') }`). Projeções (em `projections { }` ou fora dele): `property`, `distinct`, `groupProperty`, `count`, `rowCount`, `countDistinct`, `sum`, `avg`, `min`, `max`.

### Sessão, transação e tenant

| Método | Observação |
|---|---|
| `withTransaction { status -> }` | Participa da transação aberta ou abre uma |
| `withNewTransaction { status -> }` | Sessão e transação novas |
| `withSession { session -> }` | Sessão Hibernate corrente |
| `withNewSession { session -> }` | Sessão nova |
| `withTenant('acme') { }` | Igual a `Tenants.withId` |

### Filtros do Hibernate (plugin hibernate-filter)

| Método | Observação |
|---|---|
| `withoutHibernateFilters { }` | Desliga os filtros com `default: true` no bloco |
| `withoutHibernateFilter('nome') { }` | Desliga um filtro no bloco |
| `withHibernateFilters { }` | Religa os filtros padrão no bloco |
| `enableHibernateFilter('nome')`, `disableHibernateFilter('nome')` | Na sessão atual |

O filtro de tenant (`_tenantId`) é recusado por essa API: use `Tenants.withoutId`.

## Métodos de instância

| Método | Observação |
|---|---|
| `save()`, `save(flush: true, failOnError: true, validate: false, insert: true)` | Valida e grava. Devolve a instância, ou `null` se inválida (`ValidationException` com `failOnError`) |
| `delete()`, `delete(flush: true)` | |
| `validate()`, `validate(['campo1', 'campo2'])` | |
| `hasErrors()`, `errors`, `clearErrors()` | |
| `refresh()` | Recarrega do banco |
| `discard()` | Tira da sessão (use depois de uma validação que falhou) |
| `merge()`, `merge(opts)`, `attach()` | Reanexa uma instância destacada |
| `attached` | Está na sessão |
| `ident()` | O id (na chave composta, a própria instância) |
| `isDirty()`, `isDirty('prop')`, `getPersistentValue('prop')`, `dirtyPropertyNames` | Comparam com o estado carregado. Instância nova ou destacada conta como alterada |
| `addTo<Colecao>(obj ou Map)`, `removeFrom<Colecao>(obj)` | Mantêm os dois lados em memória |
| `properties = params` | Binding (sem `id`, `version`, `dateCreated`, `lastUpdated`) |

## Propriedades estáticas da domain class

| Propriedade | Exemplo |
|---|---|
| `hasMany` | `[enderecos: Endereco, tags: String]` |
| `belongsTo` | `[empresa: Empresa]` ou `Turma` |
| `hasOne` | `[nariz: Nariz]` |
| `embedded` | `['endereco']` (componente nas colunas do dono: `endereco_rua`...) |
| `mappedBy` | `[enderecos: 'morador']` |
| `transients` | `['nomeCompleto']` |
| `constraints` | `{ nome blank: false, size: 2..100 }` |
| `mapping` | `{ table 'usuarios'; sort 'nome'; version false; id generator: 'uuid'; nome column: 'nm', length: 80, sqlType: 'varchar(80)'; descricao type: 'text'; total formula: '...'; autoTimestamp false; tenantId name: 'empresa' }` |
| `hibernateFilters` | `{ ativos(condition: 'ativo = true', default: true) }` |

Geradores de id: `identity` (padrão), `sequence`, `uuid` (tipo `UUID`), `assigned`; `id composite: ['role', 'user']` para chave composta.

Eventos de instância: `beforeInsert()`, `beforeUpdate()`, `beforeDelete()`. Timestamps: `dateCreated` e `lastUpdated`.

## Tenants

`grails.gorm.multitenancy.Tenants`:

| Método | Observação |
|---|---|
| `Tenants.currentId()` | Tenant atual; sem ele, `TenantNotFoundException` |
| `Tenants.currentIdOrNull()` | `null` sem tenant |
| `Tenants.withId('acme') { }` | Executa com outro tenant (abre nova sessão se preciso) |
| `Tenants.withCurrent { }` | Executa com o tenant resolvido |
| `Tenants.withoutId { }` | Sem filtro: vê todos os tenants |

Anotações em services: `@WithoutTenant`, `@Tenant({ 'acme' })`, `@CurrentTenant`, combináveis com `@Transactional`.

## GORM Data Services

```groovy
@grails.gorm.services.Service(Ponto)
interface PontoService {
    Ponto get(Serializable id)
    List<Ponto> list(Map args)
    Long count()
    Ponto save(Ponto ponto)          // ValidationException se inválido
    void delete(Serializable id)
    Ponto findByNome(String nome)
}
```

Métodos aceitos por convenção: `get`, `read`, `list`, `count`, `save`, `delete`, `find*`, `findAll*` e `count*`. Os outros lançam `UnsupportedOperationException`. Cada chamada roda numa transação.

## Veja também

- [Domain classes](../recursos/domain.md)
- [Consultas](../recursos/consultas.md)
- [Validação, binding e mensagens](../recursos/validacao.md)
- [Sessões, transações e eventos](../recursos/transacoes.md)
- [Multi-tenancy](../recursos/multi-tenancy.md)
