package org.grox.testdomain

/** Chave composta como o UserRole do spring-security. */
class Inscricao implements Serializable {
    Turma turma
    Aluno aluno
    String observacao

    static constraints = {
        observacao nullable: true
    }

    static mapping = {
        id composite: ['turma', 'aluno']
        version false
    }

    boolean equals(other) {
        other instanceof Inscricao && other.turma?.id == turma?.id && other.aluno?.id == aluno?.id
    }

    int hashCode() {
        Objects.hash(turma?.id, aluno?.id)
    }
}
