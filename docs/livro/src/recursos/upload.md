# Upload de arquivos

Este capítulo mostra como receber arquivos por formulários multipart: o formulário, a leitura do arquivo na action, o binding em domain classes e os limites.

## Formulário

```html
<g:uploadForm action="enviarFoto" id="${usuario.id}">
    <input type="file" name="foto"/>
    <g:submitButton name="enviar"/>
</g:uploadForm>
```

`<g:uploadForm>` é um `<g:form>` com `enctype="multipart/form-data"`. O `_csrf` e o `_method` funcionam em formulários multipart.

## Na action

```groovy
def enviarFoto(Long id) {
    def usuario = Usuario.get(id)
    def f = request.getFile('foto')          // ou um parâmetro MultipartFile foto
    if (f && !f.empty) {
        usuario.foto = f.bytes
        usuario.fotoTipo = f.contentType
        usuario.save()
    }
    redirect action: 'show', id: id
}

def enviarVarios() {
    request.getFiles('anexos').each { f -> f.transferTo(new File("/tmp/${f.originalFilename}")) }
}

def enviar(MultipartFile arquivo) { ... }    // org.springframework.web.multipart.MultipartFile
```

| `MultipartFile` | |
|---|---|
| `originalFilename`, `name` | Nome do arquivo e do campo |
| `contentType`, `size`, `empty` | Metadados |
| `bytes`, `inputStream` | Conteúdo |
| `transferTo(file)` | Grava em disco |

No `request`: `getFile('campo')`, `getFiles('campo')`, `fileNames`, `multipart`. O `request` também implementa `MultipartHttpServletRequest`. Os arquivos também aparecem em `params`.

## Binding

O binding copia um `MultipartFile` para uma propriedade `byte[]` ou `MultipartFile` de mesmo nome. Arquivo vazio é ignorado (não apaga o valor anterior).

```groovy
class Usuario {
    byte[] foto
    String fotoTipo
    static constraints = {
        foto nullable: true, maxSize: 2 * 1024 * 1024     // define o tamanho da coluna
        fotoTipo nullable: true, inList: ['image/png', 'image/jpeg']
    }
}
```

Se o tipo for inválido, o `save()` falha e nada é gravado.

## Limites

- O corpo inteiro fica **em memória**. O limite é `grox.http.maxContentLength` (padrão 10 MB); acima dele, `413`. O `100-continue` respeita o mesmo limite.
- Streaming para disco não existe.

```yaml
grox:
  http:
    maxContentLength: 52428800   # 50 MB
```

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| Multipart do container (pode ir para disco) | Sempre em memória, limitado por `grox.http.maxContentLength` |
| `request.getParts()` (Servlet) | `UnsupportedOperationException`: use `getFile`/`getFiles` |

## Veja também

- [Controllers](controllers.md)
- [Validação, binding e mensagens](validacao.md)
- [Configuração](../referencia/configuracao.md)
