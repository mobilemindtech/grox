# Domain classes

Este capítulo cobre o modelo de dados: como declarar domain classes, associações, o `static mapping`, os nomes de tabelas e colunas, a geração do esquema, proxies, filtros do Hibernate e os GORM Data Services. Consultas ficam em [Consultas](consultas.md); validação e binding, em [Validação, binding e mensagens](validacao.md).

O Grox tem um GORM próprio sobre o **Hibernate 7.4** e o **HikariCP**. Os jars do GORM não são usados (compilados com Groovy 5, não rodam no Groovy 6), mas a API é a mesma: o código de uma app Grails compila sem mudanças.

## Onde ficam e o que a AST adiciona

Numa app no layout Grails, as domain classes ficam em `grails-app/domain/<pacote>/`. A transformação AST reconhece o diretório e adiciona:

- `Long id` e `Long version`;
- as coleções declaradas em `hasMany`, tipadas (`hasMany = [livros: Livro]` gera `Set<Livro> livros`), e as referências de `belongsTo`;
- o trait `GormEntity<Livro>`, com a API estática e de instância já com o tipo da classe (`Livro.get(1)` é um `Livro`);
- um construtor sem argumentos e um construtor com `Map`, que converte tipos e ignora chaves desconhecidas (`new Usuario(params)`). Ele existe mesmo quando a classe declara o próprio construtor sem argumentos.

Fora de `grails-app/domain` (apps sem o layout Grails, ver [Dados com @Entity](../sem-grails/dados.md)), anote a classe com `@grails.gorm.annotation.Entity`, como no GORM standalone. O resultado é o mesmo.

```groovy
import grails.gorm.annotation.Entity

@Entity
class Livro {
    String titulo
    Integer ano
}
```

Domain classes **não recebem injeção de dependências**, como no Grails 3.3+.

O IntelliJ IDEA completa essa API, os `id`/`version` e os dynamic finders pelo descritor GDSL do framework; em outros editores ou em código `@CompileStatic`, declare `implements GormEntity<Livro>` (ver [Autocomplete no IntelliJ IDEA](estrutura.md#autocomplete-no-intellij-idea)).

## Um exemplo completo

```groovy
class Usuario {
    String nome
    String email
    Integer idade
    String apelido
    Status status = Status.ATIVO     // enum, gravado como texto
    Grupo grupo                      // many-to-one (lazy)
    Date dateCreated                 // preenchido no insert (autoTimestamp)
    Date lastUpdated                 // preenchido a cada save

    static hasMany = [enderecos: Endereco]           // Set<Endereco> enderecos (one-to-many)
    static belongsTo = [empresa: Empresa]            // propriedade + cascata a partir da dona
    static transients = ['nomeCompleto']
    static mappedBy = [enderecos: 'morador']         // quando a volta não é óbvia

    static constraints = {
        nome blank: false, size: 2..100
        email email: true, unique: true
        idade nullable: true, min: 0, max: 150
        status inList: Status.values() as List
        grupo nullable: true
        apelido nullable: true, validator: { v, obj -> v != obj.nome ?: 'igualAoNome' }
    }

    static mapping = {
        table 'usuarios'
        sort 'nome'                  // ou sort nome: 'desc'
        nome column: 'nm', length: 80
    }

    String getNomeCompleto() { "$nome ($apelido)" }

    def beforeInsert() { email = email?.toLowerCase() }
}
```

## Propriedades

- **Tipos simples:** `String`, números (primitivos e wrappers, `BigDecimal`), `Boolean`/`boolean`, `Date` e `java.time`, `UUID`, `byte[]`, enums (gravados como texto).
- **Obrigatórias por padrão:** toda propriedade persistente é `nullable: false`, exceto `dateCreated` e `lastUpdated`. Use `nullable: true` nas constraints para liberar.
- **`transients`:** propriedades listadas em `static transients` não são gravadas. Getters sem campo (`getNomeCompleto()`) também não.
- **Timestamps:** `dateCreated` é preenchido no insert e `lastUpdated` a cada `save()`. `autoTimestamp false` no mapping desliga.
- **`byte[]`:** para arquivos, `maxSize` nas constraints define o tamanho da coluna.

## Associações

### Many-to-one

Uma propriedade cujo tipo é outra domain class vira many-to-one, com carga lazy.

```groovy
class Livro { Autor autor }
```

A coluna é `autor_id`. No binding, aceita `autor.id=3`, `autor=3` ou `{"autor": {"id": 3}}`; id vazio limpa a associação.

### One-to-many (`hasMany`)

```groovy
class Autor { static hasMany = [livros: Livro] }
class Livro { Autor autor }
```

- É bidirecional quando o outro lado tem a propriedade de volta (ou um `belongsTo`). Com mais de uma propriedade candidata, diga qual com `mappedBy`.
- Sem o lado de volta, é **unidirecional**: o Grox usa a tabela de junção do GORM (`autor_livro`, colunas `autor_livros_id` e `livro_id`). Associação auto-referente também funciona.
- `addToLivros(livro)` (ou `addToLivros(titulo: 'X')`, que cria a instância) e `removeFromLivros(livro)` mantêm os dois lados em memória.

Se a tabela de junção tiver o nome de uma entidade existente, o Grox avisa no log e, ao gerar o esquema, a renomeia.

### `belongsTo` e cascata

```groovy
class Empresa { static hasMany = [funcionarios: Funcionario] }
class Funcionario { static belongsTo = [empresa: Empresa] }
```

Com `belongsTo`, `save()` e `delete()` propagam da dona para os filhos, e filhos removidos da coleção são apagados.

### Many-to-many

```groovy
class Turma { static hasMany = [alunos: Aluno] }
class Aluno { static belongsTo = Turma; static hasMany = [turmas: Turma] }
```

O dono é `Turma`, o lado de quem o outro faz `belongsTo`. A tabela de junção é `turma_alunos` (colunas `turma_id` e `aluno_id`). `turma.addToAlunos(a)` atualiza os dois lados em memória, e a gravação acontece pelo lado dono.

### One-to-one com `hasOne`

```groovy
class Rosto { static hasOne = [nariz: Nariz] }
class Nariz { Rosto rosto }
```

A chave estrangeira fica no filho (`nariz.rosto_id`), como no GORM, e o dono propaga save e delete (cascade all).

### One-to-one sem `hasOne`

```groovy
class Carro { Motor motor }
class Motor { static belongsTo = [carro: Carro] }
```

Como no GORM, a chave fica no dono (`carro.motor_id`, com cascade do dono), e `Motor.carro` é o lado inverso, sem coluna `carro_id`. Se o dono também tem `hasMany` do tipo do filho, ou mais de uma propriedade desse tipo, o `belongsTo` continua many-to-one.

### `hasOne` com filho multi-tenant

Se o filho implementa `MultiTenant` (`class Anamnese implements MultiTenant<Anamnese>`), o filtro de tenant do Hibernate 7 vale também na carga por chave, e um filho ausente daria `EntityFilterException`. O Grox mapeia então a propriedade do dono como many-to-one somente leitura, por uma fórmula (`(select min(c.id) from anamnese c where c.cliente_id = id)`) com `not-found IGNORE`: filho ausente vira `null`, como no GORM. A carga é eager e em lote. O próprio Grox faz o cascade: grava o filho depois do pai e o apaga antes dele.

Limites: a fórmula usa os nomes físicos em snake_case (tabela ou coluna com palavra reservada não é escapada) e atribuir `null` ao `hasOne` não apaga o filho (sem orphan removal).

### `hasMany` de valores simples

```groovy
class Artigo { static hasMany = [tags: String] }
```

Grava numa tabela à parte com os nomes do GORM: tabela `artigo_tags`, colunas `artigo_id` e `tags_java_lang_string` (`<propriedade>_<classe>`) e, se a propriedade for `List`, a coluna de posição `tags_idx`.

## `static mapping`

| Instrução | Efeito |
|---|---|
| `table 'usuarios'` | Nome da tabela |
| `sort 'nome'` ou `sort nome: 'desc'` | Ordenação padrão de `list()` |
| `version false` | Sem coluna de versão (sem lock otimista) |
| `id generator: 'identity'` | Padrão. Também `sequence`, `uuid` (id do tipo `UUID`) e `assigned` (a app atribui) |
| `id composite: ['role', 'user']` | Chave composta, sem classe de id: a entidade é a chave (ver abaixo) |
| `nome column: 'nm', length: 80, sqlType: 'varchar(80)'` | Coluna, tamanho e tipo SQL |
| `descricao type: 'text'` | Coluna de texto longo |
| `total formula: 'preco * quantidade'` | Propriedade calculada por SQL, só leitura |
| `autoTimestamp false` | Não preenche `dateCreated`/`lastUpdated` |
| `tenantId name: 'empresa'` | Troca a propriedade de tenant (ver [Multi-tenancy](multi-tenancy.md)) |

`static indexes` e `fetchMode` são aceitos e ignorados. Cache, índices e lazy/eager por propriedade não são suportados.

### Chave composta

```groovy
class UserRole implements Serializable {
    User user
    Role role
    static mapping = { id composite: ['user', 'role']; version false }
}
```

`save()` insere ou mescla conforme a linha exista (ou `save(insert: true)` força o insert), e `ident()` devolve a própria instância. É o formato do `UserRole` gerado pelo `s2-quickstart`.

## Nomes de tabelas e colunas

Os nomes físicos seguem a regra do GORM (`ImprovedNamingStrategy`):

| Propriedade | Coluna |
|---|---|
| `dateCreated` | `date_created` |
| `grupo` (associação) | `grupo_id` |
| `userCriado` | `user_criado_id` |
| `valor10Meses` | `valor10meses` (dígito não separa) |
| `codigoERP` | `codigoerp` (sigla não separa) |

Só separa a maiúscula que fica entre duas minúsculas. Palavras reservadas são citadas, mas continuam em snake_case: a classe `User` vira a tabela `` `user` ``, como no GORM. Isso importa ao apontar para um banco criado pelo Grails: o Grox enxerga as mesmas tabelas e colunas.

## Esquema (`dbCreate`)

`dataSource.dbCreate` aceita `create-drop`, `create`, `update`, `validate` e `none` (padrão).

Com `update`, o Grox é **seguro por padrão** *(extensão Grox)*: cria tabelas, colunas, chaves estrangeiras e índices, mas nunca altera colunas existentes, como o GORM sobre Hibernate 5. O Hibernate 7 puro faz `alter table ... modify column` quando tipo ou tamanho diferem, o que num banco legado pode truncar dados ou travar a subida num `ALTER` de tabela grande.

| Chave | Padrão | Efeito |
|---|---|---|
| `grox.schema.update` | `safe` | `full` volta ao comportamento do Hibernate 7. Vale também com `hibernate.dialect` explícito de MySQL, MariaDB, H2 ou PostgreSQL |
| `grox.schema.updateScript` | | Arquivo onde gravar o DDL que o `update` aplicaria, **sem executar** |
| `grox.schema.scriptOnly` | `false` | Com `updateScript`, encerra depois de gerar o script, antes do BootStrap |

Antes de apontar para um banco existente, gere o script e revise:

```bash
JAVA_OPTS="-Dgrox.schema.updateScript=/tmp/ddl.sql -Dgrox.schema.scriptOnly=true" build/install/app/bin/app
```

## Proxies e carga em lote

- **`grox.data.proxies: auto`** (padrão) *(extensão Grox)*: só as entidades alvo de many-to-one ganham proxy lazy. Nas demais, `load(id)` carrega na hora. `all` gera proxy para todas, como o GORM. A geração de proxies era o maior custo de subida do Hibernate.
- **`hibernate.default_batch_fetch_size: 16`** (padrão do Grox): associações lazy pendentes são carregadas juntas com `IN`, o que evita N+1 em listas. `1` volta ao comportamento do Hibernate.
- `GrailsHibernateUtil.unwrapIfProxy(x)` devolve a entidade real.

## Filtros do Hibernate (plugin hibernate-filter)

```groovy
class Produto {
    boolean ativo
    static hibernateFilters = {
        ativos(condition: 'ativo = true', default: true)
    }
}

Produto.list()                                        // só os ativos (filtro ligado por padrão)
Produto.withoutHibernateFilters { Produto.list() }    // todos
Produto.withoutHibernateFilter('ativos') { ... }
Produto.withHibernateFilters { ... }                  // religa os filtros padrão (dentro de um without...)
```

- A condição é SQL (nomes de coluna).
- Filtros com `default: true` ficam ligados em toda sessão. `enableHibernateFilter(nome)` e `disableHibernateFilter(nome)` agem na sessão atual.
- Filtros com parâmetros, `collection` e `aliasDomain` são ignorados com aviso.
- O filtro de multi-tenancy (`_tenantId`) é recusado por essa API (`IllegalArgumentException`). Para ver todos os tenants, use `Tenants.withoutId`.

## GORM Data Services

```groovy
import grails.gorm.services.Service

@Service(Ponto)                 // em grails-app/services
interface PontoService {
    Ponto get(Serializable id)
    List<Ponto> list(Map args)
    Long count()
    Ponto save(Ponto ponto)     // lança ValidationException se inválido
    void delete(Serializable id)
    Ponto findByNome(String nome)
}
```

A interface vira o bean `pontoService` (um proxy), e cada chamada roda numa transação. A convenção cobre `get`, `read`, `list`, `count`, `save`, `delete`, `find*`, `findAll*` e `count*`. Métodos fora dela lançam `UnsupportedOperationException`. Classes abstratas como Data Service não são suportadas.

## Modelo de mapeamento

O bean `hibernateDatastore` expõe o modelo do GORM, somente leitura: `mappingContext.getPersistentEntity('sample.Usuario')` devolve uma `PersistentEntity` com `PersistentProperty`, `OneToMany`, `ManyToOne` e `ManyToMany`. One-to-one aparece como `ManyToOne`.

Para diagnóstico, `grox.data.ormXmlFile: /tmp/orm.xml` grava o mapeamento gerado para o Hibernate. Se o Hibernate falhar na subida, esse XML vai para o log.

## Como funciona por baixo

As entidades são POGOs sem anotações. Na subida, o Grox lê `hasMany`, `belongsTo`, `mappedBy`, `transients`, `mapping` e `constraints`, gera um `orm.xml` (Jakarta Persistence 3.2, acesso por campo) e entrega ao Hibernate. O Hibernate sobe numa thread própria, em paralelo com o resto da aplicação (`grox.startup.parallel`).

## Diferenças para o Grails

| Recurso | Grox |
|---|---|
| `embedded`, herança de domain classes, coleções de tipos básicos além de `hasMany` simples | ❌ |
| `static indexes`, `fetchMode`, cache por entidade | Ignorados |
| `dbCreate: update` | Não altera colunas existentes (`grox.schema.update: safe`) |
| `load(id)` | Proxy só em entidades alvo de many-to-one (`grox.data.proxies: auto`) |
| Injeção em domain classes | Não, como no Grails 3.3+ |
| Multi-datasource, MongoDB e outros datastores | ❌ |

## Veja também

- [Consultas](consultas.md)
- [Validação, binding e mensagens](validacao.md)
- [Sessões, transações e eventos](transacoes.md)
- [Multi-tenancy](multi-tenancy.md)
- [Dados com @Entity](../sem-grails/dados.md)
- [API do GORM](../referencia/api-gorm.md)
