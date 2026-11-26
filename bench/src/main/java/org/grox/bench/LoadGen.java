package org.grox.bench;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * Gerador de carga HTTP/1.1 minimalista (estilo wrk): N conexões keep-alive, cada uma numa
 * virtual thread com socket bloqueante, uma requisição por vez. Latência em histograma de
 * 10 µs a 10 s.
 *
 * <pre>java LoadGen URL [conexões=64] [segundos=10] [aquecimento=3] [cabeçalho: valor]...</pre>
 */
public final class LoadGen {

    static final int BUCKETS = 2000;
    final AtomicLongArray histogram = new AtomicLongArray(BUCKETS);
    final LongAdder requests = new LongAdder();
    final LongAdder errors = new LongAdder();
    final LongAdder bytes = new LongAdder();
    final LongAdder non2xx = new LongAdder();
    volatile boolean recording;
    final AtomicBoolean running = new AtomicBoolean(true);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("uso: LoadGen URL [conexões] [segundos] [aquecimento] [Cabeçalho: valor]...");
            System.exit(2);
        }
        URI uri = URI.create(args[0]);
        int connections = args.length > 1 ? Integer.parseInt(args[1]) : 64;
        int seconds = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        int warmup = args.length > 3 ? Integer.parseInt(args[3]) : 3;
        StringBuilder extra = new StringBuilder();
        for (int i = 4; i < args.length; i++) extra.append(args[i]).append("\r\n");
        new LoadGen().run(uri, connections, seconds, warmup, extra.toString());
    }

    /** Bucket logarítmico: 10 µs … 10 s. */
    static int bucket(long nanos) {
        double us = Math.max(10, nanos / 1000.0);
        int b = (int) (Math.log(us / 10.0) / Math.log(1e6) * BUCKETS);
        return Math.min(BUCKETS - 1, Math.max(0, b));
    }

    static double bucketMs(int b) {
        return 10.0 * Math.pow(1e6, (b + 0.5) / BUCKETS) / 1000.0;
    }

    void run(URI uri, int connections, int seconds, int warmup, String headers) throws Exception {
        String path = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
        byte[] request = ("GET " + (path.isEmpty() ? "/" : path) + " HTTP/1.1\r\nHost: " + uri.getHost() + "\r\n" + headers + "\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < connections; i++) {
            threads.add(Thread.ofVirtual().start(() -> worker(uri.getHost(), uri.getPort(), request)));
        }
        Thread.sleep(warmup * 1000L);
        requests.reset(); errors.reset(); bytes.reset(); non2xx.reset();
        for (int i = 0; i < BUCKETS; i++) histogram.set(i, 0);
        recording = true;
        long start = System.nanoTime();
        Thread.sleep(seconds * 1000L);
        recording = false;
        double elapsed = (System.nanoTime() - start) / 1e9;
        running.set(false);
        for (Thread t : threads) t.join(2000);
        report(uri, connections, elapsed);
    }

    void worker(String host, int port, byte[] request) {
        while (running.get()) {
            try (Socket s = new Socket(host, port)) {
                s.setTcpNoDelay(true);
                OutputStream out = s.getOutputStream();
                InputStream in = new BufferedInputStream(s.getInputStream(), 65536);
                while (running.get()) {
                    long t0 = System.nanoTime();
                    out.write(request);
                    out.flush();
                    int status = readResponse(in);
                    long dt = System.nanoTime() - t0;
                    if (status < 0) throw new IOException("conexão fechada");
                    if (recording) {
                        requests.increment();
                        histogram.incrementAndGet(bucket(dt));
                        if (status < 200 || status >= 300) non2xx.increment();
                    }
                }
            } catch (IOException e) {
                if (recording) errors.increment();
            }
        }
    }

    /** Lê status, cabeçalhos e corpo (Content-Length ou Transfer-Encoding: chunked); devolve o status ou -1. */
    int readResponse(InputStream in) throws IOException {
        String statusLine = readLine(in);
        if (statusLine == null) return -1;
        int status = Integer.parseInt(statusLine.substring(9, 12));
        long length = 0;
        boolean close = false;
        boolean chunked = false;
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int c = line.indexOf(':');
            if (c < 0) continue;
            String name = line.substring(0, c).trim();
            String value = line.substring(c + 1).trim();
            if (name.equalsIgnoreCase("content-length")) length = Long.parseLong(value);
            else if (name.equalsIgnoreCase("connection") && value.equalsIgnoreCase("close")) close = true;
            else if (name.equalsIgnoreCase("transfer-encoding") && value.toLowerCase().contains("chunked")) chunked = true;
        }
        if (chunked) {
            // tamanho em hexadecimal (com extensões opcionais após ';'), dados, CRLF; termina em 0 e trailers
            length = 0;
            while (true) {
                String sizeLine = readLine(in);
                if (sizeLine == null) return -1;
                int semi = sizeLine.indexOf(';');
                long size = Long.parseLong((semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim(), 16);
                if (size == 0) {
                    String trailer;
                    while ((trailer = readLine(in)) != null && !trailer.isEmpty()) { }
                    break;
                }
                if (!skip(in, size)) return -1;
                if (readLine(in) == null) return -1;   // CRLF depois dos dados
                length += size;
            }
        } else if (!skip(in, length)) {
            return -1;
        }
        if (recording) bytes.add(length);
        if (close) throw new IOException("servidor pediu Connection: close");
        return status;
    }

    static boolean skip(InputStream in, long length) throws IOException {
        long skipped = 0;
        while (skipped < length) {
            long n = in.skip(length - skipped);
            if (n <= 0) {
                if (in.read() < 0) return false;
                n = 1;
            }
            skipped += n;
        }
        return true;
    }

    static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(64);
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') {
                int len = sb.length();
                if (len > 0 && sb.charAt(len - 1) == '\r') sb.setLength(len - 1);
                return sb.toString();
            }
            sb.append((char) c);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    double percentile(double p) {
        long total = 0;
        for (int i = 0; i < BUCKETS; i++) total += histogram.get(i);
        long target = (long) Math.ceil(total * p);
        long seen = 0;
        for (int i = 0; i < BUCKETS; i++) {
            seen += histogram.get(i);
            if (seen >= target && total > 0) return bucketMs(i);
        }
        return 0;
    }

    void report(URI uri, int connections, double elapsed) {
        long n = requests.sum();
        System.out.printf("%s  conexões=%d  %.1fs%n", uri, connections, elapsed);
        System.out.printf("  req/s: %.0f   requisições: %d   erros: %d   não-2xx: %d   MB/s: %.1f%n",
                n / elapsed, n, errors.sum(), non2xx.sum(), bytes.sum() / elapsed / 1e6);
        System.out.printf("  latência ms  p50: %.2f  p90: %.2f  p99: %.2f  p99.9: %.2f%n",
                percentile(0.5), percentile(0.9), percentile(0.99), percentile(0.999));
    }
}
