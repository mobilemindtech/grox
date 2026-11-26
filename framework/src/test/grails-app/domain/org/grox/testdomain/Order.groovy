package org.grox.testdomain

/** Nome reservado (order) e associação camelCase: tabela `order`, coluna responsavel_principal_id. */
class Order {
    String codigo
    BigDecimal bruto
    BigDecimal desconto
    BigDecimal liquido
    Aluno responsavelPrincipal
    Aluno responsavel2Turno
    String codigoERP

    static constraints = {
        responsavelPrincipal nullable: true
        responsavel2Turno nullable: true
        codigoERP nullable: true
    }

    static mapping = {
        liquido formula: 'bruto - desconto'
    }
}
