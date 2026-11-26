package org.grox.testdomain

class Aluno {
    String nome
    String senha
    String codigo
    long pontos

    static belongsTo = Turma
    static hasMany = [turmas: Turma]

    static constraints = {
        senha nullable: true
    }

    /** construtor sem argumentos declarado: o construtor com Map ainda deve existir */
    Aluno() {
        codigo = 'A-' + System.nanoTime()
    }
}
