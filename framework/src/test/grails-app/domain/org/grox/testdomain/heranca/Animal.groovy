package org.grox.testdomain.heranca

/** Raiz abstrata de uma hierarquia em tabela única (padrão do GORM). */
abstract class Animal {
    String nome

    static constraints = {
        nome blank: false
    }
}
