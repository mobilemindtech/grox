package org.grox.web.interceptor

/** Constantes de ordem dos interceptors ({@code int order = HIGHEST_PRECEDENCE + 50}). */
interface Ordered {
    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE
}
