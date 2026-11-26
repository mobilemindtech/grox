package org.grox.data

import groovy.transform.CompileStatic
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.engine.jdbc.dialect.internal.StandardDialectResolver
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo
import org.hibernate.engine.jdbc.dialect.spi.DialectResolver

/**
 * {@code dbCreate: update} seguro, como no Hibernate 5 / GORM clássico: cria tabelas, colunas,
 * FKs e índices, mas **nunca altera o tipo, o tamanho ou a nulidade de uma coluna existente**.
 * O {@code update} do Hibernate 7 faz {@code alter table ... modify column} quando o mapeamento
 * difere do banco, o que num banco legado pode truncar dados ou falhar com {@code not null}.
 *
 * Ativado por padrão ({@code grox.schema.update: safe}); {@code full} volta ao comportamento do Hibernate.
 * Vale para MySQL, MariaDB, H2 e PostgreSQL: detectado pelo banco ou, com {@code hibernate.dialect} explícito
 * de um deles, trocado pela versão safe da mesma classe ({@link #safeFor}).
 */
@CompileStatic
class SafeUpdateDialects implements DialectResolver {

    private final StandardDialectResolver standard = new StandardDialectResolver()

    @Override
    Dialect resolveDialect(DialectResolutionInfo info) {
        Dialect d = standard.resolveDialect(info)
        if (d == null) return null
        // MariaDB antes de MySQL: MariaDBDialect estende MySQLDialect
        if (d instanceof MariaDBDialect) return new SafeMariaDB(info)
        if (d instanceof MySQLDialect) return new SafeMySQL(info)
        if (d instanceof H2Dialect) return new SafeH2(info)
        if (d instanceof PostgreSQLDialect) return new SafePostgreSQL(info)
        d
    }

    /** Versão safe de um {@code hibernate.dialect} explícito; null se não houver (dialect de outro banco ou já próprio). */
    static String safeFor(String dialect) {
        switch (dialect) {
            case MySQLDialect.name: return SafeMySQL.name
            case MariaDBDialect.name: return SafeMariaDB.name
            case H2Dialect.name: return SafeH2.name
            case PostgreSQLDialect.name: return SafePostgreSQL.name
            default: return null
        }
    }

    static class SafeMySQL extends MySQLDialect {
        SafeMySQL(DialectResolutionInfo info) { super(info) }
        /** {@code hibernate.dialect} explícito: o Hibernate instancia sem argumentos, como o dialect original. */
        SafeMySQL() { super() }
        @Override boolean supportsAlterColumnType() { false }
    }

    static class SafeMariaDB extends MariaDBDialect {
        SafeMariaDB(DialectResolutionInfo info) { super(info) }
        /** {@code hibernate.dialect} explícito: o Hibernate instancia sem argumentos, como o dialect original. */
        SafeMariaDB() { super() }
        @Override boolean supportsAlterColumnType() { false }
    }

    static class SafeH2 extends H2Dialect {
        SafeH2(DialectResolutionInfo info) { super(info) }
        /** {@code hibernate.dialect} explícito: o Hibernate instancia sem argumentos, como o dialect original. */
        SafeH2() { super() }
        @Override boolean supportsAlterColumnType() { false }
    }

    static class SafePostgreSQL extends PostgreSQLDialect {
        SafePostgreSQL(DialectResolutionInfo info) { super(info) }
        /** {@code hibernate.dialect} explícito: o Hibernate instancia sem argumentos, como o dialect original. */
        SafePostgreSQL() { super() }
        @Override boolean supportsAlterColumnType() { false }
    }
}
