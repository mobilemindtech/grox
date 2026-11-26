package sample.plain

import grails.gorm.transactions.Transactional

@Transactional
class LivroService {

    List<Livro> todos() {
        Livro.list(sort: 'titulo')
    }

    Livro buscar(Long id) {
        Livro.get(id)
    }

    /** O livro salvo ou, com erros de validação, sem id. */
    Livro salvar(Map dados) {
        def livro = new Livro(dados)
        livro.save()
        livro
    }

    Map<String, Long> porAutor() {
        Livro.list().countBy { it.autor }.collectEntries { k, v -> [k, v as Long] }
    }
}
