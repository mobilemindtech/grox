package org.grox.routing

/**
 * Destino de uma rota pelo nome ({@code controller} e {@code action} no mapeamento): o que o
 * roteamento precisa saber para casar um caminho e gerar um link. Os controllers do módulo Grails
 * ({@code ControllerArtefact}) são um tipo de destino; quem atende a requisição recebe o destino
 * casado em {@link RouteMatch#controller}.
 */
interface RouteTarget {
    /** Ação usada quando o mapeamento não define uma ({@code /livro} → {@code index}), ou null. */
    String getDefaultAction()

    boolean hasAction(String name)
}
