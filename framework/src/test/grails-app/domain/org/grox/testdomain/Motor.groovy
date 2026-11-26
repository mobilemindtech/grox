package org.grox.testdomain

/** Lado inverso de Carro.motor: sem coluna carro_id. */
class Motor {
    String serie
    static belongsTo = [carro: Carro]
}
