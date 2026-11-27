package org.grox.testdomain.heranca

/** Uma tabela por subclasse (tablePerHierarchy false). */
class Veiculo {
    String placa

    static mapping = {
        tablePerHierarchy false
    }
}
