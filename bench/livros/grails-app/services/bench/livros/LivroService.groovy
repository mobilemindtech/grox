package bench.livros

import grails.gorm.transactions.Transactional

/** O mesmo service do sample-plain. */
@Transactional
class LivroService {

    List<Livro> todos() {
        Livro.list(sort: 'titulo')
    }

    Livro buscar(Long id) {
        Livro.get(id)
    }
}
