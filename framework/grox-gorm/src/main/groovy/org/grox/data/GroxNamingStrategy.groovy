package org.grox.data

import groovy.transform.CompileStatic
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment

/**
 * Nomes físicos como o naming do GORM ({@link DomainClass#snake}), também para identificadores entre aspas.
 * O snake_case do Hibernate ({@code CamelCaseToUnderscoresNamingStrategy}) separa dígitos ({@code valor10Meses}
 * → {@code valor10_meses_id}, mas o banco do Grails tem {@code valor10meses_id}) e não converte nomes entre aspas:
 * com {@code hibernate.auto_quote_keyword}, a classe {@code User} vira {@code `User`}, o que gerava a tabela
 * {@code User} em vez de {@code user} e a coluna {@code userCriado_id} em vez de {@code user_criado_id}.
 */
@CompileStatic
class GroxNamingStrategy implements PhysicalNamingStrategy {
    @Override
    Identifier toPhysicalCatalogName(Identifier name, JdbcEnvironment env) { apply(name) }

    @Override
    Identifier toPhysicalSchemaName(Identifier name, JdbcEnvironment env) { apply(name) }

    @Override
    Identifier toPhysicalTableName(Identifier name, JdbcEnvironment env) { apply(name) }

    @Override
    Identifier toPhysicalSequenceName(Identifier name, JdbcEnvironment env) { apply(name) }

    @Override
    Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment env) { apply(name) }

    private static Identifier apply(Identifier name) {
        name == null ? null : new Identifier(DomainClass.snake(name.text), name.quoted)
    }
}
