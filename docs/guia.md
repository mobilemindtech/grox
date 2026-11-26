# Guia: escrevendo uma aplicação Grox

O `sample-app/` é a referência executável de tudo o que está aqui.

## Estrutura

```
minha-app/
  build.gradle
  grails-app/
    conf/application.yml        configuração
    controllers/<pkg>/          *Controller.groovy, UrlMappings.groovy
    services/<pkg>/             *Service.groovy
    taglib/<pkg>/               *TagLib.groovy
    views/                      <controller>/<action>.gsp, layouts/, templates _x.gsp
    i18n/                       messages.properties, messages_en.properties...
    domain/<pkg>/               domain classes (GORM)
    init/<pkg>/                 Application.groovy, BootStrap.groovy
  src/main/groovy/              classes comuns
  src/main/resources/public/    arquivos estáticos (servidos em /static/**)
```

`build.gradle` com o plugin `org.grox.app` (build incluído em `gradle-plugin/`; veja `settings.gradle`):

```groovy
plugins { id 'org.grox.app' }
dependencies { implementation project(':framework') }   // ou a coordenada publicada do framework
application { mainClass = 'sample.Application' }
grox { precompileGsp = true }                            // padrão
```

**Nome do diretório** *(extensão Grox)*: em vez de `grails-app/`, o projeto pode usar `grox-app/`, que é detectado sem configuração, ou qualquer outro nome com `grox { appDir = 'app' }` (vale para os plugins `org.grox.app` e `org.grox.lib`). O plugin passa o nome (`-Dgrox.appDir`) à compilação, para a AST reconhecer os artefatos, ao `run` (views e assets com reload) e aos testes. Fora do Gradle, a chave `grox.appDir` faz o mesmo. O `sample-lib/` usa `grox-app/`.

O plugin aplica `groovy` e `application` e faz o seguinte:
- mapeia `grails-app/{controllers,services,domain,init,taglib,jobs,utils}` como fontes;
- mapeia `grails-app/conf` e `grails-app/i18n` como resources, e `grails-app/views` como `views/` no classpath;
- configura o `run` no diretório do projeto (reload de views);
- liga a [recarga de classes](#recarga-de-classes-em-desenvolvimento) no `run` (`grox { reload = false }` desliga) e a compilação incremental do Groovy;
- adiciona a tarefa **`compileGsp`**, que pré-compila as views no build.

Sobre o `compileGsp`:
- um erro de GSP **falha o build** com arquivo e linha;
- as classes vão para o classpath e para o jar;
- sem reload, o runtime usa as views pré-compiladas, então a primeira requisição não paga a compilação.

`Application.groovy`:

```groovy
package sample
import org.grox.GroxApp

class Application {
    static void main(String[] args) { GroxApp.run(Application, args) }
}
```

Os artefatos são procurados no **pacote de `Application`** e nos subpacotes, e também nos pacotes listados em `META-INF/grox/packages` no classpath. Os plugins `org.grox.app` e `org.grox.lib` geram esse índice (tarefa `groxPackageIndex`) com os pacotes dos fontes de `grails-app/`, então domains e services em outros pacotes (`br.com.x.pay`, numa app `br.com.x.app C`) também são encontrados.

### Bibliotecas de artefatos (`org.grox.lib`)

Domains, services, TagLibs e jobs compartilhados por várias aplicações (como um plugin do Grails, ou um `core` usado por app, API e jobs) ficam num projeto com o plugin `org.grox.lib`, no mesmo layout `grails-app/`, sem `Application`:

```groovy
// core/build.gradle
plugins { id 'org.grox.lib' }
dependencies { api project(':framework') }

// app/build.gradle
dependencies { implementation project(':core') }
```

- O plugin aplica `groovy` e `java-library`, mapeia `grails-app/` como na aplicação e gera o índice de pacotes; a AST aplica a API de cada artefato pelo diretório, como na app.
- A aplicação que depende da biblioteca descobre os artefatos dela pelo índice, sem configuração.
- As `messages*.properties` da biblioteca são mescladas com as da aplicação, e a aplicação prevalece.
- Views, assets, `application.yml` e a recarga de classes continuam só na aplicação: as classes da biblioteca não são recarregadas no `run`.

## Controllers

Um controller é uma classe `*Controller` em `grails-app/controllers`. Não estende nada: a AST aplica o trait `ControllerApi`. **Actions** são os métodos públicos e também propriedades closure no estilo Grails 2 (`def list = { ... }`), sem parâmetros (use `params`).

```groovy
class LivroController {
    static allowedMethods = [save: 'POST', delete: ['DELETE', 'POST']]  // senão, 405
    static responseFormats = ['html', 'json']    // formatos aceitos pelo respond
    static defaultAction = 'index'               // padrão: index, ou a única action
    static concurrency = 'pool'                  // 'pool' (padrão da app) ou 'request'
    static poolSize = 8                          // atores no modo pool
    static layout = 'admin'                      // layout padrão das views deste controller

    LivroService livroService                    // injeção por nome

    def index(Integer max) { [livros: livroService.listar(max ?: 10)] }  // Map → views/livro/index.gsp
    def show(Long id) { respond livroService.buscar(id) }                // null → 404
    def save(Livro livro) { ... }                                        // command object (params ou JSON)
}
```

### API disponível nas actions

| Membro | Descrição |
|---|---|
| `params` | `GroxParams`: query + form + variáveis da rota. Oferece `params.int('max', 10)`, `long`, `double`, `boolean`, `list('ids')`, `date('d', 'dd/MM/yyyy')` e acesso aninhado (`params.endereco.cidade` para a chave `endereco.cidade`) |
| `request` | `GroxRequest`: `method`, `getHeader()`, `JSON` (corpo parseado; `json` também, extensão Grox), `jsonRequest`, `xhr`, `forwardURI`, `get/setAttribute`, `cookies`, `getFile('campo')`, `getFiles('campo')`, `fileNames`, `multipart` |
| `response` | `GroxResponse`: `status`, `setHeader()`, `contentType`, `writer`, `outputStream`, `sendError()`, `sendRedirect()` |
| `session`, `flash` | Sessão em memória (cookie `GROXSESSIONID`) e escopo flash (dura até a próxima requisição) |
| `controllerName`, `actionName`, `grailsApplication`, `log` | Como no Grails |
| `render` | `render 'texto'`, `render text: 'x', contentType: 'text/plain', status: 201`, `render view: 'edit', model: [...]`, `render template: 'linha', model:/bean:/collection:, var:`, `render status: 204`, `render file: bytes, fileName: 'a.pdf'`, `render obj as JSON` (fecha o writer, como no Grails: renders seguintes são descartados) |
| `respond obj`, `respond obj, status: 201, model: [...], view: 'x'` | Formato `json` responde com o objeto serializado. `html` renderiza a view da action com o modelo `livro`, ou `livroList` + `livroCount` para coleções, mesclado com `model:` |
| `redirect` | `redirect action: 'show', id: 1, params: [...]`, `controller:`, `uri:`, `url:`, `permanent: true` |
| `withFormat { html { } json { } '*' { } }` | Executa o bloco do formato negociado |
| `bindData(obj, params, [include:/exclude:])` | Binding com conversão de tipos |
| `createLink(...)`, `g.<tag>(...)`, `message(code:, args:)` | Links, tags e i18n |
| `header('X-Nome', valor)` | Define um cabeçalho da resposta |

### Scaffold dinâmico e `RestfulController`

```groovy
class LivroController {
    static scaffold = Livro          // como o plugin scaffolding do Grails 3+
}
```

A AST faz o controller estender `grails.rest.RestfulController<Livro>`, que implementa `index` (`max` até 100), `show`, `create`, `save`, `edit`, `update` e `delete`, com os ganchos `queryForResource`, `createResource`, `listAllResources`, `countResources`, `saveResource`, `updateResource`, `deleteResource` e `getObjectToBind`. Formulários HTML (`form`, `multipartForm`) recebem o redirecionamento com a mensagem no flash (`default.created.message`, `default.updated.message`, `default.deleted.message`, `default.not.found.message`); os outros formatos, `respond` com o status (201, 200, 204, 404). Erros de validação dão 422, com a view `create`/`edit` ou os erros em JSON. O `scaffold = true` do Grails 2 é um erro de compilação.

As views que a app não tiver (`views/livro/index.gsp`, `show`, `create`, `edit`) são geradas dos templates de scaffold, no formato dos do Grails 3+ (`${propertyName}`, `${className}`, `\${...}` para as expressões da GSP), e compiladas uma vez. Para trocá-los, ponha os seus em `src/main/templates/scaffolding/` (o plugin Gradle os copia para `templates/scaffolding/` no classpath). As views usam as tags `f:` (subconjunto do plugin fields): `f:all`, `f:field`, `f:display` e `f:table`, com o widget pelo tipo e pelas constraints (`inList`, `email`, `url`, `password`, `maxSize`, `widget: 'textarea'`, associações como `<select>`, `hasMany` fora do formulário). Os rótulos vêm de `<domain>.<propriedade>.label` ou do nome natural; as mensagens padrão (`default.list.label`, `default.button.create.label`...) estão no framework em inglês e português.

### Parâmetros das actions

Os parâmetros da action são preenchidos por nome:
- **tipos simples** (números, `String`, `Boolean`, datas, enums, `UUID`) vêm convertidos de `params`, com `null` se inválidos ou `0` para primitivos;
- **`MultipartFile`** (`org.springframework.web.multipart.MultipartFile`) recebe o arquivo enviado no campo de mesmo nome;
- **`Map`** recebe o JSON do corpo ou `params`;
- **outras classes** são **command objects**: instanciados, injetados e preenchidos com o JSON do corpo (se `Content-Type: application/json`) ou com `params`. Depois, `validate()` é chamado se existir.

Uma action que escreve o corpo diretamente (`response.outputStream << bytes`, `pdfRenderingService.render(args, response)`) não cai na view padrão, como no Grails.

### Negociação de formato

A ordem é:
1. `params.format`, que vem da extensão na URL (`/livro/show/1.json`) ou do mapeamento (`format: 'json'`);
2. o cabeçalho `Accept`;
3. `Content-Type` JSON na requisição.

Sem preferência (`*/*`), vale o primeiro de `responseFormats` ou o primeiro bloco do `withFormat`.

## Domain classes (GORM)

Classes em `grails-app/domain`. A AST adiciona `Long id`, `Long version`, as coleções de `hasMany` (tipadas: `hasMany = [livros: Livro]` gera `Set<Livro> livros`), a API do GORM (a trait `GormEntity<Livro>`, genérica: `Livro.get(1)` é um `Livro`) e um construtor com Map, que converte tipos e ignora chaves desconhecidas, como `new Usuario(params)`.

**Autocomplete no IntelliJ IDEA** *(extensão Grox)*: os jars trazem descritores GDSL (`org/grox/data/gorm.gdsl`, `org/grox/web/grails.gdsl`, `org/grox/dsl/dsl.gdsl`), que o IDEA carrega do classpath. Domains de `grails-app/domain`, `grox-app/domain` ou com `@Entity` ganham a API do GORM tipada, `id`/`version`, `hasMany` com `addTo*`/`removeFrom*` e os dynamic finders de cada propriedade (sem as combinações `And`/`Or`). Controllers, interceptors, services, TagLibs, `init/` e jobs ganham a trait do diretório (com scaffold, o `RestfulController`); classes `@Handler`, o `HandlerApi`. Sem o GDSL (outro editor, `@CompileStatic`, `appDir` próprio), declare a trait: `implements GormEntity<Livro>`, `implements ControllerApi`. O `AutocompleteTest` roda os descritores com um `psiClass` simulado e falha se um método da `GormEntity` faltar no `gorm.gdsl`.

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
        version false
        id generator: 'uuid'         // identity (padrão), sequence, uuid (tipo UUID), assigned
        nome column: 'nm', length: 80, sqlType: 'varchar(80)'
        autoTimestamp false
    }

    def beforeInsert() { ... }      // também beforeUpdate, beforeDelete
}
```

- **Propriedades obrigatórias por padrão:** use `nullable: true` para liberar (exceto `dateCreated`/`lastUpdated`).
- **Associações:**
  - uma propriedade cujo tipo é outra domain class vira many-to-one;
  - `hasMany` vira one-to-many, bidirecional quando o outro lado tem a propriedade de volta (ou `belongsTo`);
  - com `belongsTo`, save e delete propagam da dona para os filhos, e filhos removidos da coleção são apagados;
  - many-to-many ainda não é suportado.
- **Esquema:** `dataSource.dbCreate` (`create-drop`, `create`, `update`, `validate`, `none`) gera o esquema. Tabelas e colunas ficam em snake_case (`dateCreated` → `date_created`), com a regra do GORM: só separa a maiúscula entre duas minúsculas (`valor10Meses` → `valor10meses_id`, `codigoERP` → `codigoerp`).

### API

| Estáticos | Instância |
|---|---|
| `get(id)`, `read`, `load`, `exists`, `getAll([ids])` | `save()`, `save(flush: true, failOnError: true, validate: false)` |
| `list()`, `list(max:, offset:, sort:, order:)`, `count()`, `first()`, `last()` | `delete()`, `delete(flush: true)` |
| `findBy*`, `findAllBy*`, `countBy*`, `listOrderBy*` | `validate()`, `hasErrors()`, `errors`, `clearErrors()` |
| `findWhere([..])`, `findAllWhere([..], [max:..])` | `addTo<Colecao>(obj ou Map)`, `removeFrom<Colecao>(obj)` |
| `find(hql, params)`, `findAll(hql, params, [max:..])`, `executeQuery`, `executeUpdate` (opções no mapa: `max`, `offset`, `lock`, `readOnly`, `cache`, `fetchSize`, `timeout`, `flushMode`) | `refresh()`, `discard()`, `attached`, `ident()` |
| `withTransaction { status -> }`, `withNewTransaction`, `withSession { session -> }`, `withNewSession` | `properties = params` |

Operadores dos dynamic finders: `Equal` (padrão), `NotEqual`, `LessThan`, `LessThanEquals`, `GreaterThan`, `GreaterThanEquals`, `Like`, `Ilike`, `NotLike`, `InList`, `NotInList`, `Between` (2 argumentos), `InRange` (Range), `IsNull`, `IsNotNull`, `IsEmpty`, `IsNotEmpty`.
- Combine os termos com `And` **ou** `Or` (não os dois).
- Um Map extra no fim aceita `max`, `offset`, `sort` e `order`.
- Argumentos String são convertidos para o tipo da propriedade, o que permite `findAllByIdadeGreaterThan(params.idade)`.
- `grails.databinding.dateFormats` (lista no yml, como no Grails) define os formatos de texto aceitos para `Date` no binding, tentados na ordem e sem leniência. Os formatos ISO do Grox (`yyyy-MM-dd`, `yyyy-MM-dd'T'HH:mm:ss`...) continuam valendo depois deles.
- Um bean `PropertyEditorRegistrar` (Spring) registra editores usados na conversão de texto do binding, como o formato de `Date` (`CustomDateEditor`, `StructuredDateEditor`). O registro é feito uma vez por requisição, então pode depender do locale. Texto que o editor recusa segue para a conversão padrão.
- Finders booleanos, como no GORM: `findAllAtivo()`, `findAllNotAtivo()`, `findAtivoByNome(n)`. A propriedade booleana vale `true` (`false` com `Not`) e não consome argumento; com `Or`, ela continua obrigatória (`ativo AND (a OR b)`). Argumentos extras que não são Map são ignorados (`findAllAtivo(true)`).

HQL aceita parâmetros nomeados (`[n: 'x']` com `:n`) ou posicionais (`[1]` com `?1`, ou um valor solto: `executeQuery(hql, id)`). Valores `GString` (`"%${q}%"`) são convertidos para `String`, como no GORM. Os rótulos herdados do Hibernate 5, que o Hibernate 7 recusa (`?0, ?1...` com base zero e `?` sem número), são renumerados para `?1, ?2...`.

### Consultas where

```groovy
int minimo = 18
def q = Livro.where { titulo ==~ 'G%' && (preco > minimo || autor.nome == params.autor) && !esgotado }
q.list(max: 10, sort: 'titulo'); q.find(); q.count(); q.exists()
q.where { situacao == Situacao.ATIVO }.sort('preco', 'desc').max(5).list()    // encadeia (imutável)
q.updateAll(preco: 10.0); q.deleteAll()
for (l in q) { ... }                                                            // iterável
Livro.whereAny { titulo == 'A'; titulo == 'B' }                                 // linhas com OR
Livro.findAll { preco < 20 }; Livro.findAll(max: 5) { ... }; Livro.find { titulo == 'X' }
```

| Operador | Critério |
|---|---|
| `==`, `!=` | igual / diferente (`== null` vira `isNull`) |
| `>`, `>=`, `<`, `<=` | comparação |
| `==~ 'G%'` | `like` |
| `=~ 'g%'` | `ilike` (sem diferenciar maiúsculas) |
| `in [..]` / `in 1..5` | `inList` / `between` |
| `!in` | `notInList` |
| `&&`, `\|\|`, `!`, parênteses | E, OU, NÃO |
| `esgotado` (booleano sozinho) | `esgotado == true` |
| `autor.nome == x`, `livro.autor.nome == x` | join pela associação (qualquer número de níveis, também por `hasMany`) |
| `preco > custo` | comparação entre propriedades (o lado direito é propriedade da domain class) |

- **Várias linhas:** no closure, são combinadas com AND.
- **Valores:** o lado direito pode ser variável local, `params.x`, chamada de método ou GString.
- **Compilação:** o closure é reescrito por uma transformação AST, como no GORM. Expressões não suportadas (por exemplo `nome.size() > 3`) ou propriedades inexistentes viram **erro de compilação**. Nos caminhos de associação, cada nível é conferido na domain class do nível anterior (`livro.autor.naoExiste` dá `Autor não tem 'naoExiste'`); um nível que não é domain class (embedded, tipo não resolvido) não é conferido.
- **Onde a transformação vale:** em `Classe.where { }`, `find { }` e `findAll { }` de domain classes, e em `where`/`whereAny` encadeados ou sobre uma variável `DetachedCriteria`.

### createCriteria / withCriteria

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
Livro.withCriteria { between('preco', 10.0, 50.0) }
Livro.createCriteria().list {
    projections { groupProperty('autor.nome'); count(); sum('preco') }   // [[nome, qtd, soma], ...]
}
```

- **Restrições:** `eq`, `ne`, `gt`, `ge` (`gte`), `lt`, `le` (`lte`), `like`, `ilike`, `between`, `'in'`/`inList`, `notIn`, `isNull`, `isNotNull`, `isEmpty`, `isNotEmpty`, `idEq`, e `eqProperty`/`neProperty`/`gtProperty`/`geProperty`/`ltProperty`/`leProperty`.
- **Combinações:** `and`, `or`, `not`.
- **Controle:** `order`, `maxResults`, `firstResult`.
- **Projeções:** `property`, `distinct`, `groupProperty`, `count`, `rowCount`, `countDistinct`, `sum`, `avg`, `min`, `max`. Com uma projeção, `list` devolve os valores; com várias, listas. Associações valem dentro de `projections` (`projections { autor { groupProperty('nome') } }` equivale a `groupProperty('autor.nome')`).

### Sessões e transações

- **Actions:** cada action tem uma sessão aberta durante a execução **e a renderização da view**, então associações lazy funcionam na GSP. Vale também para a página de login (`views/login/auth.gsp`) e as views de erro, renderizadas fora de uma action. Ela é propagada para services-ator.
- **Fora de actions:** BootStrap e código de teste ou de threads próprias funcionam sem configuração, porque cada chamada abre a sua sessão. Para agrupar operações, use `withTransaction` ou `withNewSession`.
- **`save()` e `delete()`:** participam da transação aberta, ou abrem e fazem commit de uma.
- **Alterações sem `save()`:** alterações em entidades carregadas **só são gravadas por `save()`** ou dentro de uma transação que faça commit. Depois de uma validação que falhou, chame `discard()` antes de continuar, para que outra transação da mesma requisição não grave o estado inválido.

### Binding

- `new Usuario(params)`, `bindData(u, params)`, `u.properties = params` e os argumentos de action **não** vinculam `id`, `version`, `dateCreated` nem `lastUpdated`.
- **`bindable: false`** nas constraints (domain classes e command objects) tira a propriedade do binding, como no Grails. Um `include` explícito no `bindData` ainda a vincula. Use em campos que o usuário não pode escolher, como tenant, papéis e flags de conta em telas de autoedição:
  ```groovy
  static constraints = {
      userTenantId bindable: false
      enabled bindable: false
  }
  ```
  Numa tela de administração que precisa desses campos, use `bindData(user, params, [include: ['enabled', ...]])`.
- Strings são aparadas, e string vazia vira `null`.
- **Valor que não converte** (ex. `idade=abc`) deixa a propriedade `null` e registra um erro `typeMismatch` em `errors`.
  - O erro **sobrevive ao `validate()`**, e o campo não recebe também o erro de nullable.
  - Códigos: `typeMismatch.<Classe>.<prop>`, `typeMismatch.<prop>`, `typeMismatch.java.lang.Integer`, `typeMismatch`.
  - Mensagens padrão em pt e en.
- Associações aceitam `grupo.id=3` (params), `{"grupo": {"id": 3}}` (JSON) ou `grupo=3`. Id vazio ou `null` limpa a associação.
- Argumento de action do tipo domain: se a requisição traz `id`, a entidade é **carregada** e recebe o binding (`null` se não existir); senão é criada uma nova. Nos dois casos `validate()` já foi chamado, então basta checar `usuario.hasErrors()`.

### Validação e mensagens

`errors` expõe `hasErrors()`, `errorCount`, `allErrors`, `fieldErrors`, `getFieldError('campo')`, `hasFieldErrors('campo')`, `rejectValue(campo, código)` e `reject(código)`.

Cada `FieldError` tem estes códigos, do mais específico ao mais genérico: `sample.Usuario.email.unique`, `usuario.email.unique`, `default.not.unique.message`. Os argumentos são `{0}` propriedade, `{1}` classe, `{2}` valor e `{3}...` os parâmetros da constraint. Para personalizar, basta definir a mensagem em `messages.properties`, por exemplo `usuario.email.unique=Já existe um usuário com o e-mail {2}`. O framework traz mensagens padrão em inglês e português.

| Constraint | Código |
|---|---|
| `nullable` | `nullable` |
| `blank` | `blank` |
| `size` | `size.toosmall` / `size.toobig` |
| `minSize` / `maxSize` | `minSize.notmet` / `maxSize.exceeded` |
| `min` / `max` | `min.notmet` / `max.exceeded` |
| `range` | `range.toosmall` / `range.toobig` |
| `inList` | `not.inList` |
| `matches` | `matches.invalid` |
| `email` | `email.invalid` |
| `url` | `url.invalid` |
| `unique` (true, propriedade ou lista de escopo) | `unique` |
| `validator` | `validator.invalid`, ou o código retornado (`'meuCodigo'` ou `['meuCodigo', arg]`) |

Formulário com erros:

```html
<g:hasErrors bean="${usuario}">
  <ul><g:eachError bean="${usuario}" var="erro"><li><g:message error="${erro}"/></li></g:eachError></ul>
</g:hasErrors>
<p class="${hasErrors(bean: usuario, field: 'nome', 'com-erro')}">
  <g:textField name="nome" value="${fieldValue(bean: usuario, field: 'nome')}"/>
</p>
<g:select name="grupo.id" from="${grupos}" optionKey="id" optionValue="nome" value="${usuario?.grupo?.id}" noSelection="${['': '—']}"/>
```

Em JSON, `respond usuario.errors, status: 422` produz `{"errors": [{"object", "field", "rejected-value", "message"}]}`. Entidades são serializadas **rasas**: as propriedades, mais `{id}` para as associações.

Command objects que não são domain classes podem usar `implements grails.validation.Validateable`. Nesse caso as constraints funcionam do mesmo jeito, também com propriedades obrigatórias por padrão.

### Eventos, listeners e dirty checking

Métodos `beforeInsert`, `beforeUpdate` e `beforeDelete` da domain class são chamados pelo `save()`/`delete()`. Para lógica fora da entidade (o `UserPasswordEncoderListener` gerado pelo spring-security, por exemplo), use um bean com métodos `@Listener`:

```groovy
class UserPasswordEncoderListener {
    SpringSecurityService springSecurityService

    @Listener(User)
    void onPreUpdateEvent(PreUpdateEvent event) {
        User u = event.entityObject as User
        if (u.isDirty('password')) event.entityAccess.setProperty('password', springSecurityService.encodePassword(u.password))
    }
}
```

O bean é registrado no `resources.groovy`, ou o método fica em um service. Listeners que estendem `AbstractPersistenceEventListener` (o estilo do GORM, com `onPersistenceEvent` e `supportsEventType`) também funcionam, registrados no `resources.groovy` com `ref('hibernateDatastore')` no construtor ou como services. Os eventos são `PreInsertEvent`, `PreUpdateEvent`, `PreDeleteEvent` e os `Post*` (pacote `org.grails.datastore.mapping.engine.event`). `event.cancel()` num `Pre*` cancela a gravação.

O dirty checking compara com o estado carregado do banco: `isDirty()`, `isDirty('prop')`, `getPersistentValue('prop')` e `getDirtyPropertyNames()`. Uma instância nova ou destacada conta como alterada.

### Many-to-many

```groovy
class Turma { static hasMany = [alunos: Aluno] }
class Aluno { static belongsTo = Turma; static hasMany = [turmas: Turma] }
```

O dono é `Turma`, o lado de quem o outro faz `belongsTo`. A tabela de junção é `turma_alunos` (colunas `turma_id`, `aluno_id`). `turma.addToAlunos(a)` atualiza os dois lados em memória, e a gravação acontece pelo lado dono.

### One-to-one com `hasOne`

```groovy
class Rosto { static hasOne = [nariz: Nariz] }
class Nariz { Rosto rosto }
```

A chave estrangeira fica no filho (`nariz.rosto_id`), como no GORM; o dono propaga save e delete (cascade all).

**Sem `hasOne`** (`class Carro { Motor motor }` e `class Motor { static belongsTo = [carro: Carro] }`): como no GORM, a chave fica no dono (`carro.motor_id`, com cascade do dono) e `Motor.carro` é o lado inverso, sem coluna `carro_id`. Se o dono também tem `hasMany` do tipo do filho, ou mais de uma propriedade desse tipo, o `belongsTo` continua many-to-one.

**Filho multi-tenant** (`class Anamnese implements MultiTenant<Anamnese>`): o `@TenantId` do Hibernate 7 filtra também a carga por chave, e no one-to-one inverso um filho ausente dava `EntityFilterException`, porque o Hibernate não distingue "ausente" de "filtrado". Nesse caso o Grox mapeia a propriedade do dono como many-to-one só leitura, com `join-formula` (`(select min(c.id) from anamnese c where c.cliente_id = id)`) e `not-found IGNORE`: filho ausente vira `null`, como no GORM. A carga é eager e em lote (`default_batch_fetch_size`). O Grox faz o cascade: grava o filho depois do pai (no `save()` e no persist por cascade de outra entidade) e o apaga antes do pai. Limites: a fórmula usa os nomes físicos do `snake_case` (tabela ou coluna com palavra reservada não é escapada), e atribuir `null` ao hasOne não apaga o filho (sem orphan removal).

### Filtros do Hibernate (plugin hibernate-filter)

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

A condição é SQL (nomes de coluna). Filtros com parâmetros, `collection` e `aliasDomain` são ignorados com aviso. Filtros com `default: true` ficam ligados em toda sessão. `enableHibernateFilter(nome)`/`disableHibernateFilter(nome)` agem na sessão atual. O filtro de multi-tenancy do Hibernate (`_tenantId`) é recusado por essa API (`IllegalArgumentException`): para ver todos os tenants, use `Tenants.withoutId`.

### GORM Data Services

```groovy
@Service(Ponto)                 // grails.gorm.services.Service, em grails-app/services
interface PontoService {
    Ponto get(Serializable id)
    List<Ponto> list(Map args)
    Long count()
    Ponto save(Ponto ponto)     // lança ValidationException se inválido
    void delete(Serializable id)
    Ponto findByNome(String nome)
}
```

Vira o bean `pontoService` (proxy), e cada chamada roda numa transação. Métodos fora dessa convenção lançam `UnsupportedOperationException`.

## Sessão HTTP

`session.x = valor` e `session.getAttribute('x')` funcionam em qualquer store. O cookie é `GROXSESSIONID` (HttpOnly, SameSite=Lax).

```yaml
grox:
  session:
    store: redis          # memory (padrão) ou redis
    timeout: 1800         # segundos de inatividade
    redis:
      url: redis://localhost:6379
      keyPrefix: 'grox:session:'
```

- **`memory`:** um nó só; as sessões ficam na memória do processo.
- **`redis`:** o cliente é o Lettuce. Cada sessão é um hash `grox:session:<id>` com TTL igual ao timeout, então ela sobrevive a reinícios e é compartilhada entre nós.
  - **Gravação:** só ao fim da requisição, e só do que mudou: atributos alterados, removidos e os **lidos com valor mutável**, para que `session.carrinho << item` também seja salvo. A autenticação e o flash não são regravados só por terem sido lidos: valores que implementam `org.grox.session.SessionValue` dizem se mudaram (`isModified()`).
  - **Uma ida ao Redis por gravação:** um script Lua faz HSET, HDEL e EXPIRE de forma atômica, e só grava uma sessão existente se a chave ainda existir. Assim, um logout (ou a expiração) durante uma requisição concorrente não é desfeito quando ela termina.
  - **Serialização:** Java, então **todo atributo precisa ser `Serializable`** (senão aparece `NotSerializableException` com o nome do atributo). Não guarde entidades do GORM na sessão, guarde o id.
  - **Classes aceitas na leitura:** JDK comum (`java.lang`, `java.util`, `java.time`, `java.math`), o framework, `GString` e os pacotes da aplicação: o da classe `Application`, o `grails.codegen.defaultPackage` e a lista `grox.session.serialization.allow`. Qualquer outra classe é recusada e o atributo é ignorado, com aviso no log. Isso impede que quem consiga gravar no Redis execute código com um "gadget" de biblioteca.
- **Login:** troca o id da sessão (proteção contra session fixation) e gera um token CSRF novo. O mesmo vale para `springSecurityService.reauthenticate` e para `SecurityContextHolder.context.authentication = ...` com outro usuário. O logout a invalida.
- **Cookie:** `HttpOnly` e `SameSite=Lax`. `Secure` conforme `grox.session.cookie.secure`: `auto` (padrão; em HTTPS, inclusive atrás de proxy confiável), `true` ou `false`.

## Segurança (estilo spring-security-core)

Ativa quando existe `grails.plugin.springsecurity` no `application.yml`. As chaves são as do spring-security-core e do spring-security-rest. As marcadas com *Grox* são extensões deste framework.

```yaml
grails:
  plugin:
    springsecurity:
      rejectIfNoRule: true                    # sem regra = negado (padrão)
      userLookup:
        userDomainClassName: sample.auth.User
        authorityJoinClassName: sample.auth.UserRole
        tenantPropertyName: tenantId          # Grox: tenant do usuário
      authority:
        className: sample.auth.Role
      users:                                  # Grox: usuários em memória
        - {username: suporte, password: '{noop}suporte', roles: [ROLE_ADMIN]}
      useBasicAuth: true
      stateless: ['/api/**']                  # Grox: sem sessão nem CSRF (só Bearer/Basic)
      csrf.enabled: true                      # Grox (padrão true)
      rest:
        active: true
        token.storage.jwt.secret: '...no mínimo 32 bytes...'
        token.storage.jwt.expiration: 3600
      interceptUrlMap:
        - {pattern: '/', access: [permitAll]}
        - {pattern: '/static/**', access: [permitAll]}
        - {pattern: '/api/**', httpMethod: GET, access: ['isAuthenticated()']}
        - {pattern: '/api/**', access: [ROLE_ADMIN]}
        - {pattern: '/admin/**', access: ["hasRole('ADMIN') and isFullyAuthenticated()"]}
        - {pattern: '/**', access: ['isAuthenticated()']}
```

### Regras de acesso

1. `@Secured` da **action**, depois `@Secured` do **controller**. Por exemplo `@Secured('ROLE_ADMIN')` ou `@Secured(value = ['ROLE_A', 'ROLE_B'], httpMethod = 'POST')`.
2. Senão, a **primeira** regra de `interceptUrlMap` que casar com o caminho e o método; depois as de `controllerAnnotations.staticRules` (lista de `[pattern:, access:, httpMethod:]` ou o mapa `'/caminho': ['regra']`, como o `s2-quickstart` gera no `application.groovy`).
3. Senão, `rejectIfNoRule`.

As URLs do próprio login (`/login/auth`, `/login/authenticate`, `/logoff`, `/api/login` e, sem controller próprio, `/logout`) são sempre liberadas.

**Sem a cadeia de segurança** (nem leitura da sessão), para não pagar o custo em arquivos:
- **`filterChain.chainMap` com `filters: 'none'`:** mesma chave do spring-security-core. Por exemplo: `- {pattern: '/public/**', filters: 'none'}`.
- **`/assets/**`:** sempre, como no Grails, em que o filtro do asset-pipeline roda antes do Spring Security.
- **Estáticos (`/static/**`) em GET/HEAD cuja regra libera anônimos** (ex.: `permitAll`): o arquivo não depende de quem está logado. Um estático protegido por regra continua passando pela segurança.

**Listas de filtros no `chainMap`** (`'JOINED_FILTERS,-x,-y'` ou nomes explícitos), como no spring-security-core/rest. Vale a **primeira** entrada que casar com o caminho; sem `chainMap`, ou sem entrada que case, a cadeia é a completa. O Grox não tem os filtros em si, mas cada um corresponde a uma etapa:

| Filtro | Sem ele |
|---|---|
| `securityContextPersistenceFilter` | sem sessão (como `stateless`: sem login pela sessão nem CSRF) |
| `restTokenValidationFilter` | o token REST é ignorado (a requisição segue como anônima) |
| `basicAuthenticationFilter` | o HTTP Basic é ignorado |
| `anonymousAuthenticationFilter` | quem não se autenticou recebe 401 (ou vai para o login), mesmo em regra `permitAll`, como o `FilterSecurityInterceptor` |
| `filterInvocationInterceptor` | sem autorização (nem `interceptUrlMap`, nem `@Secured`) |
| `exceptionTranslationFilter` | sem login: 401 em vez de redirecionar o navegador para a página de login |

Exemplo do spring-security-rest (o app-c-api usa este):

```yaml
filterChain:
  chainMap:
    - {pattern: '/api/**', filters: 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-authenticationProcessingFilter,-securityContextPersistenceFilter,-rememberMeAuthenticationFilter'}
    - {pattern: '/**', filters: 'JOINED_FILTERS,-restTokenValidationFilter,-restExceptionTranslationFilter'}
```

`/api/**` só aceita token (401 sem ele, antes da action) e não usa sessão; o resto do site usa a sessão e ignora tokens. As URLs do próprio login (`/api/login`, `/login/authenticate`...) funcionam em qualquer cadeia.

- **Padrões Ant:** `/admin/**` casa com `/admin` e tudo abaixo; `*` casa com um segmento; `?` com um caractere.
- **Caminho canônico das actions:** `/admin.json` e `/admin/` roteiam como `/admin`. Numa action, vale a regra que vier primeiro entre a do caminho e a do caminho sem formato e sem barra final. Assim `/admin.json` não escapa de `/admin/**` caindo no `/**` (no Grails, o `AntPathMatcher` deixa essa brecha), e uma regra explícita para `/x.json` continua valendo.
- **Console e actuator** (`/console`, `/actuator/**`) conferem o próprio acesso (`grox.console.access`, `grox.management.metricsAccess`) mesmo numa cadeia do `chainMap` sem `filterInvocationInterceptor` ou com `filters: 'none'`.
- **`access`:** uma lista que concede se **qualquer** item conceder. Itens aceitos:
  - `permitAll`, `denyAll`;
  - `isAuthenticated()`, `isFullyAuthenticated()`, `isAnonymous()`;
  - `ROLE_X`;
  - `hasRole('X')` (com ou sem o prefixo `ROLE_`), `hasAnyRole(...)`, `hasAuthority(...)`, `hasAnyAuthority(...)`;
  - os legados `IS_AUTHENTICATED_*`;
  - combinações com `and`, `or`, `!` e parênteses.
- **Validação na subida:** uma expressão inválida impede a aplicação de subir.
- **Sem permissão:**
  - usuário não autenticado vindo de navegador: redirect para o login, e a URL pedida é salva para depois do login;
  - API, XHR, JSON ou caminho stateless: `401` com `WWW-Authenticate`;
  - usuário autenticado sem a role: `403`, que usa o mapeamento `"403"` do UrlMappings se existir.
- **Caminhos com `.` ou `..`:** recebem `400`, para não contornar as regras.

### Mecanismos de autenticação

| Mecanismo | Como |
|---|---|
| **Formulário e sessão** | `GET /login/auth` mostra a página. `POST /login/authenticate` recebe `username`, `password` e `_csrf`; no sucesso redireciona para a URL salva ou para `successHandler.defaultTargetUrl`, na falha para `/login/auth?login_error=1`. O logout é `POST /logoff` (com `_csrf`). A página de login é `views/login/auth.gsp` se existir, com o modelo `postUrl`, `usernameParameter`, `passwordParameter`, `csrfParameter`, `csrfToken` e `errorMessage`; senão uma página padrão. Um `LoginController` da aplicação também é respeitado. Sem ele, o Grox faz o papel do controller do plugin: `/login` e `/login/index` redirecionam para `/login/auth` (ou para o `defaultTargetUrl`, se já logado), `/login/authfail` é aceito, e a falha de login coloca a mensagem traduzida em `flash.message`. A página de login é renderizada com `params.controller`/`controllerName` = `login` e `params.action`/`actionName` = `auth` (templates relativos resolvem em `views/login/`), como pelo `LoginController.auth`. Da mesma forma, sem um `LogoutController` da aplicação, `/logout` e `/logout/index` (inclusive com id, como em `<g:link controller="logout">`) fazem o logout como o `logout.filterProcessesUrl` (`/logoff`): com `logout.postOnly: true` (padrão), respondem 405 a GET, e o POST com sessão exige o `_csrf` |
| **HTTP Basic** | `useBasicAuth: true`. Autentica a cada requisição, sem sessão |
| **JWT** (spring-security-rest) | `POST /api/login` com `{"username", "password"}` devolve `{"username", "roles", "token_type": "Bearer", "access_token", "expires_in"}`. Depois, `Authorization: Bearer <token>`. HS256, com as claims `sub`, `roles`, `tenant`, `uid` e `exp`. Token inválido ou expirado dá `401` |

- **Contas:** falham no login se `enabled` for false ou se `accountLocked`, `accountExpired` ou `passwordExpired` forem true, cada caso com sua mensagem i18n (`springSecurity.errors.login.*`).
- **`passwordEncoder` da aplicação:** um bean `passwordEncoder` que implemente o `PasswordEncoder` do Spring Security (`DelegatingPasswordEncoder`, `BCryptPasswordEncoder`, `MessageDigestPasswordEncoder`, ou um próprio) substitui o `password.algorithm` no login e no `encodePassword`. Se ele pedir `upgradeEncoding(hash)` (hash antigo), o login bem-sucedido chama o bean `userDetailsPasswordService` (`UserDetailsPasswordService.updatePassword(user, novoHash)`), como o `DaoAuthenticationProvider`. Os dois também são lidos de um `DaoAuthenticationProvider` declarado em `doWithSpring`/`resources.groovy`.
- **Senhas:** no formato do `DelegatingPasswordEncoder` do spring-security-core, para o Grails e o Grox lerem o mesmo banco. `password.algorithm: bcrypt` (padrão) grava `{bcrypt}$2a$...` e lê também `$2a$...` sem prefixo. `SHA-256`, `SHA-1`, `SHA-512` ou `MD5` gravam `{SHA-256}hash` sem sal, como o Grails 8, com `password.hash.iterations` repetições (em base64 com `password.encodeHashAsBase64`), e leem também `{SHA-256}{sal}hash`. `{noop}texto` só para desenvolvimento. Para codificar: `springSecurityService.encodePassword(raw)` ou `org.grox.security.Passwords.encode(raw)`.

### Usuários

Os usuários são procurados em três fontes, nesta ordem:
1. um bean `userDetailsService` da aplicação (service ou `resources.groovy`): implementando `org.grox.security.UserDetailsService`, ou no estilo do Spring Security, `UserDetailsService`/`GrailsUserDetailsService` devolvendo `UserDetails` (como `GrailsUser` com `SimpleGrantedAuthority`). No estilo Spring, `UsernameNotFoundException` (e `NoStackUsernameNotFoundException`) significa usuário inexistente, e id/tenant vêm das propriedades `id`/`tenantId` do `UserDetails`;
2. os `users` do yml;
3. as domain classes de `userLookup`.

Domain classes no padrão do `s2-quickstart`:

```groovy
class User {
    String username; String password; String tenantId
    boolean enabled = true; boolean accountExpired; boolean accountLocked; boolean passwordExpired
    static constraints = { username blank: false, unique: true; password blank: false; tenantId nullable: true }
    Set<Role> getAuthorities() { UserRole.findAllByUser(this)*.role as Set }
    def beforeInsert() { encodePassword() }
    def beforeUpdate() { encodePassword() }
    protected void encodePassword() { if (!Passwords.isEncoded(password)) password = Passwords.encode(password) }
}
class Role { String authority }
class UserRole { User user; Role role }
```

A junção (`authorityJoinClassName`) é descoberta pelas propriedades do tipo `User` e `Role`. Sem junção, é usada a propriedade `authorities` do usuário.

### Evento de criação de token (spring-security-rest)

```groovy
class TokenCreationListener implements ApplicationListener<RestTokenCreationEvent> {
    void onApplicationEvent(RestTokenCreationEvent event) {
        log.info "login de ${event.accessToken.principal.username}"
    }
}
// resources.groovy: tokenCreationListener(TokenCreationListener)
```

A cada token emitido em `POST /api/login`, o Grox publica o `RestTokenCreationEvent` para os beans `ApplicationListener` desse tipo (síncrono; uma falha no listener vai para o log e não impede o login). Durante o evento, o usuário já está no contexto de segurança (`SecurityContextHolder`, `springSecurityService.currentUser`), e com isso o resolver de tenant da app enxerga o tenant dele. No Grails o contexto ainda está vazio nesse ponto, mas lá o tenant ausente não impede carregar associações por id. `applicationContext.publishEvent(evento)` entrega eventos da própria aplicação do mesmo jeito.

### Nos controllers, services e views

- **`springSecurityService`** (injetável): `currentUser` (a domain class do usuário), `principal` (`GroxUser`: username, `authorities*.authority`, `roles`, id, tenantId; sem login, a String `'anonymousUser'`, como no Spring Security), `loggedIn`, `authentication`, `encodePassword()`, `reauthenticate(username)`.
- **Nos controllers:** também `isLoggedIn()`, `getPrincipal()` e `getAuthenticatedUser()`.
- **`SpringSecurityUtils`:** `ifAllGranted('ROLE_A,ROLE_B')`, `ifAnyGranted`, `ifNotGranted`, `principalAuthorities`.
- **Tags `sec:`:**
  - `ifLoggedIn`, `ifNotLoggedIn`;
  - `ifAllGranted`, `ifAnyGranted`, `ifNotGranted` (roles);
  - `username`, `loggedInUserInfo field="x"`;
  - `access expression="..."` / `access url="/admin"` e `noAccess`;
  - `csrfInput`, `csrfToken`.
- **CSRF:** ativo por padrão (`grails.plugin.springsecurity.csrf.enabled`). O spring-security-core do Grails não tem CSRF, então apps migradas cujas views e AJAX não enviam o token podem desligá-lo com `csrf.enabled: false` (é o que o app-c-web faz). O `<g:form>` já inclui o `_csrf` quando o CSRF está ativo. Em AJAX, envie o cabeçalho `X-CSRF-TOKEN`. O CSRF vale para POST, PUT, PATCH e DELETE com sessão; não vale para Bearer, caminhos `stateless` e requisições anônimas **sem** cookie de sessão que não venham de outro site (POST de API: não há estado do usuário a forjar). Vindo de outro site pelo navegador (`Sec-Fetch-Site`/`Origin`), o token é exigido mesmo sem sessão: a action poderia criar a sessão e logar a vítima numa conta do atacante (login CSRF). O login por formulário sempre exige o token. Em caminhos stateless, Basic vindo de outro site também é recusado. Com **Basic**, também não vale, **exceto** quando a requisição vem de um navegador em outro site (`Sec-Fetch-Site: cross-site`/`same-site`, ou `Origin` diferente do `Host`): o navegador reenvia sozinho credenciais Basic guardadas. Clientes de API não mandam esses cabeçalhos e continuam sem CSRF.
- **LoginController próprio (spring-security-core):**
  - `SecurityContextHolder.context.authentication` (get, set e `clearContext()`);
  - `HttpSessionRequestCache`/`SavedRequest`;
  - `session[WebAttributes.AUTHENTICATION_EXCEPTION]` e `SpringSecurityUtils.SPRING_SECURITY_LAST_USERNAME_KEY`, com a exceção da falha de login (`BadCredentialsException`, `LockedException`, `DisabledException`...);
  - `SpringSecurityUtils.securityConfig` (ConfigObject com as chaves do plugin);
  - `authenticationTrustResolver`;
  - `sessionRegistry` (`SessionRegistryImpl` sobre as sessões ativas, memória ou Redis), declarado pela app no `resources.groovy`.
- **Services-ator:** a autenticação é propagada para eles, assim como a sessão de dados.

## Multi-tenancy (GORM, modo DISCRIMINATOR)

```yaml
grails:
  gorm:
    multiTenancy:
      mode: DISCRIMINATOR
      tenantResolverClass: org.grox.security.gorm.UserTenantResolver   # padrão com segurança ativa
```

Resolvers prontos: `UserTenantResolver` (tenant do usuário logado, extensão Grox), `FixedTenantResolver`, `SystemPropertyTenantResolver` e `SessionTenantResolver` (atributo `gorm.tenantId` da sessão HTTP; sem ele, `TenantNotFoundException`).

```groovy
class Tarefa implements MultiTenant<Tarefa> {
    String titulo
    String tenantId       // se omitida, a AST adiciona; mapping { tenantId name: 'empresa' } troca a propriedade
}
```

- **Coluna de tenant:** a propriedade vira a coluna de tenant, usando o suporte nativo do Hibernate (`@TenantId`).
  - No insert ela é preenchida com o tenant atual.
  - **Todas** as consultas são filtradas: `list`, `get`, `count`, finders, Criteria e HQL.
  - Registros de outro tenant simplesmente não existem: `get(id)` devolve `null`.
- **Binding:** `tenantId` nunca é vinculado por binding nem por JSON, e não aparece no JSON das entidades.
- **Sem tenant:** acessar uma classe `MultiTenant` sem tenant resolvido lança `TenantNotFoundException`, que vira `403` nas requisições. Classes comuns não são afetadas.
- **SQL nativo não é filtrado:** `createNativeQuery`/`createSQLQuery` e `groovy.sql.Sql` veem todas as linhas. Inclua `tenant_id = :tenant` na consulta, com `Tenants.currentId()`.
- **Resolvers:**
  - `org.grox.security.gorm.UserTenantResolver` (antes `org.grox.security.UserTenantResolver`) usa o `tenantId` do usuário logado (`userLookup.tenantPropertyName`) ou a claim `tenant` do JWT;
  - também existem `FixedTenantResolver`, `SystemPropertyTenantResolver` (`-Dgorm.tenantId`);
  - ou implemente `org.grails.datastore.mapping.multitenancy.TenantResolver`.

API:

| | |
|---|---|
| `Tenants.currentId()` / `currentIdOrNull()` | Tenant atual |
| `Tenants.withId('acme') { ... }`, `Tarefa.withTenant('acme') { }` | Executa com outro tenant (abre uma nova sessão se necessário) |
| `Tenants.withCurrent { }` | Executa com o tenant resolvido |
| `Tenants.withoutId { }` | Sem filtro: vê todos os tenants. Para gravar, atribua o `tenantId` explicitamente |
| `@WithoutTenant`, `@Tenant({ 'acme' })`, `@CurrentTenant` | Em services (classe ou método), combináveis com `@Transactional`. **Opcionais**: veja abaixo |

**Controllers e services não precisam de anotação**, como no GORM em modo DISCRIMINATOR:
1. a segurança autentica o usuário;
2. quando o código acessa o banco pela primeira vez na requisição, a sessão Hibernate é aberta e pede o tenant ao `tenantResolverClass`;
3. a partir daí, o Hibernate filtra as consultas e preenche o `tenantId` nos inserts.

As anotações só servem para exceções:
- `@WithoutTenant` vê todos os tenants;
- `@Tenant({ id })` força um tenant específico;
- `@CurrentTenant` apenas exige que haja um tenant resolvido.

No BootStrap não há usuário logado, então crie os dados com `Tenants.withId(...) { }`.

## Actions assíncronas

Como no Grails (`grails.async`), a action pode devolver uma **promise**. O ator do controller fica livre na hora e atende a próxima requisição; a resposta sai quando a promise completa.

```groovy
import static grails.async.Promises.*

def relatorio() {
    task { [total: Usuario.count()] }                 // Map → view relatorio.gsp, como uma action normal
}
def painel() {
    tasks(usuarios: { Usuario.count() }, tarefas: { Tarefa.count() })   // em paralelo
        .then { Map m -> respond m }
}
def importar() {
    task { servicoLento.processar(params.id); render text: 'ok' }
}
```

- **O que a action pode devolver:** `grails.async.Promise` (de `task`/`tasks`, ou `task { }` direto no controller), `CompletableFuture`/`CompletionStage` ou o `Awaitable` do Groovy.
- **Contexto das tarefas:** rodam em virtual threads com o contexto de quem as criou (`params`, `request`, usuário autenticado, tenant). **Cada tarefa tem a própria sessão de dados**, porque a Session do Hibernate não pode ser compartilhada entre threads. Entidades carregadas na action ficam desanexadas na tarefa; carregue os dados dentro dela.
- **Quando completa:** o fluxo normal continua com a view adiada ou padrão, os `after()`/`afterView()` dos interceptors e o layout.
- **Falhas:** um erro na tarefa gera 500, pelo mapeamento `"500"`. Passado o `grox.async.timeout` (padrão 60 s), a resposta é 503.
- **API `Promises`:** `task`, `tasks(Closure...)`, `tasks(List)`, `tasks(Map)`, `waitAll`, `onComplete`, `createBoundPromise`, `createPromise`.
- **API `Promise`:** `get()`, `get(timeout, unit)`, `then`, `onComplete`, `onError`, `isDone`.

**Quando usar:** esperas longas (serviços externos, processamento pesado, várias consultas em paralelo), principalmente em controllers do modo `pool`. Uma action síncrona bloqueando uma virtual thread é barata, mas **segura aquele ator**: com `poolSize` atores, só `poolSize` requisições daquele controller andam ao mesmo tempo.

## Upload de arquivos

```html
<g:uploadForm action="enviarFoto" id="${usuario.id}">
    <input type="file" name="foto"/> <g:submitButton name="enviar"/>
</g:uploadForm>
```

```groovy
def enviarFoto(Long id) {
    def f = request.getFile('foto')          // ou um parâmetro MultipartFile foto
    if (f && !f.empty) {
        usuario.foto = f.bytes               // originalFilename, contentType, size, inputStream, transferTo(file)
        usuario.fotoTipo = f.contentType
    }
}
```

- Os arquivos também ficam em `params`.
- O binding copia um `MultipartFile` para uma propriedade `byte[]` (arquivo vazio é ignorado) ou `MultipartFile`.
- O `_csrf` e o `_method` funcionam em formulários multipart.
- O limite de tamanho é o `grox.http.maxContentLength` (acima dele, 413). O arquivo fica em memória.
- Guardando o arquivo numa domain class: para `byte[]`, `maxSize` define o tamanho da coluna.

## Interceptors

Classes `*Interceptor` em `grails-app/controllers` (API do Grails 3+):

```groovy
class AuditoriaInterceptor {
    int order = HIGHEST_PRECEDENCE + 10         // menor roda primeiro no before (e por último no after)

    AuditoriaInterceptor() {
        matchAll().excludes(controller: 'erro')
        // match(controller: 'livro', action: 'show'), match(controller: ~/(autor|editora)/),
        // match(uri: '/api/**'), match(method: 'POST')
    }

    boolean before() { true }                   // false interrompe (renderize/redirecione antes)
    boolean after() { model.extra = 1; true }   // false impede a view; model e view podem ser alterados
    void afterView() { }                        // depois da renderização
}
```

- **Sem `match(...)`:** intercepta o controller de mesmo nome (`LivroInterceptor` → `livro`).
- **API disponível:** `params`, `request`, `response`, `session`, `flash`, `controllerName`, `actionName`, `render`, `redirect`, `header`, `log`, e injeção de services.
- **Execução:** os interceptors rodam depois da segurança e dentro do ator do controller.
- **`modelAndView`:** no `after()`, `modelAndView.model` e `modelAndView.viewName` (null quando a action não tem view); `throwable` traz a exceção da requisição.
- **Views adiadas:** dentro de uma action, `render(view:)`, `respond` (HTML) e a view padrão são **renderizados só depois dos `after()`**. Templates e `render text:` saem na hora.

## Operação (produção)

### Endpoints de gestão

Estilo Spring Boot Actuator, em `grox.management.basePath` (padrão `/actuator`):

| Endpoint | Conteúdo | Acesso |
|---|---|---|
| `/health` | `{"status": "UP", "checks": {"db": {...}, "redis": {...}}}` (cada módulo contribui com as suas verificações em `GroxModule.healthChecks`). Devolve 503 se algo estiver DOWN; `OUT_OF_SERVICE` durante o desligamento | público |
| `/info` | App, ambiente, versões do Groovy e do Java, horário de início | público |
| `/metrics` | Requisições ativas e totais, contagem por classe de status, por rota (`controller.action`, `static`, `management`, `security`) com count, errors, avgMs e maxMs, e memória da JVM | `grox.management.metricsAccess` (padrão `[ROLE_ADMIN]` com segurança) |
| `/prometheus` | As mesmas métricas no formato de exposição do Prometheus | idem |

### Access log

`grox.accessLog.enabled: true` registra uma linha por requisição no logger `grox.access`:
- `format: text` (padrão), no estilo `127.0.0.1 admin "GET /x" 200 12ms usuario.index`;
- `format: json`, uma linha JSON por requisição.

### Desligamento gracioso

O `close()` (também chamado pelo shutdown hook no `SIGTERM`) faz, em ordem:
1. muda o `/health` para `OUT_OF_SERVICE`;
2. para de aceitar conexões;
3. espera as requisições em andamento **terminarem de ser escritas**, até `grox.shutdown.timeout` (padrão 30 s); nesse período as respostas saem com `Connection: close`;
4. encerra atores, sessões e banco.

### JWT: refresh e logout

- **Login:** `POST /api/login` também devolve `refresh_token` (validade em `rest.token.storage.jwt.refreshExpiration`, padrão 30 dias). Ele só serve para renovar: não é aceito como access token.
- **Renovação:** `POST /oauth/access_token` com `grant_type=refresh_token&refresh_token=...` (formulário ou JSON) gera um novo access token. O usuário é **recarregado**, então roles e contas desativadas valem na hora. Refresh inválido dá 403.
- **Logout:** `POST /api/logout` (com `Authorization: Bearer`) revoga o access token e, se enviado, o `refresh_token`.
- **Onde ficam as revogações:** em memória ou no Redis (`grox.security.tokenRevocation.store`, padrão igual ao `grox.session.store`), até a expiração de cada token.

### Tokens em GORM e cabeçalho `X-Auth-Token`

Como no spring-security-rest, onde o token fica e como ele chega são configurações independentes. O padrão continua sendo **JWT com `Authorization: Bearer`**.

```yaml
grails:
  plugin:
    springsecurity:
      rest:
        token:
          storage:
            useGorm: true                     # token opaco numa domain class, no lugar do JWT
            gorm:
              tokenDomainClassName: app.AuthenticationToken
              tokenValuePropertyName: tokenValue   # padrão
              usernamePropertyName: username       # padrão
          validation:
            useBearerToken: false             # padrão true (Authorization: Bearer)
            headerName: X-Auth-Token          # padrão; usado quando useBearerToken é false
```

```groovy
class AuthenticationToken {
    String tokenValue
    String username
}
```

- **Login:** `POST /api/login` grava um registro e devolve `{"username", "roles", "token_type": "Bearer", "access_token"}`, sem `expires_in` nem `refresh_token`. O token é aleatório (160 bits, base 32, como o `SecureRandomTokenGenerator` do plugin) e vale até o logout.
- **Validação:** o token é procurado no banco e o usuário é **recarregado** pelo `username`, numa sessão de dados só e com as roles numa consulta. Token inexistente dá `401`.
- **Cache (extensão Grox):** o usuário de cada token fica em memória por `rest.token.storage.gorm.cacheSeconds` (padrão 30; `0` desliga), para não repetir as consultas a cada requisição. Roles novas e contas desativadas, bloqueadas ou expiradas passam a valer depois desse prazo. O logout invalida o cache na hora no mesmo nó; em outros nós, vale depois do prazo.
- **Logout:** `POST /api/logout`, com o token no mesmo cabeçalho, apaga o registro.
- **Sem refresh:** `/oauth/access_token` só existe com JWT.
- **`useBearerToken` e `headerName` também valem para o JWT:** com `useBearerToken: false`, o JWT passa a ser lido do cabeçalho configurado, e `Authorization: Bearer` é ignorado.

## Jobs (substituto do plugin Quartz)

Classes `*Job` em `grails-app/jobs`, com `log` e injeção de dependências por nome, como os services:

```groovy
class ContasReceberJob {
    def mailWriterService
    static concurrent = false             // não dispara de novo enquanto a execução anterior roda
    static triggers = {
        cron name: 'contasReceber', cronExpression: '0 0 7 * * ?'      // seg min hora dia mês dia-da-semana
        simple name: 'fila', repeatInterval: 60000L, startDelay: 5000L
    }
    def execute() { ... }
}
```

- Cada job é um bean com o nome completo da classe (`grailsApplication.mainContext.getBean('pacote.MeuJob')`), injetado mesmo com o agendador desligado, como no plugin quartz; `grailsApplication.getArtefacts(JobArtefactHandler.TYPE)` devolve `GrailsJobClass` (`shortName`, `fullName`, `group`, `concurrent`, `sessionRequired`, `enabled`, `description` e `triggers`, o mapa nome → trigger com `triggerAttributes`). Assim uma tela de status lista os jobs e executa um deles à mão.
- `job.triggers()` devolve o último trigger declarado como no plugin quartz (`Expando` com `triggerAttributes.cronExpression`/`repeatInterval`), para telas de status dos jobs.
- O cron segue o formato do Quartz (com segundos; `?`, listas, intervalos, passos e nomes `MON-FRI`/`JAN`). `L`, `W` e `#` não são suportados.
- Cada execução roda numa virtual thread, com sessão de dados disponível. Para tenant, use `Tenants.withId`.
- Os jobs começam depois do `BootStrap`. Desligue com `grox.jobs.enabled: false` (ou `quartz.autoStartup: false`).

## Beans (`resources.groovy`)

`grails-app/conf/spring/resources.groovy` aceita o subconjunto do DSL do Spring que é usado na prática:

```groovy
beans = {
    userPasswordEncoderListener(UserPasswordEncoderListener)
    cliente(ApiCliente, 'https://api.exemplo.com') { timeout = 30; mailService = ref('mailService') }
}
```

- O bloco de configuração pode receber a definição do Spring: `{ bean -> bean.autowire = true; bean.scope = 'singleton' }`. Ela é aceita e ignorada, porque o Grox sempre injeta por nome.
- `ref('outro')` numa propriedade pode apontar para um bean declarado depois; a referência é resolvida no fim do bloco. No construtor, o bean referenciado precisa vir antes.
- Os beans também podem vir do `doWithSpring()` da classe `Application` (que estende `GrailsAutoConfiguration`) e dos descritores de plugin (`class XGrailsPlugin extends grails.plugins.Plugin`) das bibliotecas `org.grox.lib`. A ordem é: plugins, `Application.doWithSpring()` e por fim o `resources.groovy`. O Grox também chama `doWithDynamicMethods()`, `doWithApplicationContext()` (com o contexto pronto) e `onShutdown(event)` dos plugins.
- Métodos `@PostConstruct` (`jakarta.annotation`) de services e beans rodam depois que todos estão injetados.

Os beans recebem injeção por nome, como os services, e ficam disponíveis para injeção e em `Holders.applicationContext.getBean(...)`. O framework também oferece estes beans:

| Bean | O que é |
|---|---|
| `grailsApplication` | A aplicação |
| `messageSource` | API do Spring: `getMessage(code, args, default, locale)` |
| `groovyPageRenderer` | `render(template:/view:, model:)` para String, dentro ou fora de requisições |
| `assetResourceLocator` | `findAssetForURI('logo.png')?.inputStream` |
| `sessionFactory` | `currentSession` é a sessão corrente (também por `getBean(org.hibernate.SessionFactory)`) |
| `hibernateDatastore` | Implementa o `HibernateDatastore` do GORM; `mappingContext.getPersistentEntity(nome)` dá o modelo de mapeamento (`PersistentEntity`, `OneToMany`, `ManyToOne`...), somente leitura |
| `transactionManager` | `PlatformTransactionManager` do Spring: `getTransaction(new DefaultTransactionDefinition())`, `commit(status)`, `rollback(status)` sobre a sessão corrente (REQUIRED, SUPPORTS, MANDATORY) |
| `localeResolver` | `SessionLocaleResolver`: `resolveLocale(request)` é o locale da requisição |
| `dataSource` | O pool JDBC, para `groovy.sql.Sql` |
| `springSecurityService` | Serviço de segurança |
| `jwtService` | JWT, quando ativo: `parse(token)` valida e devolve as claims |

## Auditoria (plugin audit-logging)

Domain classes que implementam `grails.plugins.orm.auditable.Auditable` têm inserção, alteração e exclusão gravadas na domain de auditoria, como no plugin audit-logging:

```yaml
grails:
  plugin:
    auditLog:
      auditDomainClassName: sample.AuditTrail   # obrigatório: ativa o plugin (domain como a do audit-quickstart)
      logFullClassName: false                   # className sem o pacote (padrão true)
      mask: [senha]                             # valor gravado como propertyMask
      asyncWrite: true                          # grava num executor depois do commit
```

- **O que é gravado:** um registro por propriedade, com `eventName` (`INSERT`, `UPDATE` ou `DELETE`), `propertyName`, `oldValue`, `newValue`, `actor`, `uri`, `className`, `persistedObjectId` e `persistedObjectVersion`. No UPDATE, só as propriedades alteradas, e o valor antigo vem do banco. `oldValue`/`newValue` são truncados no `maxSize` da domain de auditoria.
- **Quando:** depois do commit da transação, numa sessão nova. Com `asyncWrite`, num executor de virtual threads (`asyncWriteThreads`, `asyncWriteQueueCapacity`). Sem transação, grava na hora.
- **Configuração:** as mesmas chaves e padrões do plugin (`disabled`, `verbose`, `excluded`, `included`, `mask`, `propertyMask`, `logIds`, `defaultActor`, `failOnError`, `truncateLength`, `ignoreEvents`). A domain pode sobrescrever os métodos do trait (`isAuditable(evento)`, `getLogExcluded()`, `beforeSaveLog(audit)`...).
- **Quem:** bean `auditRequestResolver` (`AuditRequestResolver`, com `currentActor` e `currentURI`). O padrão é o `SpringSecurityRequestResolver` com segurança ativa; uma subclasse pode ser registrada no `resources.groovy`.
- **Desligar num trecho:** `AuditLogContext.withoutAuditLog { ... }`, ou `AuditLogContext.withConfig([verbose: false]) { ... }`.
- Os eventos vêm do `save()`/`delete()` do Grox, então uma alteração gravada só pelo flush automático, sem `save()`, não é auditada. `Stampable` compila, mas `createdBy`/`lastUpdatedBy` ainda não são persistidos.

## Redis (plugin redis)

Com o `jedis` declarado pela aplicação (`implementation libs.jedis`), o bean `redisService` tem a API do plugin redis do Grails. A configuração fica em `grails.redis`; sem ela, como no plugin, a conexão é `localhost:6379` (o pool só conecta no primeiro uso):

```yaml
grails:
  redis:
    host: localhost          # port 6379, timeout 2000, password, database, useSSL (verifica o nome do host), poolConfig: [maxTotal: 8...]
    connections:             # opcionais: redisService<Nome> e redisService.withConnection('cache')
      - name: cache
        database: 1
```

```groovy
redisService.withRedis { Jedis redis -> redis.setex(chave, 60, json) }
def total = redisService.memoize("total:${id}", [expire: 300]) { calcular() }    // String em cache
Cliente c = redisService.memoizeObject(Cliente, "cli:${id}", 300) { Cliente.get(id) }   // JSON (Gson)
redisService.ultimoAcesso = 'agora'; redisService.del('chave')                       // comandos do Jedis direto
```

Também: `memoizeHash`, `memoizeHashField`, `memoizeScore`, `withPipeline`, `withTransaction`, `withOptionalRedis` (null se o redis estiver fora do ar) e `deleteKeysWithPattern`. `sentinels` + `masterName` usam o `JedisSentinelPool`. `memoizeDomainList`/`memoizeDomainObject` ❌.

## RabbitMQ (plugin rabbitmq-native, subconjunto)

Com `rabbitmq.connections` configurado e o `amqp-client` declarado pela aplicação, o Grox conecta, declara `rabbitmq.exchanges` e `rabbitmq.queues` (com `exchange` e `binding`) e cria o bean `rabbitMessagePublisher`:

```groovy
rabbitMessagePublisher.send(new RabbitMessageProperties(exchange: 'app.desk', routingKey: "tenant-${id}", body: payloadBytes, expiration: '60000'))
rabbitMessagePublisher.send { exchange = 'app'; routingKey = 'app.alertas'; body = [tipo: 'x'] }   // Map/List → JSON
rabbitMessagePublisher.send('fila', 'texto')
```

- Sem o broker, a aplicação sobe e o envio falha. `rabbitmq.enabled: false` desliga.
- `ssl: true` valida o certificado pelo truststore da JVM (`-Djavax.net.ssl.trustStore=...`) e o nome do host. O plugin original confiava em qualquer certificado.
- Consumers (`grails-app/rabbit-consumers`, `static rabbitConfig`), RPC e confirms ainda não existem. `static rabbitQueue` em services é do plugin rabbitmq antigo: o rabbitmq-native o ignora, e o Grox também (com um aviso no log).

## PDF (plugin rendering)

Com o `flying-saucer-pdf` declarado pela aplicação, o bean `pdfRenderingService` gera PDF de um template GSP em XHTML:

```groovy
pdfRenderingService.render([template: 'recibo', controller: this, model: [conta: c], filename: 'recibo.pdf'], response)
ByteArrayOutputStream pdf = pdfRenderingService.render(template: '/recibo/recibo', model: [conta: c])
pdfRenderingService.render([template: '/recibo/recibo', model: m], outputStream)
renderPdf(template: 'recibo', model: [conta: c])                 // no controller: template relativo a ele
```

O template precisa ser XHTML bem formado. `base` (ou `grails.serverURL`) resolve URLs relativas de imagens e CSS. Imagens em bytes vão embutidas com `<rendering:inlinePng bytes="${logo}" height="66px"/>` (também `inlineJpeg` e `inlineGif`); os demais atributos vão para o `<img>`. Renderizar GIF, PNG e JPEG como saída ❌.

## Exportação Excel (plugin excel-export)

A aplicação declara `org.apache.poi:poi-ooxml`:

```groovy
def xls = new WebXlsxExporter()
xls.setWorksheetName('Contas')
xls.setResponseHeaders(response, 'contas.xlsx')
xls.putCellValue(0, 0, 'Cliente').bold().centre()
xls.putCellValue(1, 0, conta.cliente.nome)
xls.save(response.outputStream)
```

Listas inteiras: `xls.fillHeader(['Nome', 'Cidade'])` e `xls.add(contas, ['cliente.nome', 'cidade', { it.total * 2 }])`, com propriedades, caminhos com ponto, closures ou chaves de mapa; `fillRow(valores, linha)` preenche uma linha.

## Console web

`/console` executa scripts Groovy dentro da aplicação, como o plugin console do Grails. É útil para inspecionar dados, testar services e corrigir registros.

- **Editor:** `Ctrl+Enter` executa o script ou só a seleção. Histórico e rascunho ficam no navegador.
- **Rollback no final:** roda o script numa transação desfeita ao terminar, para experimentar gravações.
- **Bindings:** `ctx` com todos os beans, inclusive os de plugins e do `resources.groovy` (`ctx.usuarioService`, `ctx.getBean('redisService')`, `ctx.beanNames`), `grailsApplication`, `config`, `runtime`, `request`, `session`, `springSecurityService`, `log`, `out`, além de cada service pelo nome.
- **Imports:** os pacotes das domain classes, dos services e dos controllers já vêm importados, então `Usuario.where { ... }.list()` funciona direto, assim como `Tenants`, `JSON` e `DetachedCriteria`.
- **Contexto:** o script roda com a **sessão de dados e a autenticação de quem executa**. O tenant é o do usuário logado; use `Tenants.withId(...)` para outro.
- **Retorno:** o resultado (`inspect()`), a saída de `println` e os erros (compilação com a linha, execução com as linhas do script).
- **API:** `POST /console/execute` com `{"code": "...", "rollback": false}` devolve `{result, resultType, output, error, durationMs, rolledBack}`. Com JWT dispensa o CSRF; com sessão, envie o cabeçalho `X-CSRF-TOKEN`.
- **Proteção contra outros sites:** o `/execute` exige `Content-Type: application/json`, que um formulário HTML não consegue enviar, e recusa `Origin` diferente do `Host` ou `Sec-Fetch-Site` de outro site. Sem segurança ativa, só aceita `Host` localhost (contra DNS rebinding) e recusa requisições com `X-Forwarded-For`/`Forwarded` (um proxy local faria tudo parecer vir de localhost).

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.console.enabled` | `true` só em development | Também aceita `grails.plugin.console.enabled`, a chave do plugin. Fora de development, a subida registra um aviso no log |
| `grox.console.path` | `/console` | |
| `grox.console.access` | `[ROLE_ADMIN]` | Com segurança ativa (sessão com CSRF, ou JWT/Basic) |
| `grox.console.localOnly` | `true` | **Sem** segurança ativa, só aceita requisições de localhost |

Cada execução é registrada no log `org.grox.plugins.console.WebConsole`, com o usuário e a duração. **Atenção:** o console executa qualquer código com os privilégios da aplicação. Não o habilite em produção sem necessidade.

## Injeção de dependências

É por **nome de propriedade**, como no Grails.

- **Beans:** cada service vira o bean `nomeEmCamelCase` (`UsuarioService` → `usuarioService`). Também existem `grailsApplication`, `springSecurityService` (com segurança ativa), `groxRuntime` e `groxApplication` (a aplicação do core, sem a fachada do Grails).
- **Quem recebe:** qualquer **propriedade Groovy** (com setter) de nome igual a um bean e tipo compatível (`def` ou o tipo do service).
  - Recebem: controllers (cada instância), services, TagLibs, interceptors, BootStrap, command objects e o `tenantResolverClass`.
  - Domain classes **não** recebem, como no Grails 3.3+.
- **Proxies:** um service com `static actor = true`, `@Transactional` ou anotações de tenant é injetado como **proxy-subclasse**. A injeção tipada funciona, e chamadas internas (`this.x()`) não passam pelo proxy.
- **Circularidade:** dependências circulares funcionam, porque todos os services são criados antes da injeção.
- **Limites:** não há injeção por construtor, qualifiers nem escopos (tudo é singleton, exceto controllers no modo `request`). Para inicialização, use `@PostConstruct` (ver [Beans](#beans-resourcesgroovy)) ou o `BootStrap`.

## Services

```groovy
class ContadorService {
    static actor = true        // opcional: chamadas serializadas por um ator
    private int total           // sem sincronização: só o ator acessa
    int incrementar() { ++total }
}
```

- Os services são singletons, instanciados na subida e injetados por nome (`contadorService`). Veja [Injeção de dependências](#injeção-de-dependências).
- Com `actor = true`, a injeção entrega um proxy-subclasse. A chamada bloqueia (em virtual thread) até o ator responder, e as exceções chegam para quem chamou. A sessão de dados de quem chama é usada pelo ator.
- Com `@grails.gorm.transactions.Transactional` (ou `@ReadOnly`) na classe ou em métodos, cada chamada externa roda em transação. Chamadas internas (`this.x()`) não passam pelo proxy.
- O `@org.springframework.transaction.annotation.Transactional` também vale. Nos dois, `propagation = Propagation.REQUIRES_NEW` roda em sessão e transação novas (`withNewTransaction`). SUPPORTS, NOT_SUPPORTED e NEVER não abrem transação, e MANDATORY exige uma em andamento. `isolation` e `timeout` são aceitos, sem efeito.
- `TransactionAspectSupport.currentTransactionStatus()` devolve o status da transação corrente (`NoTransactionException` sem transação).
- **Evite ciclos** entre services-ator: A → B → A trava.

## UrlMappings

`grails-app/controllers/<pkg>/UrlMappings.groovy`:

```groovy
class UrlMappings {
    static mappings = {
        "/$controller/$action?/$id?(.$format)?" {
            constraints { id(matches: /\d+/) }
        }
        "/"(controller: 'home')                        // ou (view: '/index')
        "/ola/$nome?"(controller: 'home', action: 'ola')
        "/antigo"(redirect: '/novo')
        "/ajuda"(controller: 'site', action: 'pagina') { pagina = 'ajuda' }   // params extras
        "/livros/$id"(controller: 'livro') { action = [GET: 'show', DELETE: 'delete'] }
        post "/login"(controller: 'auth', action: 'entrar')
        group "/api", {
            "/livros"(resources: 'livro', format: 'json')   // rotas REST + params.format fixo
        }
        "404"(controller: 'erro', action: 'naoEncontrado')
        "500"(controller: 'erro', action: 'interno')        // request.exception disponível
    }
}
```

- `resources: 'livro'` gera: `GET /livros` (index), `GET /livros/create`, `POST /livros` (save), `GET /livros/$id` (show), `GET /livros/$id/edit`, `PUT /livros/$id` (update), `PATCH /livros/$id` (patch), `DELETE /livros/$id` (delete). Todas aceitam `.json`.
- Mapeamentos mais específicos (mais texto literal) têm prioridade.
- Sem nenhuma classe `UrlMappings`, vale só o mapeamento padrão `/$controller/$action?/$id?(.$format)?`.
- Formulários podem simular PUT/PATCH/DELETE com o campo `_method` (o `<g:form method="PUT">` já gera) ou com o cabeçalho `X-HTTP-Method-Override`.
- `createLink` e `redirect` geram URLs a partir destes mapeamentos. Um mapeamento que fixa parâmetros (como `format: 'json'`) só é usado se o link pedir o mesmo valor.

## Views em Pug e Markdown (`grox-pug`, `grox-markdown`) *(extensão Grox)*

Com o módulo no classpath, `render view: 'x'` (e `<g:render template>`, a página de login, o `groovyPageRenderer`) procura `x.gsp`, depois `x.pug` e `x.md`, nos lugares das GSPs; o HTML passa pelos layouts SiteMesh. O ponto de extensão é o `ViewEngine` do `grox-gsp` (`GspViews.register`).

- **Pug** (pug4j 3, sem o GraalJS nem o flexmark): as expressões são **Groovy** (`GroovyExpressionHandler` no lugar do JEXL; variável ausente é `null`, verdade do Groovy, closures, `?.`), com o modelo, os implícitos da GSP (`params`, `session`, `flash`...) e os namespaces de tags (`g.createLink(...)`, `message(code:)`). **TagLibs como elementos**: `g:link(action='show' id=l.id) Ver`, `g:form(...)` com o bloco como corpo; o argumento de `body(x)` fica como `it` (ou as chaves de um Map). A saída da tag não é escapada. `extends`/`include` com caminhos da raiz das views, mixins, filtro `:markdown` (commonmark, HTML escapado).
- **Markdown**: o `.md` é compilado como GSP (`${}` escapado, tags) e a saída vai ao commonmark (tabelas e tachado do GFM, autolink, âncoras, `sanitizeUrls`). O front matter dá `title`, `layout` e `<meta>`; `_nome.md` sai sem documento. `<md:render text="${x}"/>` converte texto do usuário escapando o HTML cru (`trusted="true"` para texto confiável); `Markdown.toHtml(texto)` em código.
- Chaves: `grox.pug.reload`, `grox.pug.prettyPrint`, `grox.markdown.reload` (padrão: o de `grox.gsp.reload`).

## DSL de HTML (`render { }`)

Alternativa à GSP, nos controllers e nas rotas do `grox-dsl`: o closure do `render` monta o HTML com o `MarkupBuilder` do Groovy, como no Grails (`render { div(...) }`). O builder foi ajustado para HTML5:
- os elementos vazios do HTML (`br`, `input`, `meta`, `img`...) saem como `<br />`, e os demais, mesmo vazios, com a tag de fechamento (`<script src="x"></script>`, `<div></div>`);
- texto e atributos são escapados; um valor seguro (a saída das tags `g:`, `raw(...)`) entra sem escape duplo;
- atributos `null` são omitidos;
- começando por `html { }`, a saída ganha o `<!DOCTYPE html>`.

```groovy
render(layout: 'main') {                       // ou meta(name: 'layout', content: 'main') no head, ou o layout padrão
    h1 'Livros'
    ul { livros.each { l -> li { a(href: g.createLink(action: 'show', id: l.id), l.titulo) } } }
    p g.message(code: 'livros.total', args: [livros.size()])
}
```

O resultado é decorado pelo layout SiteMesh como uma view GSP. As tags ficam nos namespaces (`g.*`, `sec.*`, os das TagLibs da app); um nome solto vira elemento (`message(...)` gera `<message>`), então use `g.message(...)`. Variáveis do método, `params`, `flash` e os services continuam acessíveis. Opções: `layout:`, `model:` (para o layout), `status:` e `contentType:`. Com `contentType` JSON (`application/json`, `text/json`), o closure monta o JSON, como antes; com XML, o `MarkupBuilder` puro.

## Views GSP

Arquivos em `grails-app/views`:
- `<controller>/<action>.gsp` é a view padrão de uma action;
- `_nome.gsp` é um template;
- `layouts/*.gsp` são os layouts.

### Sintaxe

| Construção | Efeito |
|---|---|
| `${expr}`, `<%= expr %>` | Imprime o valor **codificado em HTML**. `null` imprime vazio |
| `${raw(html)}`, `${x.encodeAsRaw()}` | Imprime sem codificar |
| `<% codigo %>` | Scriptlet Groovy |
| `<%@ page import="a.B; c.D" contentType="text/plain" %>` | Diretiva |
| `%{-- comentário --}%`, `<%-- comentário --%>` | Removidos da saída |
| `<g:tag attr="literal" outro="${objeto}" misto="a ${b}">corpo</g:tag>` | Tag. Um atributo que é só `${...}` recebe o **objeto**; atributos mistos viram String |
| `${g.link(action: 'x') { 'corpo' }}`, `${createLink(action: 'x')}`, `${fmt.moeda(valor: v)}` | Tags chamadas como métodos |

Variáveis implícitas: `params`, `request`, `response`, `session`, `flash`, `controllerName`, `actionName`, `grailsApplication`, `webRequest`, `pageScope`, `locale`, `out`. Uma variável desconhecida vale `null`.

### Tags `g:` nativas

| Grupo | Tags |
|---|---|
| Controle | `if` / `elseif` / `else` (test), `unless`, `while`, `each` (in, var, status), `set` (var, value ou corpo; grava no pageScope) |
| Links | `link`, `createLink` (controller, action, id, params, uri, url, absolute, fragment; os demais atributos vão para o `<a>`), `resource` (dir, file → `/static/...`) |
| Formulários | `form` (action/controller/url, method; PUT/PATCH/DELETE usam `_method`), `uploadForm` (multipart), `textField`, `passwordField`, `hiddenField`, `emailField`, `numberField`, `dateField`, `field` (type), `textArea`, `checkBox` (gera `_nome` para o desmarcado virar `false` no binding), `radio`, `select` (from, optionKey, optionValue, value, noSelection), `submitButton` |
| Templates | `render` (template, model, bean, collection, var) |
| Listas | `paginate` (total, max, offset, maxsteps, params, prev/next, omitPrev/Next/First/Last; classes `prevLink`, `step`, `currentStep`, `nextLink`), `sortableColumn` (property, title/titleKey, defaultOrder, params; `<th class="sortable sorted asc">`) |
| Layouts | `layoutTitle` (default), `layoutHead`, `layoutBody`, `pageProperty` (name: `page.x`, `meta.x`, `body.onload`), `ifPageProperty`, `applyLayout` (name, template) |
| i18n e formatação | `message` (code, args, default), `formatDate` (date, format; Date ou java.time), `formatNumber` (number, type: currency/percent/number, format, currencyCode, min/maxFractionDigits), `join` (in, delimiter) |

Codecs disponíveis em qualquer objeto: `encodeAsHTML()`, `encodeAsURL()`, `encodeAsJSON()`, `encodeAsJavaScript()`, `encodeAsRaw()`, e os de digest e codificação: `encodeAsMD5()`, `encodeAsSHA1()`, `encodeAsSHA256()` (hex; `...Bytes()` devolve `byte[]`), `encodeAsBase64()`, `encodeAsHex()`. Texto é lido em UTF-8.

Caminho dos templates:
- `<g:render template="form"/>` → `views/<controller>/_form.gsp`;
- `template="/shared/menu"` → `views/shared/_menu.gsp`;
- fora de um controller, é relativo ao diretório da view atual.

### Layouts (SiteMesh 3)

Ordem de escolha do layout:
1. `<meta name="layout" content="main"/>` na página;
2. `static layout` do controller;
3. `layouts/<controller>/<action>.gsp`;
4. `layouts/<controller>.gsp`;
5. a configuração `grails.sitemesh.default.layout`.

Só respostas `text/html` de **views** são decoradas. `render text:` e templates não são.

```html
<!-- views/layouts/main.gsp -->
<html><head><title><g:layoutTitle default="App"/></title><g:layoutHead/></head>
<body><nav><g:pageProperty name="page.menu"/></nav><g:layoutBody/></body></html>

<!-- na página -->
<content tag="menu"><a href="/">início</a></content>
```

No layout também funciona o `<sitemesh:write property="body"/>` nativo do SiteMesh. Os atributos do `<body>` da página são mesclados com os do layout.

### Reload

Em desenvolvimento (`grox.gsp.reload`, ligado por padrão em `development`), as views são lidas de `grails-app/views` e recompiladas quando o arquivo muda. Isso exige rodar a partir do diretório do projeto, como faz o `gradle run`. Nos demais casos elas vêm do classpath (`views/`) e são compiladas uma vez.

Erros de GSP indicam **arquivo e linha da GSP**, por exemplo `/usuario/show.gsp:13: ...`.

## TagLibs

```groovy
// grails-app/taglib/sample/FormatacaoTagLib.groovy
class FormatacaoTagLib {
    static namespace = 'fmt'               // padrão: g
    static returnObjectForTags = ['total'] // chamada como método devolve o objeto, não o HTML

    def badge = { attrs, body -> out << '<span class="badge">' << body() << '</span>' }
    def idade = { attrs -> out << g.message(code: 'usuario.anos', args: [attrs.valor]) }
    def total = { attrs -> attrs.itens.sum() }
}
```

Uso: `<fmt:badge>#1</fmt:badge>` ou `${fmt.badge { '#1' }}`.

Como no Grails 3+, uma tag também pode ser um **método** público, e os dois estilos podem ser misturados na mesma TagLib:

```groovy
def badge(Map attrs, Closure body) { out << '<span class="badge">' << body() << '</span>' }
def total(Map attrs) { attrs.itens.sum() }
```

- A assinatura é `(Map attrs)` ou `(Map attrs, body)`, com o corpo `Closure` ou sem tipo. Com as duas sobrecargas, vale a que recebe o corpo.
- Não viram tag os métodos estáticos e privados, os de outra assinatura (auxiliares), os getters e setters gerados e os do trait da TagLib.
- Uma subclasse pode sobrescrever o método de tag da superclasse.

Dentro da TagLib estão disponíveis `out`, `params`, `request`, `session`, `flash`, `pageScope`, `controllerName`, `g.<tag>(...)` e `<tag>(...)` direto para tags `g:`.

Qualquer namespace também é uma propriedade, nas TagLibs e nos controllers, como no Grails: `fmt.idade(valor: 30)` devolve o HTML da tag (ou o objeto, com `returnObjectForTags`).

- `body()` devolve o corpo renderizado.
- `body(item)` expõe `it`.
- `body([nome: v])` expõe variáveis.
- A saída das tags **não** é codificada: codifique os valores com `Html.escape(...)` ou `encodeAsHTML()`.

## i18n

As mensagens ficam em `grails-app/i18n/messages*.properties`, em UTF-8, com `{0}` para argumentos (sintaxe do `MessageFormat`).

O locale vem, nesta ordem:
1. de `?lang=en` (guardado na sessão);
2. da sessão;
3. do `Accept-Language`;
4. de `grox.i18n.defaultLocale`.

A busca de uma mensagem tenta `messages_<idioma>_<PAIS>`, depois `messages_<idioma>`, depois o locale padrão e por fim `messages`. Se nada for encontrado, vale `default` ou o próprio código.

Arquivos com o mesmo nome em vários jars (a app e bibliotecas `org.grox.lib`) são mesclados; o primeiro do classpath, a aplicação, prevalece.

## Recarga de classes em desenvolvimento

Com `gradle run` em `development`, salvar um controller, service, taglib, domain class ou classe de `src/main/groovy` recarrega a aplicação **sem reiniciar a JVM**. Servidor, porta e sessões continuam os mesmos, e o usuário segue logado. Views, assets e estáticos continuam com o reload próprio, que funciona igual antes e depois de uma recarga de classes.

Como funciona:
- O `run` inicia um Gradle contínuo (`--continuous :app:groxReloadStamp`) que compila a cada mudança, de forma incremental. A tarefa `groxReloadStamp` grava um carimbo ao fim de cada compilação **bem-sucedida**, e é ele que dispara a recarga. Uma compilação com erro não derruba nada: a versão anterior continua no ar.
- A app roda em classloaders próprios, com o bytecode lido na hora da recarga (o compilador pode apagar o diretório sem afetar a versão no ar). Cada recarga sobe uma nova *geração*.
- As classes ficam em duas camadas:

| Camada | O que entra | Na mudança |
|---|---|---|
| Recarregável | tudo de `grails-app/{controllers,services,taglib,init,jobs}` (marcado com `@Reloadable` pela AST) | **Recarga leve** (100 ms–1 s): troca controllers, services, taglibs, interceptors, jobs, UrlMappings e `resources.groovy`. Hibernate, segurança e dados continuam; o BootStrap **não** roda de novo |
| Estável | domain classes, `src/main/groovy`, `grails-app/utils` | **Recarga completa** (1–3 s, contra 10–15 s de um restart): recria o Hibernate, roda o `destroy` e o `init` do BootStrap. As requisições esperam a nova geração, em vez de receberem erro |

- `messages*.properties` e `resources.groovy` pedem recarga leve; `application.yml` pede a completa.
- Um artefato recarregável que é referenciado por uma classe estável (por exemplo, um service usado como tipo em `src/main/groovy`) fica fixado na camada estável e só recarrega na completa. O log avisa quando isso acontece.
- Se a recarga completa falhar (erro no BootStrap, mapeamento inválido), as requisições recebem 503 com o erro até a próxima mudança corrigir.
- Sem o wrapper do Gradle, ou com `-Dgrox.reload.compile=false`, a compilação fica por conta da IDE. Nesse caso a recarga dispara quando os diretórios de classes param de mudar.

Limitações:
- Instâncias guardadas na sessão em memória continuam sendo da geração antiga. Depois de uma recarga completa, uma domain class na sessão é de outra classe (use ids na sessão). Com sessão no Redis, o objeto é desserializado com a geração nova.
- Código de bibliotecas que carrega classes da app pelo *context classloader* de threads do Netty vê a cópia do classpath do sistema.
- Em projetos grandes, o tempo vai quase todo na compilação de domain classes. O Gradle recompila tudo que depende da classe alterada (no app A, ~24 s contra 2–3 s de um controller). Não divida a app em módulos para contornar isso: a recarga só acompanha o módulo da aplicação. O módulo de domains está no [roadmap](roadmap.md#depois-sem-prioridade-definida).
- Tokens JWT continuam válidos na recarga leve. Na completa, só continuam se o segredo estiver configurado (sem ele, o segredo é aleatório por geração).

A troca de métodos na mesma JVM, com HotSwap/DCEVM e sem nova geração, está registrada como melhoria futura no [roadmap](roadmap.md#depois-sem-prioridade-definida).

## Assets (convenções do asset-pipeline do Grails)

```
grails-app/assets/
  javascripts/application.js     //= require util   //= require_tree modulos   //= require_self
  stylesheets/application.css    /* *= require base */   url('../images/logo.png')
  images/logo.png
```

```html
<asset:stylesheet href="application.css"/>
<asset:javascript src="application.js" defer="${true}"/>
<asset:image src="logo.png" alt="Logo"/>
<asset:link rel="icon" href="favicon.ico" type="image/x-icon"/>
<div style="background: url(${assetPath(src: 'logo.png')})"></div>
<g:if test="${assetPathExists(src: 'tema.css')}">...</g:if>
```

- **Raízes:** cada subpasta de `grails-app/assets` é uma raiz (`javascripts`, `stylesheets`, `images`, e outras que você criar). O caminho lógico é relativo à raiz: `application.js`, `lib/util.js`, `logo.png`.
- **Diretivas:**
  - `require` (a extensão é opcional; busca primeiro relativo ao arquivo, depois nas raízes);
  - `require_tree` (todos os arquivos da pasta, em ordem);
  - `require_self` (posição do próprio conteúdo; o padrão é depois dos requires).

  Cada arquivo entra uma vez só no bundle.
- **CSS:** `url(...)` relativos são reescritos para a URL do asset, inclusive `../images/x.png` e o nome solto `x.png`. URLs absolutas, `data:` e `http(s):` ficam como estão.
- **Desenvolvimento** (com `grails-app/assets` e reload): `/assets/**` é processado na hora, com `Cache-Control: no-cache`, e reprocessado quando qualquer arquivo da cadeia de `require` muda.
- **Build:** a tarefa `assetCompile` do plugin `org.grox.app` gera `nome.ext` e `nome-<md5>.ext` em `META-INF/assets`, mais o `manifest.properties`. Um `require` inexistente **falha o build**.
- **Produção:** as tags apontam para os nomes com digest, servidos com `Cache-Control: public, max-age=31536000, immutable` e `ETag` (com resposta 304). Nomes lógicos também respondem, com cache curto (5 min).
- **Segurança:** com `rejectIfNoRule`, libere `/assets/**` no `interceptUrlMap`.
- **Configuração:** `grails.assets.mapping` (padrão `assets`) muda o prefixo da URL; `grox.assets.dir` muda o diretório de desenvolvimento.
- **Diferença para o plugin do Grails:** não há transpilação nem pré-processadores (ES6/Babel, SASS, LESS, CoffeeScript), nem minificação. É uma escolha: o `asset-pipeline-core` estável (5.0.9) é incompatível com o Groovy 6, e a 5.2.0 (ainda RC) exige o GraalVM.

## Estáticos

`src/main/resources/public/**` é servido em `/static/**`. Em desenvolvimento os arquivos são lidos direto do diretório de fontes (sem cache). Nos demais casos vêm do classpath, com `Last-Modified`, `304` e `Cache-Control`.

## BootStrap

```groovy
class BootStrap {
    UsuarioService usuarioService
    def init = { servletContext ->          // servletContext é sempre null
        environments { development { usuarioService.salvar(...) } }
    }
    def destroy = { }
}
```

## Subida rápida

- **Desenvolvimento:** o `gradle run` usa JIT só C1 (`-XX:TieredStopAtLevel=1`), o que acelera a subida de 25% a 40%. Desligue com `grox { fastDevStartup = false }` no `build.gradle`, por exemplo para medir desempenho.
- **Produção (JDK 25+):** `./gradlew installDist aotCache` faz uma execução de treino da aplicação instalada. A app sobe, encerra ao ficar pronta e deixa o AOT cache em `build/install/<app>/lib/grox.aot`.
  - O script `bin/<app>` usa o cache quando o arquivo existe.
  - O treino usa a configuração do ambiente (inclusive o banco), então rode com as mesmas variáveis de produção.
  - Qualquer `installDist` depois disso apaga o cache: rode `aotCache` por último.
  - O cache vale para aquele JDK e aquele classpath. Com outro, a JVM avisa e sobe sem ele.
- O log mostra o tempo de cada fase da subida (`Subida por fase`).

## Aplicações sem `grails-app/` (`grox-dsl`)

Uma aplicação pode usar só os módulos `grox-core`, `grox-gorm`, `grox-gsp`, `grox-security` e `grox-dsl`, sem o módulo Grails nem o layout `grails-app/` (exemplo completo em `sample-plain/`). As rotas são declaradas por closure ou por anotação, e as respostas seguem o estilo dos controllers do Grails (`render view:/model:`, `render json:`, `render text:`, `respond`, `redirect`). O pipeline HTTP (segurança, sessão, estáticos, `/actuator`, páginas de erro) é o mesmo das apps Grails.

```groovy
// build.gradle
dependencies {
    implementation project(':framework:grox-core')    // e grox-gorm, grox-gsp, grox-security, grox-dsl
}
tasks.withType(GroovyCompile).configureEach { groovyOptions.parameters = true }   // parâmetros por nome nos @Handler
```

O pacote da classe passada a `GroxApp.run(Aplicacao)` é varrido: domain classes, services (`*Service`), classes `Routes` e `@Handler`. As views ficam em `src/main/resources/views/` (classpath `views/`), e o `application.yml` em `src/main/resources/`.

**Rotas por closure** (classe que implementa `org.grox.dsl.Routes`; services injetados pelo nome):

```groovy
class Rotas implements Routes {
    LivroService livroService

    Closure routes = {
        get('/livros', name: 'index') { render view: 'livros/index', model: [livros: livroService.todos()] }
        get('/livros/{id}', name: 'show', view: 'livros/show') { [livro: livroService.buscar(params.long('id'))] }
        post('/livros') { redirect "/livros/${livroService.salvar(params).id}" }
        group('/relatorios', execution: 'actor', poolSize: 2) {
            after { response.setHeader('X-Relatorio', actionName) }   // só as rotas do grupo
            get('/autores') { render json: livroService.porAutor() }
        }
        ns('/notas') {                                   // ns é o mesmo que group
            get { render text: 'lista' }                 // sem caminho: o do grupo (/notas)
            get('{id}') { render text: params.id }       // relativo ao grupo: /notas/{id}
        }
    }
}
```

**Rotas por anotação** (`@Handler` no caminho base; `@Get`, `@Post`, `@Put`, `@Patch`, `@Delete` nos métodos). A classe ganha `render`, `respond`, `redirect`, `params`, `request`, `session`, `flash` e `locale`. Os parâmetros do método vêm pelo nome: valores simples convertidos dos parâmetros (inclusive os do caminho), `Map params`, `RouteContext` ou um objeto preenchido por binding.

```groovy
@Handler('/api/livros')
class LivrosApi {
    LivroService livroService

    @Get('/{id}') def show(Long id) { respond livroService.buscar(id) }   // null: 404
    @Post def save() {
        Livro livro = livroService.salvar(request.json as Map)
        livro.hasErrors() ? respond(livro.errors, status: 422) : respond(livro, status: 201)
    }
    @OnError def falhou(Throwable e) { render status: 400, json: [erro: e.message] }   // exceção numa rota da classe
}
```

**Interceptores** *(extensão Grox)*: `before`, `after` e `error` na DSL de rotas valem para as rotas do escopo onde são declarados: a classe `Routes`, o `group`/`ns` ou, na raiz do `routes { }` da DSL de projeto, a aplicação inteira (inclusive as classes `@Handler`). Com um padrão Ant (`before('/api/**') { }`), só as rotas cujo caminho casa. Nas classes `@Handler`, os métodos `@Before`, `@After` e `@OnError` (com o padrão opcional no `value`) valem para as rotas da classe.

| Interceptor | Comportamento |
|---|---|
| `before { }` | Antes do handler, com o mesmo delegate da rota (`params`, `session`, `render`, `redirect`). Devolver `false` ou responder interrompe a rota |
| `after { valor -> }` | Depois do handler, com o valor que ele devolveu (o modelo, antes da `view:` da rota) |
| `error { Throwable e -> }` | Exceção no handler ou noutro interceptor. A resposta parcial é descartada; se o interceptor responder, vale a resposta dele, senão a exceção segue para a página de erro |
| Ordem | `before` do mais externo (app, classe, grupo) para o mais interno; `after` e `error` ao contrário. Com o padrão de execução `actor`, rodam dentro do ator da rota |

| Recurso | Comportamento |
|---|---|
| Caminho | `{nome}` vira parâmetro; a rota mais específica casa primeiro; método errado dá 405 |
| Opções da rota ou do grupo | `name:` (o "action" nos links e nas métricas), `view:` (um Map devolvido sem `render` vira o modelo dessa view), `execution:`, `poolSize:` |
| `respond x` | JSON; com `view:` e o cliente pedindo HTML, a view com o modelo `[livro: x]` ou `[livroList: [...]]`; `null` é 404 |
| Execução | `grox.routes.execution`: `virtual` (padrão, uma virtual thread por requisição) ou `actor` (pool de `grox.routes.poolSize` atores; num `@Handler`, cada ator tem a sua instância). Cada rota ou grupo troca com `execution:`/`poolSize:` |
| Requisição | Cada handler roda com a autenticação e uma sessão de dados abertas (open session in view) |
| Domain classes | `@grails.gorm.annotation.Entity`, como no GORM: API do GORM, `id`, `version`, associações e construtor com Map |
| Views | GSP com layouts SiteMesh e as tags `g:` nativas (inclusive as de erros); `controllerName` é o grupo (`rotas`, `livrosApi`) e `actionName`, o nome da rota |
| Login | A `views/login/auth.gsp` da app, se existir; senão a página padrão. O `<g:form>` inclui o token CSRF |
| Links | `createLink(controller: 'rotas', action: 'show', id: 1)` usa as rotas; `uri:` e `url:` também |
| Junto com o Grails | Numa app com controllers e rotas, os controllers são consultados primeiro |

### DSL de projeto

A app pode ser declarada num lugar só: na classe passada a `GroxApp.run`, com um closure estático `app`, ou num script com `@BaseScript GroxScript`. Os módulos continuam escolhidos pelas dependências do build.

```groovy
class Livraria {
    static app = {
        config {                                     // valores padrão: yml/groovy, variáveis de ambiente e -D passam por cima
            grox.routes.execution = 'virtual'
            grox { routes { poolSize = 4 } }
        }
        security {                                   // ativa a segurança
            user 'admin', password: '{noop}admin', roles: ['ROLE_ADMIN']
            rule '/api/**', 'ROLE_ADMIN'             // regras de URL, depois das dos arquivos
            rule '/relatorios/**', httpMethod: 'GET', 'isAuthenticated()'
            useBasicAuth = true                      // demais chaves de grails.plugin.springsecurity
        }
        routes {
            get('/app/info', access: 'permitAll') { render json: [livros: livroService.todos().size()] }
            group('/admin', access: 'ROLE_ADMIN') { get('/painel') { render { h1 'Painel' } } }
            handlers LivrosApi                       // classes @Handler (também achadas pela varredura)
            error('/app/**') { Throwable e -> render status: 503, json: [erro: e.message] }   // vale para a app inteira
        }
        beans {
            relogio = java.time.Clock.systemUTC()    // instância pronta, ou a sintaxe do resources.groovy
        }
        bootstrap {
            init { if (!livroService.todos()) livroService.salvar(titulo: 'Dom Casmurro') }
            destroy { log.info 'fim' }
        }
    }

    static void main(String[] args) { GroxApp.run(Livraria, args) }
}
```

```groovy
// src/main/groovy/livraria/app.groovy: o mesmo, como script (executá-lo sobe a app)
package livraria
@groovy.transform.BaseScript org.grox.dsl.GroxScript base

grox {
    routes { get('/') { render { h1 'Olá' } } }
}
```

| Bloco | Comportamento |
|---|---|
| `config { }` | Mesma árvore do `application.yml`, com atribuições (`server.port = 8080`) e blocos aninhados. Só preenche as chaves que os arquivos não definem |
| `security { }` | `user` e `rule` acrescentam usuários e regras de URL (depois das do `interceptUrlMap` e das `staticRules`); as atribuições vão para `grails.plugin.springsecurity` como padrão |
| `routes { }` | As mesmas rotas de uma classe `Routes`, no grupo com o nome da classe da app; `handlers X, Y` registra classes `@Handler` |
| `access:` | Na rota, no `group` ou em `@Handler(access = ...)`/`@Get(access = ...)`: vale como um `@Secured`, antes das regras de URL |
| `beans { }` | A sintaxe do `resources.groovy` (`nome(Classe) { prop = ref('x') }`) e também `nome = instancia` |
| `bootstrap { }` | `init { }` na subida (depois dos services, antes do servidor, com uma sessão de dados) e `destroy { }` no desligamento; os closures veem beans e services pelo nome, `config`, `log` e `environments { }`. Roda antes das classes `BootStrap` |

Nas rotas por closure, services e beans são acessíveis pelo nome (`livroService`, `relogio`), e `config` é a configuração da aplicação. Nas classes `@Handler`, declare a propriedade (`LivroService livroService`) para receber a injeção; elas também têm `response`, `config` e `routeContext`. O `config` de dentro do bloco `app` não é visível nas rotas.

Ainda não há: tags `sec:` fora do plugin spring-security (use `request.userPrincipal`), classes `*Interceptor` (use os interceptores da DSL), `withFormat` e recarga de classes nas apps sem `grails-app/`.

Outros pontos das apps sem `grails-app/`:
- **Parâmetros de `@Handler`:** um objeto recebe só o binding de `params`: sem `validate()`, sem carga de domain pelo `id` e sem o JSON do corpo (leia `request.JSON`).
- **`@Handler` não tem `view:`:** um `Map` devolvido não vira view; use `render view:` ou `respond x, view:`.
- **Reload de views:** o padrão de `grox.gsp.viewsDir` é `grails-app/views`; defina `grox.gsp.viewsDir: src/main/resources/views` para recarregar em development.
- **Dados iniciais:** o `bootstrap { }` da DSL de projeto ou uma classe `BootStrap` no pacote da aplicação (`def init = { servletContext -> }`, `def destroy = { }`, services injetados, `environments { }`), como no Grails. Com o módulo Grails presente, é ele quem roda as classes. Na recarga leve nada roda de novo.
- **Assets:** o `grox-assets` serve `/assets/**`, mas as tags `asset:` estão no `grox-grails-plugins`, e as tarefas `compileGsp`/`assetCompile` só existem com o plugin `org.grox.app`.

## Configuração (`application.yml`)

Aceita vários documentos separados por `---`, bloco `environments:` e chaves com ponto. Como no Grails, também lê `application.groovy` e `runtime.groovy` (em `grails-app/conf/`, ou no classpath nas apps do `grox-dsl`), na sintaxe do `ConfigSlurper`, com `environments { }`; o `runtime.groovy` pode referenciar classes da aplicação. Os arquivos são mesclados num só `config`, e a precedência é:
1. `-Dchave=valor`;
2. variável de ambiente (`server.port` → `SERVER_PORT`);
3. `runtime.groovy`;
4. `application.groovy`;
5. o YAML.

A ordem entre o Groovy e o YAML segue a do Grails, que registra o carregador do `application.groovy` antes do do YAML (no Spring Boot, no mesmo local, vence o carregador que vem antes). Mudar qualquer um dos arquivos em desenvolvimento faz a recarga completa.

```groovy
// grails-app/conf/application.groovy
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
    [pattern: '/', access: ['permitAll']],
    [pattern: '/api/**', access: ['ROLE_ADMIN']]
]
environments {
    development { grox.console.enabled = true }
}
```

**Importação (`spring.config.import`)**, como no Spring Boot e no Grails 8: configuração compartilhada por várias apps (por exemplo, num projeto `org.grox.lib` com o `grails-app/conf` no classpath). O valor é uma string separada por vírgula ou uma lista, com os prefixos `classpath:` (o padrão), `file:` e `optional:` (pode faltar; sem ele, um arquivo ausente impede a subida). O arquivo importado vence o que o importou (e, no YAML, o `environments` do importado é aplicado); `.groovy` é lido pelo `ConfigSlurper`, e um importado pode importar outros. Importações do YAML ficam abaixo do `application.groovy` na precedência; as do `application.groovy`, acima dele. Em desenvolvimento, mudar qualquer `.yml`/`.groovy` na raiz dos recursos faz a recarga completa.

```yaml
spring:
  config:
    import: classpath:app-shared.yml,classpath:app-rabbitmq.groovy
```

A sobreposição por variável de ambiente vale só para valores. Um bloco do YAML (ex.: `mail:`) nunca é escondido por uma variável homônima (ex.: `MAIL` do shell).

Valores podem usar placeholders, resolvidos na carga: `${NOME}` ou `${NOME:padrão}`. `NOME` é procurado como propriedade de sistema, variável de ambiente ou outra chave do próprio YAML:

```yaml
dataSource:
    url: "${DB_URL:jdbc:h2:mem:dev}"
    password: "${DB_PASSWORD:}"
api:
    base: "https://exemplo.com"
    pagamentos: "${api.base}/pagamentos"
```

Na aplicação: `grailsApplication.config.getProperty('api.base')`, `config.getProperty('x', Integer, 10)` ou `config.api.base`. Como no Grails, `config.chaveInexistente.outra` não dá NullPointerException: devolve um navegador vazio que vale false (`config.x ?: padrao`, `for (p in config.x.lista)` não itera). O `getProperty('a.b')` explícito de uma chave inexistente devolve null. Placeholders `${...}` são resolvidos também dentro de listas de mapas (`rabbitmq.connections`).

| Chave | Padrão | Descrição |
|---|---|---|
| `server.port` | `8080` | Porta. `0` escolhe uma porta aleatória, útil em testes |
| `server.address` | `0.0.0.0` | Interface |
| `grox.controllers.concurrency` | `pool` | `pool` ou `request` |
| `grox.controllers.poolSize` | 2 × CPUs | Atores por controller no modo pool |
| `grox.session.timeout` | `1800` | Segundos de inatividade |
| `grox.session.cookieName` | `GROXSESSIONID` | |
| `grox.http.maxContentLength` | `10485760` | Corpo máximo, em bytes (acima disso, 413) |
| `grox.http.workerThreads` | `0` (Netty decide) | Event loops |
| `grox.http.requestTimeout` | `30` | Segundos para receber a requisição inteira a partir do primeiro byte (contra slowloris). `0` desliga |
| `grox.http.idleTimeout` | `60` | Segundos sem tráfego até fechar uma conexão keep-alive sem resposta pendente. `0` desliga |
| `grox.http.maxConnections` | `10000` | Conexões simultâneas; as excedentes são fechadas. `0` sem limite |
| `grox.http.maxPipelined` | `16` | Requisições em voo por conexão (HTTP pipelining) antes de pausar a leitura |
| `grox.http.trustedProxies` | loopback e redes privadas | Regex dos endereços cujos `X-Forwarded-For/Proto/Host` são aceitos, como o `internalProxies` do Tomcat. Vazio: nenhum |
| `grox.http.securityHeaders` | `nosniff`, `X-Frame-Options: SAMEORIGIN`, `Referrer-Policy: strict-origin-when-cross-origin` | Acrescentados às respostas que não os definem. Um mapa troca ou acrescenta (ex.: `Strict-Transport-Security`); valor vazio remove um; `false` desliga |
| `grox.session.cookie.secure` | `auto` | `Secure` no cookie de sessão: `auto` (em HTTPS), `true` ou `false` |
| `grox.session.serialization.allow` | | Pacotes extras aceitos na leitura da sessão do Redis |
| `grox.security.loginThrottle.enabled` | `true` | Limite de tentativas de login (form, REST e Basic). Acima dele: formulário com a mensagem `springSecurity.errors.login.throttled`; REST e Basic com `429` e `Retry-After` |
| `grox.security.loginThrottle.maxAttempts`, `...maxAttemptsPerUser`, `...windowSeconds` | `5`, `20`, `300` | Falhas por IP+usuário e por usuário (de qualquer IP) na janela. Não há limite só por IP: atrás de proxy, todos teriam o mesmo |
| `grox.i18n.defaultLocale` | locale da JVM | Por exemplo `pt-BR` |
| `grox.gsp.reload` | `true` em development | Views e estáticos lidos dos diretórios de fontes |
| `grox.pug.reload` / `grox.markdown.reload` | o de `grox.gsp.reload` | Relê as views `.pug` / `.md` quando mudam (sem ele, cache) |
| `grox.pug.prettyPrint` | `false` | HTML do Pug indentado |
| `grox.gsp.viewsDir` | `grails-app/views` | |
| `grox.reload.enabled` | `true` | Recarga de classes no `run` em development (ver [Recarga](#recarga-de-classes-em-desenvolvimento)). Também `-Dgrox.reload.enabled=false` |
| `-Dgrox.reload.compile` | `true` | `false`: não inicia o Gradle contínuo (a IDE compila) |
| `-Dgrox.reload.interval` | `500` | Intervalo do monitoramento, em ms |
| `grox.static.prefix` | `/static` | |
| `grox.static.dir` | `src/main/resources/public` | |
| `grails.serverURL` | | Base para links com `absolute: true` |
| `grox.serverURL` | | O mesmo, para os links das rotas do `grox-dsl` (que não leem `grails.serverURL`) |
| `grails.sitemesh.default.layout` | | Layout padrão |
| `grox.session.store` | `memory` | `memory` ou `redis` |
| `grox.session.redis.url`, `grox.session.redis.keyPrefix` | `redis://localhost:6379`, `grox:session:` | |
| `grails.plugin.springsecurity.*` | | Ver [Segurança](#segurança-estilo-spring-security-core) |
| `grails.gorm.multiTenancy.mode`, `tenantResolverClass` | `NONE` (`DISCRIMINATOR` se houver classes `MultiTenant`) | Ver [Multi-tenancy](#multi-tenancy-gorm-modo-discriminator) |
| `dataSource.url` | `jdbc:h2:mem:groxDb;DB_CLOSE_DELAY=-1` | JDBC. O driver precisa estar no classpath (`runtimeOnly`) |
| `dataSource.driverClassName`, `username`, `password` | —, `sa`, `''` | |
| `dataSource.dbCreate` | `none` | `create-drop`, `create`, `update`, `validate`, `none` |
| `dataSource.logSql`, `dataSource.formatSql` | `false` | Mostra o SQL gerado |
| `dataSource.properties.*` | | Repassado ao HikariCP (`maximumPoolSize`, `connectionTimeout`...) |
| `hibernate.*` | | Repassado ao Hibernate |
| `grox.management.enabled`, `basePath`, `metricsAccess` | `true`, `/actuator`, `[ROLE_ADMIN]` | Endpoints de gestão |
| `grox.accessLog.enabled`, `format` | `false`, `text` | Log de acesso (`text` ou `json`) |
| `grox.async.timeout` | `60` | Segundos para uma action assíncrona completar (senão, 503) |
| `grox.schema.update` | `safe` | Com `dbCreate: update`, `safe` só cria tabelas, colunas, FKs e índices, e nunca altera colunas existentes, como o Hibernate 5/GORM clássico. `full` é o comportamento do Hibernate 7, que faz `modify column` quando o mapeamento difere. Vale também com `hibernate.dialect` explícito de MySQL, MariaDB, H2 ou PostgreSQL (trocado pela versão safe da mesma classe) |
| `grox.schema.updateScript` | | Arquivo onde gravar o DDL que o `update` aplicaria, **sem executar nada**. Use antes de apontar para um banco existente |
| `grox.data.ormXmlFile` | | Diagnóstico: arquivo onde gravar o mapeamento (orm.xml) gerado para o Hibernate a partir das domains |
| `grox.schema.scriptOnly` | `false` | Com `updateScript`: encerra depois de gerar o script, antes do BootStrap (sem gravar dados) |
| `grox.startup.parallel` | `true` | Hibernate e metadados das domain classes sobem em paralelo com o resto |
| `grox.data.proxies` | `auto` | `auto`: proxy lazy (ByteBuddy) só para entidades referenciadas por many-to-one; as demais carregam na hora em `load(id)`. `all`: proxy em todas, como o GORM |
| `hibernate.default_batch_fetch_size` | `16` | Associações lazy pendentes carregadas juntas (`IN`), contra N+1 em listas. `1` volta ao comportamento do Hibernate |
| `grox.jobs.enabled` | `true` | Liga os jobs de `grails-app/jobs` (também `quartz.autoStartup`) |
| `grox.shutdown.timeout` | `30` | Segundos de espera pelas requisições no desligamento |
| `grails.plugin.springsecurity.rest.token.storage.jwt.refreshExpiration` | `2592000` | Validade do refresh token, em segundos |
| `...rest.token.generation.refreshEndpointUrl`, `...rest.logout.endpointUrl` | `/oauth/access_token`, `/api/logout` | |
| `grox.security.tokenRevocation.store` | igual a `grox.session.store` | `memory` ou `redis` |
| `grails.plugin.springsecurity.rest.token.storage.useGorm` | `false` | Tokens em domain class (`...gorm.tokenDomainClassName`, `...gorm.tokenValuePropertyName`, `...gorm.usernamePropertyName`) no lugar do JWT |
| `grails.plugin.springsecurity.rest.token.validation.useBearerToken`, `...validation.headerName` | `true`, `X-Auth-Token` | De onde vem o token: `Authorization: Bearer`, ou o cabeçalho configurado |

Fora de development, as páginas e o JSON de erro padrão não mostram a mensagem da exceção (SQL, tabelas, caminhos): só o status. O `/health` também omite a mensagem da falha do banco ou do Redis. As duas continuam no log.

O ambiente vem de `-Dgrails.env`, `-Dgrox.env`, `GRAILS_ENV` ou `GROX_ENV`. Aceita também `dev` e `prod`. Sem nenhum deles, vale `-Dgrox.env.default` e, por fim, **`production`**, como no Grails: o artefato empacotado (`installDist`, jar) sobe em production. O plugin `org.grox.app` define `development` na tarefa `run` e `test` nas tarefas de teste. A subida registra no log quando o ambiente veio do padrão. Fora do código Grails, o ambiente está em `org.grox.core.Env` (`Env.currentName`, `Env.development`) e em `groxApplication.environmentName`; o `grails.util.Environment` delega para ele.

## Testes de integração

```groovy
System.setProperty('grails.env', 'test')      // o environment test usa server.port: 0
def app = GroxApp.run(Application)
def base = "http://localhost:${app.port}"
def r = groovy.http.HttpBuilder.http { followRedirects false }.get(base + '/livros')   // HttpResult: status(), body(), headers()
app.close()
```

Os testes usam o `groovy.http.HttpBuilder` (dependência `groovy-http-builder`). Monte a URL absoluta (`base + path`): com `baseUri`, um caminho `//outro.host/x` iria para outro host. Use `TestClient` (em `sample-app/src/test`) para login por formulário com CSRF (`loggedIn('user', 'senha')`), JWT (`withToken`) e formulários (`form(path, campos)` já envia o `_csrf`). Com `forkEvery = 1` no Gradle, cada classe de teste sobe a própria aplicação.

`app.controllers`, `app.services.beans`, `app.datastore`, `app.security` e `app.config` permitem inspecionar o runtime. As domain classes podem ser usadas direto no teste (`Grupo.withTransaction { new Grupo(...).save() }`), porque o GORM é global na JVM.
