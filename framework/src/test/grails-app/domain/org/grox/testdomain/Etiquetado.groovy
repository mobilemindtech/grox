package org.grox.testdomain

/** hasMany de valores simples (element collection), como a CategoriaAssunto do app B. */
class Etiquetado {
    String nome
    List<String> tags = []
    Set<Integer> numeros = []

    static hasMany = [tags: String, numeros: Integer]
}
