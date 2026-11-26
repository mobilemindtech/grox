package grails.gorm.transactions

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Em services: a classe inteira (métodos públicos) ou o método roda em transação.
 * {@code rollbackFor}, {@code noRollbackFor}, {@code timeout}, {@code transactionManager} e
 * {@code connection} são aceitos para o código do Grails compilar, mas ainda não mudam nada: a
 * transação é desfeita em qualquer exceção (ver docs/compatibilidade.md).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD])
@interface Transactional {
    org.springframework.transaction.annotation.Propagation propagation() default org.springframework.transaction.annotation.Propagation.REQUIRED
    boolean readOnly() default false
    Class<? extends Throwable>[] rollbackFor() default []
    Class<? extends Throwable>[] noRollbackFor() default []
    int timeout() default -1
    String transactionManager() default ''
    String connection() default ''
}
