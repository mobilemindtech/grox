package org.grox.testdomain

class Autor {
    String nome
    String email
    Integer idade
    boolean ativo = true
    Date dateCreated
    Date lastUpdated

    static hasMany = [livros: Livro]

    static constraints = {
        nome blank: false, size: 2..50
        email nullable: true, email: true, unique: true
        idade nullable: true, min: 0
    }

    static mapping = {
        sort 'nome'
    }
}
