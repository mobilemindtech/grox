package org.grox.testdomain

/** hasOne do GORM: a chave estrangeira fica no filho (Nariz.rosto). */
class Rosto {
    String nome
    static hasOne = [nariz: Nariz]

    static constraints = {
        nariz nullable: true
    }
}
