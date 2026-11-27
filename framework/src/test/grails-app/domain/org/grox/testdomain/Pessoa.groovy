package org.grox.testdomain

/** Domain com componentes ({@code embedded}): o Endereco fica no mesmo arquivo, como no GORM; o Contato, fora de grails-app. */
class Pessoa {
    String nome
    Endereco endereco
    org.grox.testdomain.componentes.Contato contato

    static embedded = ['endereco', 'contato']

    static constraints = {
        contato nullable: true
    }
}

class Endereco {
    String rua
    String cidade
    Uf uf
    Integer numeroPredial

    static constraints = {
        rua maxSize: 60
    }
}

enum Uf { SP, RJ, PR }
