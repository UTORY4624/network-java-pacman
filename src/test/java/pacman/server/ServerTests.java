package pacman.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class ServerTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        // 같은 PC에서 두 명이 들어오는 상황으로 확인
        PacmanServer server = new PacmanServer("127.0.0.1", 0);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread serverThread = new Thread(() -> {
            try {
                server.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        serverThread.start();

        try (TestClient first = new TestClient(server.getPort());
             TestClient second = new TestClient(server.getPort())) {
            check(first.await("WELCOME").contains("\"playerId\":1"), "첫 번째 접속");
            check(second.await("WELCOME").contains("\"playerId\":2"), "두 번째 접속");
            first.await("\"id\":2");
            second.await("\"id\":2");

            try (TestClient third = new TestClient(server.getPort())) {
                check(third.await("SERVER_FULL").contains("ERROR"), "세 번째 접속 거절");
                check(third.reader.readLine() == null, "세 번째 연결 종료");
            }

            first.send("ROLE PACMAN");
            check(first.await("\"role\":\"PACMAN\"").contains("\"id\":1"), "팩맨 선택");
            second.send("ROLE PACMAN");
            check(second.await("ROLE_TAKEN").contains("ERROR"), "같은 역할 중복 방지");
            second.send("ROLE GHOST");
            String lobby = second.await("\"role\":\"GHOST\"");
            check(lobby.contains("\"id\":1,\"role\":\"PACMAN\"")
                    && lobby.contains("\"id\":2,\"role\":\"GHOST\""), "양쪽 역할 공유");

            first.send("ROLE GHOST");
            check(first.await("ROLE_TAKEN").contains("ERROR"), "상대 역할로 변경 막기");
            first.send("ROLE UNKNOWN");
            check(first.await("INVALID_ROLE").contains("ERROR"), "잘못된 역할 처리");
            first.send("ROLE");
            check(first.await("BAD_COMMAND").contains("ERROR"), "역할 없이 보낸 요청 처리");
            first.send("READY 1");
            check(first.await("BAD_COMMAND").contains("ERROR"), "현재 범위 밖 명령 처리");
            first.send("MOVE 1 RIGHT");
            check(first.await("BAD_COMMAND").contains("ERROR"), "이동 명령 처리 범위 확인");

            // 메시지가 나뉘어 오거나 한 번에 여러 개 와도 줄 단위로 받는지 확인
            first.sendRaw("PI");
            first.sendRaw("NG\nPING\n");
            check(first.await("PONG").contains("PONG"), "나뉘어 도착한 메시지");
            check(first.await("PONG").contains("PONG"), "연속으로 도착한 메시지");

            second.send("QUIT");
            check(first.await("PLAYER_LEFT").contains("\"playerId\":2"), "상대 종료 알림");
            check(!first.await("LOBBY").contains("\"id\":2"), "종료한 사람 목록에서 제외");
            first.send("ROLE GHOST");
            check(first.await("LOBBY").contains("\"role\":\"GHOST\""), "비어 있는 역할로 변경");

            try (TestClient replacement = new TestClient(server.getPort())) {
                check(replacement.await("WELCOME").contains("\"playerId\":3"), "빈자리 재접속");
                replacement.send("ROLE PACMAN");
                check(replacement.await("\"role\":\"PACMAN\"").contains("\"id\":3"), "변경 후 비워진 역할 선택");
            }
            check(first.await("PLAYER_LEFT").contains("\"playerId\":3"), "창을 닫은 경우도 정리");
            first.await("LOBBY");

            try (TestClient longMessage = new TestClient(server.getPort())) {
                longMessage.await("WELCOME");
                char[] text = new char[257];
                Arrays.fill(text, 'X');
                longMessage.send(new String(text));
                check(first.await("PLAYER_LEFT").contains("\"playerId\":4"), "너무 긴 메시지 정리");
            }
            first.send("PING");
            check(first.await("PONG").contains("PONG"), "상대가 나간 뒤에도 서버 응답");

            try (TestClient windowsClient = new TestClient(server.getPort())) {
                windowsClient.await("WELCOME");
                char[] command = new char[256];
                Arrays.fill(command, ' ');
                command[0] = 'P';
                command[1] = 'I';
                command[2] = 'N';
                command[3] = 'G';
                windowsClient.sendRaw(new String(command) + "\r\n");
                check(windowsClient.await("PONG").contains("PONG"), "256자 CRLF 명령 처리");
            }
        } finally {
            server.close();
            serverThread.join(3000);
        }
        check(!serverThread.isAlive() && failure.get() == null, "서버 정상 종료");
        System.out.println("PASS / 접속·역할 선택 테스트 " + checks + "개 통과");
    }

    private static class TestClient implements AutoCloseable {
        final Socket socket;
        final BufferedReader reader;
        final BufferedWriter writer;

        TestClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(3000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void send(String message) throws IOException {
            sendRaw(message + "\n");
        }

        void sendRaw(String message) throws IOException {
            writer.write(message);
            writer.flush();
        }

        String await(String expected) throws IOException {
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < deadline) {
                String message = reader.readLine();
                if (message == null) throw new AssertionError("응답 전에 연결 종료 / " + expected);
                if (message.contains(expected)) return message;
            }
            throw new AssertionError("응답 시간 초과 / " + expected);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        checks++;
    }
}
