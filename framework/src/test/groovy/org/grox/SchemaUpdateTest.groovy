package org.grox

import org.grox.core.Config
import org.grox.data.Datastore
import org.grox.data.Gorm
import org.grox.data.SafeUpdateDialects
import org.grox.testdomain.Autor
import org.grox.testdomain.Livro
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import java.sql.DriverManager

/** {@code dbCreate: update} no modo safe (padrão), inclusive com {@code hibernate.dialect} explícito. */
class SchemaUpdateTest {

    Datastore datastore

    @AfterEach
    void stop() {
        Gorm.reset()
        datastore?.close()
    }

    @Test
    void 'hibernate dialect explicito continua no modo safe e nao altera colunas existentes'() {
        String url = 'jdbc:h2:mem:schemaUpdate;DB_CLOSE_DELAY=-1'
        def conn = DriverManager.getConnection(url, 'sa', '')
        // tabela legada: nome menor e email maior que o mapeamento
        conn.createStatement().execute('create table autor (id bigint auto_increment primary key, version bigint, nome varchar(10) not null, ' +
                'email varchar(500), idade int, ativo boolean not null, date_created timestamp, last_updated timestamp)')
        File script = File.createTempFile('grox-update', '.sql')
        try {
            // como o app C: hibernate.dialect declarado no application.yml
            def config = new Config([dataSource: [url: url, dbCreate: 'update'],
                                     hibernate : [dialect: 'org.hibernate.dialect.H2Dialect'],
                                     grox       : [schema: [updateScript: script.absolutePath]]])
            datastore = new Datastore(config, [Autor, Livro], SchemaUpdateTest.classLoader)
            def dialect = ((SessionFactoryImplementor) datastore.sessionFactory).jdbcServices.dialect
            assert dialect instanceof SafeUpdateDialects.SafeH2
            String ddl = script.text.toLowerCase()
            assert ddl.contains('create table livro')                // tabelas novas continuam sendo criadas
            assert !ddl.contains('alter column') && !ddl.contains('modify')   // e as colunas existentes, intactas
            // controle: no modo full (Hibernate 7 puro), o mesmo banco geraria alter column
            datastore.close()
            Gorm.reset()
            config = new Config([dataSource: [url: url, dbCreate: 'update'],
                                 hibernate : [dialect: 'org.hibernate.dialect.H2Dialect'],
                                 grox       : [schema: [update: 'full', updateScript: script.absolutePath]]])
            datastore = new Datastore(config, [Autor, Livro], SchemaUpdateTest.classLoader)
            datastore.sessionFactory
            assert script.text.toLowerCase().contains('alter column')
        } finally {
            script.delete()
            conn.createStatement().execute('drop all objects')
            conn.close()
        }
    }

    @Test
    void 'safeFor so troca os dialects conhecidos'() {
        assert SafeUpdateDialects.safeFor('org.hibernate.dialect.MySQLDialect') == SafeUpdateDialects.SafeMySQL.name
        assert SafeUpdateDialects.safeFor('org.hibernate.dialect.MariaDBDialect') == SafeUpdateDialects.SafeMariaDB.name
        assert SafeUpdateDialects.safeFor('org.hibernate.dialect.OracleDialect') == null
    }
}
