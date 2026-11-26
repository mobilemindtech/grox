package org.grox.testdomain

import grails.gorm.MultiTenant

/** hasOne com filho multi-tenant (como Cliente.anamnese do app C): o filho pode faltar. */
class Ficha implements MultiTenant<Ficha> {
    String nome
    static hasOne = [anamnese: FichaAnamnese]

    static constraints = {
        anamnese nullable: true
    }
}
