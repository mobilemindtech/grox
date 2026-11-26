package org.grox.testdomain

/** One-to-one do GORM sem hasOne (Face/Nose da documentação): a chave fica no dono (carro.motor_id). */
class Carro {
    String modelo
    Motor motor

    static constraints = {
        motor nullable: true
    }
}
