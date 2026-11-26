package extra

import grails.gorm.transactions.Transactional

@Transactional
class NotaService {

    Nota anotar(String texto) {
        new Nota(texto: texto).save(failOnError: true)
    }

    int total() {
        Nota.count() as int
    }
}
