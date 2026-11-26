# Manter este livro

Este livro é a documentação de quem usa o Grox. Ele só tem valor se acompanhar o código: **toda funcionalidade nova e toda mudança de comportamento entram no livro no mesmo commit**, junto com o teste e com os documentos internos (`docs/guia.md`, `docs/compatibilidade.md` e `docs/roadmap.md`). Uma mudança sem a documentação correspondente não está concluída.

## O que atualizar em cada tipo de mudança

| Mudança | Capítulos do livro | Também no repositório |
|---|---|---|
| Recurso novo (API, tag, opção, plugin) | O guia do recurso em `recursos/` (ou `sem-grails/`), com exemplo; a página de referência correspondente (`referencia/api-controllers.md`, `api-gorm.md`, `tags.md`, `beans.md`) | `docs/guia.md`, `docs/compatibilidade.md`, `docs/roadmap.md` |
| Chave de configuração nova ou padrão alterado | `referencia/configuracao.md` (chave, padrão, descrição) e o guia do recurso | Tabela de configuração em `docs/guia.md` |
| Compatibilidade com o Grails (nova, parcial ou removida) | `referencia/compatibilidade.md`; `migracao/plugins.md` se for de plugin; `migracao/diferencas.md` se for diferença intencional | `docs/compatibilidade.md` |
| Mudança de comportamento (padrão, segurança, erro) | O guia do recurso, `migracao/diferencas.md` se afetar apps migradas, e uma entrada em `recomendacoes/problemas.md` ou `faq.md` se for algo que o usuário vai encontrar | `docs/guia.md` |
| Plugin ou tarefa Gradle | `referencia/gradle.md` e, se mudar o projeto mínimo, `introducao/inicio-rapido.md` e `sem-grails/projeto-do-zero.md` | `docs/guia.md` |
| Recurso dos módulos sem Grails (`grox-dsl`) | `sem-grails/` (o capítulo do assunto e, se couber, o `tutorial.md`) | `docs/guia.md` |
| Armadilha nova do Groovy 6 que afeta apps | `apendices/groovy6.md` | `docs/desenvolvimento.md` |
| Problema encontrado num candidato à migração | `migracao/armadilhas.md` e, se ajudar, `migracao/casos-reais.md` | |
| Recurso removido ou renomeado | Todos os capítulos que o citam (procure com `grep -rn` em `docs/livro/src`) | Idem |

Mudanças internas que não alteram nada para quem usa (refatoração, desempenho sem mudança de API, testes) não precisam do livro, só dos documentos internos quando couber (`docs/arquitetura.md`, `docs/benchmark.md`).

## Convenções

- Português do Brasil, frases curtas, tabelas e exemplos de código. O exemplo precisa funcionar: copie de um teste ou do `sample-app`/`sample-plain` quando possível.
- Cada capítulo começa com `# Título` igual ao do `SUMMARY.md`, um parágrafo dizendo o que cobre e termina com `## Veja também`.
- Extensões do Grox (o que o Grails não tem) são marcadas com *(extensão Grox)*. Compatibilidade: ✅ suportado, 🟡 parcial, ❌ ausente.
- Links só entre capítulos do livro, com caminho relativo (`../recursos/controllers.md`). Arquivos do repositório são citados em código (`docs/roadmap.md`), porque links para fora de `src/` quebram no HTML.
- Capítulo novo: crie o arquivo e inclua-o no `src/SUMMARY.md`; o build falha se um capítulo do sumário não existir.

## Gerar o livro

```bash
docs/livro/build.sh            # gera docs/livro/build/ (HTML) e confere os links internos
docs/livro/build.sh serve      # servidor local com recarga em http://localhost:3000
```

O script usa o `mdbook` do `PATH` ou baixa a versão fixada (para `docs/livro/.mdbook/`). Ele confere se todo link relativo entre capítulos aponta para um arquivo existente (antes de gerar) e se toda âncora (`capitulo.md#secao`) existe no HTML gerado, e falha se não. Rode-o antes de concluir uma mudança que altere o livro.

## Veja também

- [Apresentação](../README.md)
- [Referência de configuração](../referencia/configuracao.md)
- [Compatibilidade com o Grails](../referencia/compatibilidade.md)
