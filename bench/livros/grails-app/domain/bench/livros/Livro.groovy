package bench.livros

/** A mesma domain class do sample-plain. */
class Livro {
    String titulo
    String autor
    Integer ano

    static constraints = {
        titulo blank: false
        autor blank: false
        ano nullable: true, min: 1450
    }
}
