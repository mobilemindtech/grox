package org.grox.testdomain

/** Dois níveis de associação até o autor (capitulo.livro.autor), para as consultas where. */
class Capitulo {
    String titulo
    Integer paginas
    Livro livro
}
