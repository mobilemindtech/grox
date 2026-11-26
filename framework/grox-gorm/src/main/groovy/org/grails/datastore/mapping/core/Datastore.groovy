package org.grails.datastore.mapping.core

/**
 * Compatibilidade com o {@code Datastore} do GORM: o bean {@code hibernateDatastore}
 * ({@code org.grox.data.Datastore}) o implementa, para {@code ref('hibernateDatastore')} no
 * {@code resources.groovy} e listeners que o recebem no construtor.
 */
interface Datastore {
}
