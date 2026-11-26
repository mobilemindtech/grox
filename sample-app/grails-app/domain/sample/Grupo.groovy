package sample

class Grupo {
    String nome

    static hasMany = [usuarios: Usuario]

    static constraints = {
        nome blank: false, unique: true
    }

    String toString() {
        nome
    }
}
