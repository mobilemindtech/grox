package org.grox.core

import groovy.transform.CompileStatic

import java.lang.reflect.Modifier
import java.util.function.Predicate

/**
 * Tipo de artefato reconhecido na varredura das classes da aplicação ({@link GroxApplication#scan}).
 * O core registra só {@code Service}; os outros tipos vêm dos módulos ({@link GroxModule#getArtefactTypes}):
 * o GORM registra {@code Domain} e {@code DataService}, o GSP {@code TagLib}, o Grails
 * {@code Controller}, {@code UrlMappings}... Cada classe fica com o primeiro tipo que a aceitar, na
 * ordem de {@link #precedence}.
 */
@CompileStatic
class ArtefactType {

    /** Precedência dos tipos reconhecidos por marcador (interface, anotação), antes dos por sufixo. */
    static final int MARKER = 0
    static final int SUFFIX = 100

    final String name
    /** Menor primeiro. */
    final int precedence
    /** Aceita interface e classe abstrata (Data Services); os outros tipos só classes concretas. */
    final boolean abstractAllowed
    private final Predicate<Class<?>> test

    ArtefactType(String name, int precedence, boolean abstractAllowed, Predicate<Class<?>> test) {
        this.name = name
        this.precedence = precedence
        this.abstractAllowed = abstractAllowed
        this.test = test
    }

    /** Classe concreta cujo nome termina com o tipo ({@code LivroController}); com {@code exact}, também o nome sozinho ({@code UrlMappings}). */
    static ArtefactType suffix(String name, boolean exact = false) {
        new ArtefactType(name, SUFFIX, false, { Class<?> c ->
            String n = c.simpleName
            n.endsWith(name) && (exact || n != name)
        } as Predicate<Class<?>>)
    }

    /** Classe concreta que é subtipo de {@code marker} (trait, interface ou classe base). */
    static ArtefactType assignableTo(String name, int precedence, Class<?> marker) {
        new ArtefactType(name, precedence, false, { Class<?> c -> marker.isAssignableFrom(c) } as Predicate<Class<?>>)
    }

    boolean accepts(Class<?> c) {
        accepts(c, concrete(c))
    }

    /** @param concrete {@link #concrete} da classe, calculado uma vez para todos os tipos */
    boolean accepts(Class<?> c, boolean concrete) {
        (concrete || abstractAllowed) && test.test(c)
    }

    /** Classe instanciável: nem interface, enum, anotação ou abstrata. */
    static boolean concrete(Class<?> c) {
        !(c.interface || c.enum || c.annotation || Modifier.isAbstract(c.modifiers))
    }

    @Override
    String toString() { name }
}
