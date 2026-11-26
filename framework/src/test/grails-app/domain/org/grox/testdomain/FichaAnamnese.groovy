package org.grox.testdomain

import grails.gorm.MultiTenant

class FichaAnamnese implements MultiTenant<FichaAnamnese> {
    String observacao
    static belongsTo = [ficha: Ficha]
}
