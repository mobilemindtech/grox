package org.grox.testdomain

/** Plugin hibernate-filter: só os ativos aparecem por padrão. */
class Produto {
    String nome
    boolean ativo = true

    static hibernateFilters = {
        ativoFilter(condition: 'ativo = true', default: true)
        caroFilter(condition: "nome like 'caro%'")
    }
}
