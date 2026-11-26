package org.springframework.transaction.annotation

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * {@code @Transactional} do Spring em services: tratado como o {@code grails.gorm.transactions.Transactional}.
 * {@code propagation} REQUIRED (padrão), REQUIRES_NEW (sessão e transação novas), SUPPORTS/NOT_SUPPORTED/NEVER
 * (sem abrir transação) e MANDATORY; {@code isolation} e {@code timeout} são aceitos, sem efeito.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD])
@interface Transactional {
    Propagation propagation() default Propagation.REQUIRED
    Isolation isolation() default Isolation.DEFAULT
    boolean readOnly() default false
    int timeout() default -1
    Class<? extends Throwable>[] rollbackFor() default []
    Class<? extends Throwable>[] noRollbackFor() default []
    String value() default ''
    String transactionManager() default ''
}
