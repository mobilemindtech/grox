package org.grox.service

/**
 * Comportamento em volta dos métodos de um service (transação e tenant do GORM, por exemplo).
 * Descoberto por {@link ServiceLoader} ({@code META-INF/services/org.grox.service.ServiceInterceptor});
 * o de menor {@link #getOrder} fica por fora.
 */
interface ServiceInterceptor {

    int getOrder()

    /**
     * Por nome de método, um closure que recebe a chamada ({@code Closure} sem argumentos) e a executa
     * com o comportamento do interceptador. Vazio se nenhum método da classe é interceptado.
     *
     * @param methods os métodos que o proxy atende
     */
    Map<String, Closure> interceptors(Class<?> type, Object target, Set<String> methods)
}
