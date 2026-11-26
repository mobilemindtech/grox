package org.grox.session

/**
 * Valor de sessão que sabe se mudou depois de carregado. Repositórios remotos (Redis) regravam
 * atributos lidos com valor mutável, porque {@code session.lista << x} não passa pelo
 * {@code setAttribute}. Quem implementa esta interface só é regravado quando {@link #isModified()}.
 */
interface SessionValue {

    boolean isModified()
}
