package org.grox.testdomain.heranca

class Gato extends Animal {
    Boolean ronrona = true

    static mapping = {
        discriminator 'GATO'
    }
}
