package org.grox.view


interface ViewRenderer {
    /** Renderiza {@code viewPath}.gsp com o modelo (aplica layout). */
    void render(String viewPath, Map model, ViewRequest request)

    /** Renderiza um template ({@code _nome.gsp}) sem layout. */
    void renderTemplate(String templatePath, Map attrs, ViewRequest request)
}
