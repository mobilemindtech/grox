package org.grox.testdomain.heranca

class Cachorro extends Animal {
    String raca

    static constraints = {
        raca nullable: true
    }
}
