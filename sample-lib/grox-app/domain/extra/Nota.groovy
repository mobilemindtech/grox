package extra

/** Domain class vinda de uma biblioteca org.grox.lib, em pacote diferente do da aplicação. */
class Nota {
    String texto

    static constraints = {
        texto blank: false
    }
}
