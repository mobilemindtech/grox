package sample.plain

import grails.gorm.annotation.Entity

/** Domain class fora de grails-app/domain: @Entity dá a API do GORM, id, version e o construtor com Map. */
@Entity
class Livro {
    String titulo
    String autor
    Integer ano

    static constraints = {
        titulo blank: false
        autor blank: false
        ano nullable: true, min: 1450
    }
}
