package org.springframework.beans

/**
 * Compatibilidade com o {@code PropertyEditorRegistrar} do Spring: um bean que o implemente registra
 * editores usados pelo data binding (ex.: formato de {@code Date}). Chamado uma vez por requisição,
 * na primeira conversão, então pode depender do locale.
 */
interface PropertyEditorRegistrar {
    void registerCustomEditors(PropertyEditorRegistry registry)
}
