package org.grox.testdomain

/** many-to-many (lado dono: Aluno faz belongsTo Turma). */
class Turma {
    String nome
    static hasMany = [alunos: Aluno]
}
