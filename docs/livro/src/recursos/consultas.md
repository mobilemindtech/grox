# Consultas

Este capítulo mostra todas as formas de ler dados: os métodos estáticos, os dynamic finders, `findWhere`, HQL, as consultas `where { }`, `DetachedCriteria` e `createCriteria`. Todas passam pelo mesmo motor (Criteria API do JPA), então joins, conversão de tipos, paginação e o filtro de tenant valem igual em todas.

## Métodos estáticos

| Método | Retorno |
|---|---|
| `get(id)` | A entidade ou `null` |
| `read(id)` | Como `get`, para leitura |
| `load(id)` | Proxy lazy (só em entidades alvo de many-to-one; nas demais carrega na hora, ver [Domain classes](domain.md#proxies-e-carga-em-lote)) |
| `exists(id)` | `true`/`false` |
| `getAll([1, 2, 3])` | Lista na ordem dos ids |
| `list()`, `list(max: 10, offset: 20, sort: 'nome', order: 'desc')` | Lista; com `max`, um `PagedResultList` |
| `count()` | Total |
| `first()`, `last()`, `first(sort: 'nome')`, `last(Map)` | Primeira ou última pelo id (ou pelo `sort`) |

- `sort` só aceita propriedades persistentes. Ordenar por uma coleção `hasMany` falha, como no Hibernate.
- `offset` negativo é tratado como 0, como no GORM.

### `PagedResultList`

Com `max` nos parâmetros, `list`, `createCriteria().list(max: n) { }` e `where { }.list(max: n)` devolvem um `PagedResultList`: a lista da página mais `totalCount`. O `count` só roda na primeira leitura de `totalCount`.

```groovy
def pagina = Livro.list(max: 10, offset: params.int('offset', 0))
render view: 'index', model: [livros: pagina, total: pagina.totalCount]
```

## Dynamic finders

```groovy
Livro.findByTitulo('Dom Casmurro')
Livro.findAllByAutorAndAnoGreaterThan(autor, 1900, [max: 10, sort: 'ano'])
Livro.countByAnoBetween(1900, 1950)
Livro.findAllByTituloIlike('%casa%')
Livro.listOrderByTitulo()
Usuario.findAllByIdadeGreaterThan(params.idade)      // String convertida para Integer
```

| Operador | Exemplo |
|---|---|
| `Equal` (padrão) | `findByNome(n)` |
| `NotEqual` | `findAllByStatusNotEqual(s)` |
| `LessThan`, `LessThanEquals`, `GreaterThan`, `GreaterThanEquals` | `findAllByPrecoLessThan(10)` |
| `Like`, `Ilike`, `NotLike` | `findAllByNomeLike('A%')` |
| `InList`, `NotInList` | `findAllByIdInList([1, 2])` |
| `Between` (2 argumentos) | `findAllByAnoBetween(1900, 1950)` |
| `InRange` (um `Range`) | `findAllByAnoInRange(1900..1950)` |
| `IsNull`, `IsNotNull` | `findAllByGrupoIsNull()` |
| `IsEmpty`, `IsNotEmpty` | `findAllByEnderecosIsEmpty()` |

- Combine termos com `And` **ou** `Or`, não os dois no mesmo finder.
- Um `Map` no fim aceita `max`, `offset`, `sort` e `order`.
- Argumentos `String` são convertidos para o tipo da propriedade. `GString` vira `String`.

### Finders booleanos

Como no GORM, uma propriedade booleana pode ser o próprio critério:

```groovy
Usuario.findAllAtivo()             // ativo = true
Usuario.findAllNotAtivo()          // ativo = false
Usuario.findAtivoByNome('Ana')     // ativo = true AND nome = 'Ana'
Usuario.findAllAtivoByNomeOrEmail(n, e)   // ativo AND (nome = n OR email = e)
```

A propriedade booleana não consome argumento. Argumentos extras que não são `Map` são ignorados (`findAllAtivo(true)` funciona).

## `findWhere` e `findAllWhere`

```groovy
Livro.findWhere(titulo: 'X', ano: 1900)
Livro.findAllWhere([autor: autor], [max: 5, sort: 'ano'])
```

## HQL

```groovy
Livro.findAll('from Livro l where l.ano > :ano', [ano: 1900], [max: 10])
Livro.find('from Livro where titulo = ?1', ['X'])
Livro.executeQuery('select l.titulo from Livro l where l.autor.id = :id', [id: 3])
Livro.executeQuery('from Livro where id = ?1', id)          // valor solto
Livro.executeUpdate('update Livro set preco = preco * 1.1 where ano < :ano', [ano: 1950])
```

- **Parâmetros:** nomeados (`:n` com `[n: 'x']`) ou posicionais (`?1` com `[valor]`, ou um valor solto, ou um array).
- **Renumeração** *(extensão Grox)*: os rótulos herdados do Hibernate 5, que o Hibernate 7 recusa (`?0, ?1...` com base zero, e `?` sem número), são renumerados para `?1, ?2...`. O resultado fica em cache.
- **`GString`** (`"%${q}%"`) é convertida para `String`, como no GORM (o Hibernate 7 recusaria).
- **Opções no mapa de parâmetros:** `max`, `offset`, `lock`, `readOnly`, `cache`, `fetchSize`, `timeout` e `flushMode`. São separadas dos parâmetros nomeados. `lock: true` gera `FOR UPDATE`.

```groovy
Conta.executeQuery('from Conta where id = :id', [id: 1, lock: true])
```

O HQL do Hibernate 6+ não aceita nomes de coluna (`tenant_id`, `arquivo_morto = 'false'`); o do Hibernate 5 os repassava. Use os nomes das propriedades.

## Consultas `where { }`

```groovy
int minimo = 18
def q = Livro.where { titulo ==~ 'G%' && (preco > minimo || autor.nome == params.autor) && !esgotado }
q.list(max: 10, sort: 'titulo')
q.find(); q.count(); q.exists()
q.where { situacao == Situacao.ATIVO }.sort('preco', 'desc').max(5).list()   // encadeia (imutável)
q.updateAll(preco: 10.0)
q.deleteAll()
for (l in q) { println l.titulo }                                            // iterável

Livro.whereAny { titulo == 'A'; titulo == 'B' }        // linhas combinadas com OR
Livro.findAll { preco < 20 }
Livro.findAll(max: 5) { autor.nome == 'Ana' }
Livro.find { titulo == 'X' }
```

| Operador | Critério |
|---|---|
| `==`, `!=` | igual, diferente (`== null` vira `isNull`) |
| `>`, `>=`, `<`, `<=` | comparação |
| `==~ 'G%'` | `like` |
| `=~ 'g%'` | `ilike` (sem diferenciar maiúsculas) |
| `in [..]`, `in 1..5` | `inList`, `between` |
| `!in` | `notInList` |
| `&&`, `\|\|`, `!`, parênteses | E, OU, NÃO |
| `esgotado` (booleano sozinho) | `esgotado == true` |
| `autor.nome == x`, `livro.autor.nome == x` | join pela associação (qualquer número de níveis, também por `hasMany`) |
| `preco > custo` | comparação entre propriedades (o lado direito é propriedade da domain class) |

- Várias linhas no closure são combinadas com AND (em `whereAny`, com OR).
- O lado direito pode ser variável local, `params.x`, chamada de método ou GString.
- **É compilado:** uma transformação AST reescreve o corpo do closure, como no GORM. Expressões não suportadas (`nome.size() > 3`, funções como `year(data)`, subqueries) e propriedades inexistentes viram **erro de compilação**. Nos caminhos de associação, cada nível é conferido na domain class do nível anterior:

  ```groovy
  Capitulo.where { livro.autor.nome == 'Ana' && paginas > 50 }     // dois níveis: compila
  Capitulo.where { livro.autor.naoExiste == 'x' }                  // erro: Autor não tem 'naoExiste'
  ```

  Um nível que não é domain class (embedded, tipo não resolvido) não é conferido.
- A transformação vale em `Classe.where { }`, `find { }` e `findAll { }` de domain classes, e em `where`/`whereAny` encadeados ou chamados sobre uma variável `DetachedCriteria`, em qualquer código (controllers, services, testes).

### `DetachedCriteria`

```groovy
import grails.gorm.DetachedCriteria

def ativos = new DetachedCriteria(Usuario).where { ativo }
ativos.list(); ativos.count()
new DetachedCriteria(Pedido).distinct('id').list()
```

`list`, `find`, `count`, `exists`, `deleteAll`, `updateAll`, `sort`, `max` e o encadeamento de `where` funcionam como na consulta `where { }`.

## `createCriteria` e `withCriteria`

```groovy
def resultado = Livro.createCriteria().list(max: 10, offset: 0) {
    ge('preco', 10.0)
    or { like('titulo', 'G%'); isNull('autor') }
    autor { eq('nome', 'Ana') }            // associação aninhada
    not { eq('situacao', Situacao.ESGOTADO) }
    order('titulo', 'desc')
}
Livro.createCriteria().get { eq('titulo', 'X') }
Livro.createCriteria().count { gt('preco', 20.0) }
Livro.createCriteria().listDistinct { autor { eq('pais', 'BR') } }
Livro.withCriteria { between('preco', 10.0, 50.0) }
```

| Grupo | Métodos |
|---|---|
| Restrições | `eq`, `ne`, `gt`, `ge` (`gte`), `lt`, `le` (`lte`), `like`, `ilike`, `between`, `'in'`/`inList`, `notIn`, `isNull`, `isNotNull`, `isEmpty`, `isNotEmpty`, `idEq` |
| Entre propriedades | `eqProperty`, `neProperty`, `gtProperty`, `geProperty`, `ltProperty`, `leProperty` |
| Combinações | `and`, `or`, `not` |
| Associações | bloco com o nome da associação (`autor { }`), `createAlias('autor', 'a')` |
| Controle | `order`, `maxResults`, `firstResult` |

### Projeções

```groovy
Livro.createCriteria().list {
    projections { groupProperty('autor.nome'); count(); sum('preco') }   // [[nome, qtd, soma], ...]
}
Livro.createCriteria().list {
    projections { autor { groupProperty('nome') } }                        // o mesmo que 'autor.nome'
}
Livro.createCriteria().get { projections { max('preco') } }
```

- Projeções: `property`, `distinct`, `groupProperty`, `count`, `rowCount`, `countDistinct`, `sum`, `avg`, `min`, `max`.
- Com uma projeção, `list` devolve os valores; com várias, uma lista por linha.
- Como no GORM, projeções também funcionam fora do bloco `projections { }` (`groupProperty('autor')` direto no closure).
- Ainda não há `fetchMode`, `sqlRestriction` nem `scroll`.

## SQL nativo

`sessionFactory.currentSession.createNativeQuery(sql)` e `groovy.sql.Sql` (com o bean `dataSource`) funcionam, mas **não são filtrados por tenant**. Inclua o filtro na consulta:

```groovy
def sql = new groovy.sql.Sql(dataSource)
sql.rows('select * from tarefa where tenant_id = ?', [Tenants.currentId()])
```

`session.createSQLQuery` foi removido no Hibernate 6: use `createNativeQuery`.

## Diferenças para o Grails

| Recurso | Grox |
|---|---|
| Named queries | ❌ |
| `where { }` com funções, subqueries ou `property.size()` | ❌ (erro de compilação) |
| `fetchMode`, `sqlRestriction`, `scroll` na Criteria | ❌ |
| HQL com `?0` ou `?` | Aceito e renumerado (o Hibernate 7 do Grails 8 recusa) |
| Opções do `executeQuery` (`lock`, `readOnly`...) | Aplicadas |

## Veja também

- [Domain classes](domain.md)
- [Multi-tenancy](multi-tenancy.md)
- [Sessões, transações e eventos](transacoes.md)
- [API do GORM](../referencia/api-gorm.md)
- [Desempenho](../recomendacoes/desempenho.md)
