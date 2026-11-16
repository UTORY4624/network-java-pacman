package pacman.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

final class ClientSession {
    final long id;
    String role;
    private final Socket socket;
    private final PacmanServer server;
    private final BlockingQueue<String> outgoing = new ArrayBlockingQueue<>(64);
    private final AtomicBoolean closed = new AtomicBoolean();
    private Thread writerThread;

    ClientSession(long id, Socket socket, PacmanServer server) {
        this.id = id;
        this.socket = socket;
        this.server = server;
    }

    void start() {
        // 받는 쪽이랑 보내는 쪽을 나눠서 한쪽이 기다려도 다른 쪽은 동작하게 하기
        writerThread = new Thread(this::writeLoop, "client-writer-" + id);
        writerThread.setDaemon(true);
        writerThread.start();
        Thread readerThread = new Thread(this::readLoop, "client-reader-" + id);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    void send(Object message) {
        // 상대가 계속 못 받으면 연결을 정리하기
        if (!closed.get() && !outgoing.offer(Json.encode(message))) close();
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8))) {
            long rateWindow = System.nanoTime();
            int count = 0;
            String line;
            while ((line = readLine(reader)) != null) {
                if (System.nanoTime() - rateWindow >= 1_000_000_000L) {
                    rateWindow = System.nanoTime(); count = 0;
                }
                if (++count > 120) throw new IOException("Too many commands");
                server.command(this, line);
                if (closed.get()) break;
            }
        } catch (IOException ignored) {
            // 창을 닫거나 30초 동안 연락이 없으면 아래에서 연결 정리
        } finally { close(); }
    }

    private static String readLine(BufferedReader reader) throws IOException {
        // 줄바꿈까지가 메시지 하나. 너무 길게 보내는 경우도 여기서 확인
        StringBuilder line = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            if (ch == '\n') return line.toString();
            if (ch == '\r') {
                // Windows에서 보내는 CRLF의 CR은 메시지 길이에 넣지 않기
                reader.mark(1);
                if (reader.read() == '\n') return line.toString();
                reader.reset();
            }
            if (line.length() >= 256) throw new IOException("Command exceeds 256 characters");
            line.append((char) ch);
        }
        return null;
    }

    private void writeLoop() {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8))) {
            while (!closed.get()) {
                writer.write(outgoing.take());
                writer.newLine();
                writer.flush();
            }
        } catch (IOException | InterruptedException ignored) {
            // 연결이 끊겼거나 서버를 종료한 경우
        } finally { close(); }
    }

    void close() {
        // 수신과 송신에서 동시에 오류가 나도 종료 처리는 한 번만 하기
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) { }
        if (writerThread != null) writerThread.interrupt();
        server.disconnected(this);
    }
}
