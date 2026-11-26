package extra

/** Bean declarado no doWithSpring() do ExtraGrailsPlugin. */
class FormatadorNota {
    String prefixo
    NotaService notaService

    String formatar(String texto) { "${prefixo}${texto} (${notaService.total()})" }
}
