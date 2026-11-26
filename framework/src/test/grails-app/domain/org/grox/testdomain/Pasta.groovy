package org.grox.testdomain

/** hasMany auto-referente e unidirecional (como Plano.planosDisponiveisDessaAtividade do app C). */
class Pasta {
    String nome

    static hasMany = [relacionadas: Pasta, livros: Livro]
}
