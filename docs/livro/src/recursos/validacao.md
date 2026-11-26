# Validação, binding e mensagens

Este capítulo cobre as constraints e seus códigos de erro, a API `errors`, as mensagens i18n, os command objects `Validateable` e o data binding: como `params` e JSON viram propriedades, o que é protegido, conversão de datas e o erro `typeMismatch`.

## Constraints

```groovy
static constraints = {
    nome blank: false, size: 2..100
    email email: true, unique: true
    idade nullable: true, min: 0, max: 150
    site nullable: true, url: true
    cpf matches: /\d{11}/
    status inList: ['ATIVO', 'INATIVO']
    codigo unique: ['empresa']                   // único dentro da empresa
    apelido nullable: true, validator: { v, obj -> v != obj.nome ?: 'igualAoNome' }
    foto nullable: true, maxSize: 2 * 1024 * 1024
    tenantId bindable: false
}
```

| Constraint | Código do erro |
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
| `unique` (true, uma propriedade ou lista de escopo) | `unique` |
| `validator` | `validator.invalid`, ou o código retornado |
| `bindable: false` | não valida: tira a propriedade do binding |

- **Obrigatório por padrão:** em domain classes e em `Validateable`, toda propriedade é `nullable: false`, exceto `dateCreated` e `lastUpdated`.
- **`validator`:** recebe `(valor)`, `(valor, objeto)` ou `(valor, objeto, errors)`. Devolve `true`/`null` para válido, `false` para `validator.invalid`, uma String com o código próprio (`'igualAoNome'`) ou uma lista com código e argumentos (`['meuCodigo', 10]`).
- **`unique`** consulta o banco com flush manual, então a entidade inválida não é gravada pelo auto-flush.
- Não implementadas: `creditCard`, `scale`, `notEqual`, `shared` e `importFrom`.

## Validar e ler os erros

```groovy
def u = new Usuario(params)
if (!u.validate()) {
    u.errors.allErrors.each { println it }
}
u.save()                 // valida antes; devolve null se inválido
u.save(failOnError: true)   // lança ValidationException
u.save(validate: false)  // grava sem validar
```

| Membro de `errors` | |
|---|---|
| `hasErrors()`, `errorCount` | Há erros, quantos |
| `allErrors`, `fieldErrors` | Todos, só os de campo |
| `getFieldError('campo')`, `hasFieldErrors('campo')` | Erro de um campo |
| `rejectValue('campo', 'codigo')` | Acrescenta um erro de campo |
| `reject('codigo')` | Acrescenta um erro global |

Na instância: `validate()`, `hasErrors()`, `errors` e `clearErrors()`. Os erros implementam as interfaces do Spring (`org.springframework.validation.Errors`, `FieldError`, `ObjectError`), então código tipado com elas compila.

Se a validação falhar dentro de uma transação, a entidade fica **somente leitura** para que o commit não grave o estado inválido. Antes de continuar com outra transação na mesma requisição, chame `discard()`.

## Mensagens

Cada `FieldError` tem códigos do mais específico ao mais genérico. Para `email` com `unique` em `sample.Usuario`:

1. `sample.Usuario.email.unique`
2. `usuario.email.unique`
3. `default.not.unique.message`

Os argumentos da mensagem são `{0}` a propriedade, `{1}` a classe, `{2}` o valor rejeitado e `{3}...` os parâmetros da constraint (o mínimo do `min`, os limites do `size`).

```properties
# grails-app/i18n/messages.properties
usuario.email.unique=Já existe um usuário com o e-mail {2}
usuario.nome.size.toosmall=O nome precisa ter pelo menos {3} letras
```

O framework traz as mensagens padrão do Grails em inglês e português. A aplicação só define o que quer mudar.

## Command objects

```groovy
import grails.validation.Validateable

class BuscaCommand implements Validateable {
    String termo
    Integer pagina
    static constraints = {
        termo blank: false
        pagina nullable: true, min: 1
    }
}

def buscar(BuscaCommand cmd) {
    if (cmd.hasErrors()) return render(view: 'index', model: [cmd: cmd])
    ...
}
```

Um argumento de action que não é tipo simples vira command object: é instanciado, recebe injeção por nome, é preenchido com o JSON do corpo (com `Content-Type: application/json`) ou com `params`, e tem `validate()` chamado se existir. As constraints funcionam como nas domain classes, com propriedades obrigatórias por padrão.

## Binding

Formas de binding:

```groovy
new Usuario(params)
u.properties = params
bindData(u, params)
bindData(u, params, [include: ['nome', 'email']])
bindData(u, params, [exclude: ['senha']])
def save(Usuario usuario) { ... }        // argumento da action
```

Regras:

- **Nunca vinculados:** `id`, `version`, `dateCreated`, `lastUpdated` e a propriedade de tenant (também fora do JSON das entidades).
- **`bindable: false`** tira a propriedade do binding, em domain classes e command objects. Um `include` explícito ainda a vincula. Use em campos que o usuário não escolhe, como tenant, papéis e flags de conta em telas de autoedição:

  ```groovy
  static constraints = {
      userTenantId bindable: false
      enabled bindable: false
  }
  // numa tela de administração:
  bindData(user, params, [include: ['enabled', 'accountLocked']])
  ```

- Strings são aparadas, e string vazia vira `null`.
- `params` aninhados: `endereco.cidade=X` vai para `usuario.endereco.cidade`.
- `checkBox` gera `_nome` para que o desmarcado vire `false`.
- `MultipartFile` é copiado para propriedades `byte[]` (arquivo vazio é ignorado) ou `MultipartFile`.

### Associações

| Entrada | Efeito |
|---|---|
| `grupo.id=3` (params) | `usuario.grupo = Grupo.get(3)` |
| `grupo=3` | idem |
| `{"grupo": {"id": 3}}` (JSON) | idem |
| `grupo.id=` (vazio) ou `null` | limpa a associação |

### Domain class como argumento da action

Se a requisição traz `id`, a entidade é **carregada** e recebe o binding (`null` se não existir). Sem `id`, é criada uma nova. Nos dois casos `validate()` já foi chamado:

```groovy
def update(Usuario usuario) {
    if (usuario == null) return render(status: 404)
    if (usuario.hasErrors()) return respond(usuario.errors, view: 'edit')
    usuario.save()
    redirect action: 'show', id: usuario.id
}
```

### Valor que não converte (`typeMismatch`)

`idade=abc` deixa a propriedade `null` e registra um erro `typeMismatch` em `errors`:

- o erro sobrevive ao `validate()`, e o campo não recebe também o erro de `nullable`;
- códigos, do mais específico ao genérico: `typeMismatch.sample.Usuario.idade`, `typeMismatch.idade`, `typeMismatch.java.lang.Integer`, `typeMismatch`;
- mensagens padrão em pt e en.

Parâmetros simples de action (`show(Long id)`) com valor inválido viram `null`, sem erro.

### Datas

Formatos aceitos para texto em `Date`, nesta ordem:

1. `@BindingFormat('dd/MM/yyyy')` na propriedade (vale para `Date`, `java.time` e números);
2. `grails.databinding.dateFormats` no yml, tentados na ordem e sem leniência:

   ```yaml
   grails:
     databinding:
       dateFormats: ['dd/MM/yyyy', 'dd/MM/yyyy HH:mm']
   ```

3. editores registrados pela aplicação (abaixo);
4. os formatos ISO do Grox (`yyyy-MM-dd`, `yyyy-MM-dd'T'HH:mm:ss`...).

O `g:datePicker` envia `nome_day`, `nome_month` e `nome_year` (e hora e minuto), e o binding os junta.

### Editores do Spring

Um bean que implementa `PropertyEditorRegistrar` registra editores usados na conversão de texto, como `CustomDateEditor` ou `StructuredDateEditor`:

```groovy
class DataEditorRegistrar implements PropertyEditorRegistrar {
    void registerCustomEditors(PropertyEditorRegistry r) {
        r.registerCustomEditor(Date, new CustomDateEditor(new SimpleDateFormat('dd/MM/yyyy'), true))
    }
}
// resources.groovy
beans = { dataEditorRegistrar(DataEditorRegistrar) }
```

O registro é feito uma vez por requisição, então pode depender do locale. O editor vale por tipo (o caminho da propriedade é ignorado). Texto que o editor recusa segue para a conversão padrão; campo vazio vira `null` antes de chegar ao editor.

## Erros nas views

```html
<g:hasErrors bean="${usuario}">
  <ul><g:eachError bean="${usuario}" var="erro"><li><g:message error="${erro}"/></li></g:eachError></ul>
</g:hasErrors>
<g:renderErrors bean="${usuario}"/>
<p class="${hasErrors(bean: usuario, field: 'nome', 'com-erro')}">
  <g:textField name="nome" value="${fieldValue(bean: usuario, field: 'nome')}"/>
</p>
```

`fieldValue` mostra o valor rejeitado quando houve erro, para o usuário corrigir o que digitou.

## Erros em JSON

```groovy
respond usuario.errors, status: 422
```

```json
{"errors": [{"object": "sample.Usuario", "field": "email", "rejected-value": "x", "message": "..."}]}
```

As mensagens saem traduzidas no locale da requisição.

## Diferenças para o Grails

| Recurso | Grox |
|---|---|
| `creditCard`, `scale`, `notEqual`, `shared`, `importFrom` | ❌ |
| Transients e propriedades `def` | Ainda são vinculados (o Grails não vincula por padrão) |
| Editor do Spring por caminho de propriedade | Vale por tipo |
| Parâmetro simples de action inválido | `null` sem erro |

## Veja também

- [Domain classes](domain.md)
- [Controllers](controllers.md)
- [Internacionalização](i18n.md)
- [Upload de arquivos](upload.md)
- [Segurança: CSRF e proteções](seguranca-protecoes.md)
