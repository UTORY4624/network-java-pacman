package pacman.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import pacman.server.PacmanServer;

public class ConnectionTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        connectionResults();
        heartbeat();
        responseOrder();
        cancelWhileWaiting();
        invalidServer();
        System.out.println("PASS / 접속 화면 통신 테스트 " + checks + "개 통과");
    }

    private static void connectionResults() throws Exception {
        PacmanServer server = new PacmanServer("127.0.0.1", 0);
        Thread thread = new Thread(() -> {
            try {
                server.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
        int port = server.getPort();
        try (ServerConnection first = new ServerConnection();
             ServerConnection second = new ServerConnection();
             ServerConnection third = new ServerConnection()) {
            Result a = new Result();
            Result b = new Result();
            Result c = new Result();
            first.connect("127.0.0.1", port, a);
            second.connect("127.0.0.1", port, b);
            check(a.connected.await(3, TimeUnit.SECONDS), "첫 번째 접속 성공 알림");
            check(b.connected.await(3, TimeUnit.SECONDS), "두 번째 접속 성공 알림");
            third.connect("127.0.0.1", port, c);
            check(c.closed.await(3, TimeUnit.SECONDS), "정원 초과 연결 종료");
            check(c.connected.getCount() == 1 && c.message.contains("두 명"), "정원 초과를 성공으로 표시하지 않기");
            server.close();
            check(a.closed.await(3, TimeUnit.SECONDS) && b.closed.await(3, TimeUnit.SECONDS), "서버 종료 알림");
        } finally {
            server.close();
            thread.join(3000);
        }
        try (ServerConnection retry = new ServerConnection()) {
            Result result = new Result();
            retry.connect("127.0.0.1", port, result);
            check(result.closed.await(5, TimeUnit.SECONDS), "실행 중인 서버가 없으면 실패 안내");
            check(result.connected.getCount() == 1 && result.message.contains("서버"), "접속 실패 상태 확인");
        }
    }

    private static void heartbeat() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0);
             ServerConnection client = new ServerConnection()) {
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(13000);
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    writer.println("{ \"playerId\" : 1, \"type\" : \"WELCOME\" }");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    received.set(reader.readLine());
                    writer.println("{\"type\":\"PONG\"}");
                } catch (Exception e) {
                    received.set(e.toString());
                }
            });
            peer.start();
            Result result = new Result();
            client.connect("127.0.0.1", server.getLocalPort(), result);
            check(result.connected.await(3, TimeUnit.SECONDS), "접속 응답 확인 후 성공 알림");
            peer.join(14000);
            check("PING".equals(received.get()), "10초 주기로 접속 유지 메시지 보내기");
            check(result.closed.await(3, TimeUnit.SECONDS), "서버 EOF 처리");
        }
    }

    private static void cancelWhileWaiting() throws Exception {
        CountDownLatch accepted = new CountDownLatch(1);
        CountDownLatch ended = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0);
             ServerConnection client = new ServerConnection()) {
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    accepted.countDown();
                    if (socket.getInputStream().read() == -1) ended.countDown();
                } catch (Exception ignored) { }
            });
            peer.setDaemon(true);
            peer.start();
            Result result = new Result();
            client.connect("127.0.0.1", server.getLocalPort(), result);
            check(accepted.await(3, TimeUnit.SECONDS), "접속 응답 대기 시작");
            client.close();
            check(result.closed.await(3, TimeUnit.SECONDS) && ended.await(3, TimeUnit.SECONDS), "취소하면 소켓 정리");
            check(result.connected.getCount() == 1, "취소한 접속의 성공 알림 방지");
            peer.join(3000);
        }
    }

    private static void responseOrder() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             ServerConnection client = new ServerConnection()) {
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                            socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    writer.println("{ \"code\" : \"SERVER_FULL\", \"type\" : \"ERROR\" }");
                } catch (Exception ignored) { }
            });
            peer.start();
            Result result = new Result();
            client.connect("127.0.0.1", server.getLocalPort(), result);
            check(result.closed.await(3, TimeUnit.SECONDS), "필드 순서가 다른 정원 초과 응답");
            check(result.connected.getCount() == 1 && result.message.contains("두 명"), "오류 코드로 정원 초과 확인");
            peer.join(3000);
        }
    }

    private static void invalidServer() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             ServerConnection client = new ServerConnection()) {
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.getOutputStream().write("unrelated server\n".getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) { }
            });
            peer.start();
            Result result = new Result();
            client.connect("127.0.0.1", server.getLocalPort(), result);
            check(result.closed.await(3, TimeUnit.SECONDS), "다른 종류 서버에 접속한 경우 안내");
            check(result.connected.getCount() == 1 && result.message.contains("응답"), "잘못된 응답을 성공으로 표시하지 않기");
            peer.join(3000);
        }
    }

    private static class Result implements ServerConnection.Listener {
        final CountDownLatch connected = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        volatile String message;

        @Override
        public void connected() {
            connected.countDown();
        }

        @Override
        public void disconnected(String message) {
            this.message = message;
            closed.countDown();
        }
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
}
