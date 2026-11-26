package org.grox.testdomain

import grails.gorm.MultiTenant

class Nota implements MultiTenant<Nota> {
    String texto
}
