package sample

class Grupo {
    String nome
    /** Componente ({@code embedded}): colunas sede_cidade e sede_uf na tabela grupo. */
    Sede sede

    static hasMany = [usuarios: Usuario]
    static embedded = ['sede']

    static constraints = {
        nome blank: false, unique: true
        sede nullable: true
    }

    String toString() {
        nome
    }
}

/** Componente do Grupo, no mesmo arquivo como no GORM: não vira entidade (sem tabela própria). */
class Sede {
    String cidade
    String uf

    static constraints = {
        uf maxSize: 2
    }
}
