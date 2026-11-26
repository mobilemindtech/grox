package org.grox.assets

import java.security.MessageDigest

/**
 * Compila os assets no build (tarefa {@code assetCompile} do plugin {@code org.grox.app}):
 * processa JS/CSS (diretivas), gera {@code nome-<md5>.ext} e {@code nome.ext} em
 * {@value #OUTPUT} e o {@code manifest.properties} (lógico → com digest).
 *
 * Argumentos: diretório {@code grails-app/assets}, diretório de saída.
 */
class AssetCompiler {

    static final String OUTPUT = 'META-INF/assets'
    static final String MANIFEST = OUTPUT + '/manifest.properties'

    static void main(String[] args) {
        if (args.length < 2) {
            System.err.println('uso: AssetCompiler <assetsDir> <outputDir>')
            System.exit(2)
        }
        try {
            int n = compile(new File(args[0]), new File(args[1]))
            println "assets compilados: ${n}"
        } catch (AssetException e) {
            System.err.println("erro de asset: ${e.message}")
            System.exit(1)
        }
    }

    /** @return número de assets */
    static int compile(File assetsDir, File outputDir) {
        def processor = new AssetProcessor(assetsDir)
        File out = new File(outputDir, OUTPUT)
        out.deleteDir()
        out.mkdirs()
        Map<String, String> manifest = new TreeMap<>()
        List<String> all = processor.allLogicalPaths()
        // 1) binários (imagens, fontes) primeiro: o CSS reescreve url() para os nomes com digest
        // 2) JS; 3) CSS
        List<String> ordered = all.findAll { !(AssetProcessor.extension(it) in AssetProcessor.TEXT) } +
                all.findAll { AssetProcessor.extension(it) == 'js' } + all.findAll { AssetProcessor.extension(it) == 'css' }
        String prefix = '/assets/'
        for (String logical : ordered) {
            def result = processor.process(logical) { String ref -> prefix + (manifest[ref] ?: ref) }
            String digested = digestedName(logical, result.content)
            write(new File(out, logical), result.content)
            write(new File(out, digested), result.content)
            manifest[logical] = digested
        }
        def props = new Properties()
        manifest.each { k, v -> props.setProperty(k, v) }
        new File(outputDir, MANIFEST).withWriter('UTF-8') { props.store(it, 'Grox assets') }
        manifest.size()
    }

    static String digestedName(String logical, byte[] content) {
        String hex = MessageDigest.getInstance('MD5').digest(content).encodeHex().toString()
        int dot = logical.lastIndexOf('.')
        int slash = logical.lastIndexOf('/')
        dot > slash ? "${logical.substring(0, dot)}-${hex}${logical.substring(dot)}" : "${logical}-${hex}"
    }

    private static void write(File f, byte[] content) {
        f.parentFile.mkdirs()
        f.bytes = content
    }
}
