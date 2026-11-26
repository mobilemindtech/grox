package org.grox.dsl

/**
 * Delegate do {@code bootstrap { }} da DSL de projeto: {@code init { }} roda na subida, depois dos services e
 * antes do servidor; {@code destroy { }} no desligamento. Os closures acessam beans e services pelo nome.
 */
class BootStrapBlock {

    final List<Closure> inits
    final List<Closure> destroys

    BootStrapBlock(List<Closure> inits, List<Closure> destroys) {
        this.inits = inits
        this.destroys = destroys
    }

    void init(Closure body) {
        inits << body
    }

    void destroy(Closure body) {
        destroys << body
    }
}
