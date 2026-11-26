package org.grox.core.reload

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Marca as classes de {@code grails-app/{controllers,services,taglib,init,jobs}} (aplicada pela
 * {@code ArtefactTransformation}). A recarga em desenvolvimento as troca sem recriar o Hibernate;
 * as demais (domain, src/main/groovy, utils) exigem a recarga completa. Ver {@link DevReloader}.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@interface Reloadable {
}
