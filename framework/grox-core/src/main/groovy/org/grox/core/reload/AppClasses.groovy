package org.grox.core.reload

import groovy.transform.CompileStatic
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.Opcodes
import groovyjarjarasm.asm.commons.ClassRemapper
import groovyjarjarasm.asm.commons.Remapper

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.zip.CRC32

/**
 * Retrato das classes compiladas da aplicação, lido com ASM sem carregar nada. Divide as classes
 * em duas camadas:
 * <ul>
 *   <li><b>recarregável</b>: marcadas com {@link Reloadable} (controllers, services, taglibs, init, jobs)</li>
 *   <li><b>estável</b>: o resto (domain, src/main/groovy, utils). Um artefato recarregável referenciado
 *       por uma classe estável também fica estável ("fixado"), senão existiriam duas cópias dele.</li>
 * </ul>
 * O CRC de cada classe permite saber o que mudou entre dois retratos (o compilador do Groovy reescreve
 * todos os arquivos, mas gera bytes idênticos para o que não mudou).
 */
@CompileStatic
class AppClasses {

    private static final String RELOADABLE_DESC = 'L' + Reloadable.name.replace('.', '/') + ';'

    /** Nome binário (com as internas) → CRC do bytecode. */
    final Map<String, Long> checksums = [:]
    /**
     * Bytecode lido no retrato: os classloaders definem as classes daqui, não do disco, que o compilador
     * apaga e reescreve enquanto a geração atual ainda carrega classes sob demanda.
     */
    final Map<String, byte[]> bytecode = [:]
    /** Classes de nível superior da camada recarregável (já sem as fixadas). */
    final Set<String> reloadable = new HashSet<>()
    /** Classes de nível superior da camada estável. */
    final Set<String> stable = new HashSet<>()
    /** Marcadas como recarregáveis mas fixadas na camada estável: nome → classe estável que a referencia. */
    final Map<String, String> pinned = [:]

    static AppClasses scan(List<Path> dirs) {
        def result = new AppClasses()
        Set<String> marked = new HashSet<>()
        Map<String, Set<String>> refs = [:]
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue
            List<Path> files = Files.walk(dir).withCloseable { it.filter { Path p -> p.toString().endsWith('.class') }.toList() }
            for (Path file : files) {
                byte[] bytes
                try {
                    bytes = Files.readAllBytes(file)
                } catch (NoSuchFileException ignored) {
                    continue // removido durante a compilação; o próximo retrato pega
                }
                def crc = new CRC32()
                crc.update(bytes)
                def reader = new ClassReader(bytes)
                String name = reader.className.replace('/', '.')
                result.checksums[name] = crc.value
                result.bytecode[name] = bytes
                def collector = new Collector()
                reader.accept(new ClassRemapper(collector, collector.remapper), ClassReader.SKIP_FRAMES)
                if (collector.reloadable) marked << topLevel(name)
                refs.computeIfAbsent(topLevel(name)) { new HashSet<String>() }.addAll(collector.remapper.names)
            }
        }
        Set<String> tops = refs.keySet()
        tops.each { (it in marked ? result.reloadable : result.stable) << it }
        // fixa, até estabilizar, os recarregáveis alcançáveis a partir da camada estável
        Deque<String> pending = new ArrayDeque<>(result.stable)
        while (!pending.empty) {
            String from = pending.pop()
            for (String ref : refs[from]) {
                String top = topLevel(ref)
                if (top != from && result.reloadable.remove(top)) {
                    result.stable << top
                    result.pinned[top] = from
                    pending << top
                }
            }
        }
        result
    }

    /** {@code a.B$_closure1} → {@code a.B}: as internas acompanham a classe externa. */
    static String topLevel(String name) {
        int i = name.indexOf('$')
        i < 0 ? name : name.substring(0, i)
    }

    boolean isReloadable(String name) {
        reloadable.contains(topLevel(name))
    }

    /** Bytecode das classes (com as internas) cujas de nível superior estão em {@code tops}. */
    Map<String, byte[]> bytecodeOf(Set<String> tops) {
        bytecode.findAll { String n, byte[] b -> tops.contains(topLevel(n)) }
    }

    /** Classes (de nível superior) adicionadas, removidas ou com bytecode diferente de {@code before}. */
    Set<String> changedSince(AppClasses before) {
        Set<String> changed = new TreeSet<>()
        checksums.each { String n, Long crc -> if (before.checksums[n] != crc) changed << topLevel(n) }
        before.checksums.keySet().each { String n -> if (!checksums.containsKey(n)) changed << topLevel(n) }
        changed
    }

    /** Anotação {@link Reloadable} e os tipos referenciados (tudo que passa pelo remapper). */
    private static class Collector extends ClassVisitor {
        final NameCollector remapper = new NameCollector()
        boolean reloadable

        Collector() { super(Opcodes.ASM9) }

        @Override
        AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor == RELOADABLE_DESC) reloadable = true
            null
        }

        // visitors vazios (não null): sem eles o ClassRemapper não percorre o corpo dos métodos nem os campos,
        // e referências no código (ex.: XJob num doWithSpring) não fixariam o recarregável na camada estável
        @Override
        groovyjarjarasm.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            new groovyjarjarasm.asm.MethodVisitor(Opcodes.ASM9) {}
        }

        @Override
        groovyjarjarasm.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            new groovyjarjarasm.asm.FieldVisitor(Opcodes.ASM9) {}
        }
    }

    private static class NameCollector extends Remapper {
        final Set<String> names = new HashSet<>()

        NameCollector() { super(Opcodes.ASM9) }

        @Override
        String map(String internalName) {
            names << internalName.replace('/', '.')
            internalName
        }
    }
}
