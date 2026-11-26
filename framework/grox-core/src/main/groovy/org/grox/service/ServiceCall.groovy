package org.grox.service

/** Mensagem para um service-ator; leva o contexto (sessão de dados, autenticação, tenant) de quem chamou. */
record ServiceCall(String method, Object[] args, Map<ScopedValue, Object> context) {}
