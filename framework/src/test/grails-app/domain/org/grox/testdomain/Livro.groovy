package org.grox.testdomain

class Livro {
    String titulo
    Situacao situacao = Situacao.ATIVO
    BigDecimal preco

    static belongsTo = [autor: Autor]

    static constraints = {
        preco nullable: true, min: 0.0G
        titulo validator: { String v -> v?.startsWith('X') ? 'proibido' : true }
    }
}
