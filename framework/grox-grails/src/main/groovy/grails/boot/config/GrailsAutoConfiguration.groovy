package grails.boot.config

/**
 * Compatibilidade: a classe {@code Application} pode continuar estendendo esta. O Grox avalia o
 * {@code doWithSpring()} dela (beans no DSL do resources.groovy) depois dos plugins e antes do
 * resources.groovy, como o Grails.
 */
class GrailsAutoConfiguration {

    /** Beans da aplicação no DSL do resources.groovy; {@code null} se não houver. */
    Closure doWithSpring() { null }
}
