package grails.plugins.quartz

import groovy.transform.CompileStatic

/**
 * Compatibilidade com o plugin quartz: descrição de um job ({@code grailsApplication.getArtefacts('Job')}),
 * implementada por {@link DefaultGrailsJobClass}. Os valores vêm das propriedades estáticas do job
 * ({@code group}, {@code concurrent}, {@code sessionRequired}, {@code jobEnabled}, {@code description}...).
 */
@CompileStatic
interface GrailsJobClass {
    Class<?> getClazz()
    String getName()
    String getShortName()
    String getFullName()
    String getPropertyName()
    String getLogicalPropertyName()

    /** Executa o {@code execute()} numa instância nova do job (sem injeção), como o {@code referenceInstance} do Grails. */
    void execute()
    String getGroup()
    boolean isConcurrent()
    boolean isSessionRequired()
    boolean isDurability()
    boolean isRequestsRecovery()
    boolean isEnabled()
    String getDescription()

    /** Nome do trigger → objeto com {@code triggerAttributes} ({@code name}, {@code cronExpression} ou {@code repeatInterval}...). */
    Map getTriggers()
}
