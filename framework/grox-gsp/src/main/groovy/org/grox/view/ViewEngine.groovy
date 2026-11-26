package org.grox.view

import org.grox.view.gsp.GspRenderContext

/**
 * Outro formato de view ao lado da GSP (Pug, Markdown...), registrado por um módulo em {@link GspViews#register}.
 * Quando não há {@code <view>.gsp}, o {@code render view:} (e o {@code g:render template:}) procura a view nos
 * motores registrados, na ordem de registro. A saída passa pelos mesmos layouts SiteMesh e roda com o
 * {@link GspRenderContext} vinculado, então as TagLibs funcionam dentro do motor.
 */
interface ViewEngine {
    /** Extensão dos arquivos, sem ponto ({@code pug}, {@code md}). */
    String getExtension()

    /** {@code path} sem extensão ({@code /livros/index}). */
    boolean exists(String path)

    /** Escreve a view renderizada em {@code rc.out}. */
    void render(String path, Map model, GspRenderContext rc)
}
