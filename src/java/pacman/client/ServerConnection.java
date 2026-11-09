package pacman.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerConnection implements AutoCloseable {
    public interface Listener {
        void connected();
        void disconnected(String message);
    }

    private final Socket socket = new Socket();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "client-ping");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    public void connect(String host, int port, Listener listener) {
        // 접속을 기다리는 동안에도 버튼이 눌리게 별도 스레드로 실행
        Thread thread = new Thread(() -> receive(host, port, listener), "client-connection");
        thread.setDaemon(true);
        thread.start();
    }

    private void receive(String host, int port, Listener listener) {
        String message = "서버와 연결이 종료됐어요.";
        try {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(15000);
            socket.setTcpNoDelay(true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));

            // 서버에서 접속을 허용했다는 응답을 받아야 성공으로 표시
            String firstMessage = reader.readLine();
            if (firstMessage != null && firstMessage.contains("\"code\":\"SERVER_FULL\"")) {
                message = "이미 두 명이 접속 중이에요. 잠시 후 다시 시도해주세요.";
                return;
            }
            if (firstMessage == null || !firstMessage.startsWith("{\"type\":\"WELCOME\",")) {
                message = "팩맨 서버의 접속 응답을 확인할 수 없어요.";
                return;
            }

            synchronized (this) {
                if (closed) return;
                // 아무 버튼도 안 눌러도 접속이 유지되도록 10초마다 연락 보내기
                heartbeat.scheduleAtFixedRate(() -> ping(writer), 10, 10, TimeUnit.SECONDS);
            }
            listener.connected();

            while (!closed && reader.readLine() != null) {
                // 지금은 접속 유지와 서버 종료 여부만 확인
            }
        } catch (UnknownHostException e) {
            message = "서버 주소를 찾을 수 없어요. 주소를 확인해주세요.";
        } catch (ConnectException e) {
            message = "접속하지 못했어요. 서버 실행 여부와 포트를 확인해주세요.";
        } catch (SocketTimeoutException e) {
            message = "서버 응답 시간이 초과됐어요. 다시 접속해주세요.";
        } catch (IOException e) {
            message = "연결이 끊겼어요. 서버와 네트워크를 확인해주세요.";
        } finally {
            close();
            listener.disconnected(message);
        }
    }

    private void ping(BufferedWriter writer) {
        try {
            writer.write("PING\n");
            writer.flush();
        } catch (IOException e) {
            close();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        heartbeat.shutdownNow();
        try {
            socket.close();
        } catch (IOException ignored) {
            // 이미 연결이 끊긴 경우에도 종료하기
        }
    }
}
