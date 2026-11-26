package sample

/** Bean simples declarado no doWithSpring() da Application. */
class Etiqueta {
    String texto
    Etiqueta proxima

    Etiqueta() {}

    Etiqueta(String texto) { this.texto = texto }
}
