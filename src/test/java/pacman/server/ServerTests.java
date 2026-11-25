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

            first.send("READY");
            check(first.await("INVALID_PHASE").contains("ERROR"), "역할 선택 전 준비 막기");
            first.send("MOVE LEFT");
            check(first.await("INVALID_PHASE").contains("ERROR"), "대기 중 이동 거절");
            first.send("MOVE DIAGONAL");
            check(first.await("BAD_COMMAND").contains("ERROR"), "잘못된 이동 방향 거절");
            first.send("ROLE PACMAN");
            check(first.await("\"role\":\"PACMAN\"").contains("\"id\":1"), "팩맨 선택");
            second.send("ROLE PACMAN");
            check(second.await("ROLE_TAKEN").contains("ERROR"), "같은 역할 중복 방지");
            second.send("ROLE GHOST");
            String lobby = second.await("\"role\":\"GHOST\"");
            check(lobby.contains("\"id\":1,\"role\":\"PACMAN\",\"ready\":false")
                    && lobby.contains("\"id\":2,\"role\":\"GHOST\",\"ready\":false"), "양쪽 역할 공유");
            first.send("ROLE UNKNOWN");
            check(first.await("INVALID_ROLE").contains("ERROR"), "잘못된 역할 처리");
            first.send("ROLE");
            check(first.await("BAD_COMMAND").contains("ERROR"), "역할 없이 보낸 요청 처리");

            first.send("READY");
            check(first.await("\"ready\":true").contains("\"id\":1"), "첫 번째 준비 상태 공유");
            first.expectNoMessage();
            check(server.getGameState() == null, "한 명만 준비하면 게임 상태 생성 안 함");
            second.send("READY");
            String firstStart = first.await("GAME_START");
            check(firstStart.contains("\"roundId\":1") && firstStart.contains("\"role\":\"PACMAN\"")
                    && firstStart.contains("\"role\":\"GHOST\""), "두 명 준비 후 첫 번째 시작 안내");
            check(second.await("GAME_START").contains("\"roundId\":1"), "두 명 준비 후 두 번째 시작 안내");
            String initial = first.reader.readLine();
            check(initial.contains("GAME_STATE") && initial.contains("\"sequence\":1")
                    && initial.contains("\"remainingMs\":180000"), "시작 직후 초기 상태 전송");
            check(second.reader.readLine().contains("\"self\":{\"id\":2"), "두 번째 사용자 전용 상태");
            String periodic = first.reader.readLine();
            check(periodic.contains("GAME_STATE") && periodic.contains("\"sequence\":2"), "주기적 상태 전송, 시작 중복 없음");
            first.send("ROLE GHOST");
            synchronized (server) {
                GameState state = server.getGameState();
                check(state != null && state.roundId == 1 && state.pacman.id == 1 && state.ghost.id == 2,
                        "게임 시작 전에 서버 상태 생성");
                check(state.remainingMs < 180000 && state.remainingMs > 175000, "서버 제한 시간 진행");
            }
            check(first.await("INVALID_PHASE").contains("ERROR"), "진행 중 역할 변경 막기");
            first.send("READY 1");
            check(first.await("BAD_COMMAND").contains("ERROR"), "준비 명령 인자 처리");
            first.send("MOVE 1 RIGHT");
            check(first.await("BAD_COMMAND").contains("ERROR"), "이동 명령 처리 범위 확인");

            synchronized (server) {
                server.getGameState().pacman.x = 0;
                server.getGameState().pacman.y = 1;
            }
            first.sendRaw("MOVE RI");
            first.sendRaw("GHT\nPING\n");
            first.await("PONG");
            second.sendRaw("MOVE LEFT\nPING\n");
            second.await("PONG");
            synchronized (server) {
                check("RIGHT".equals(server.getGameState().pacman.requestedDirection)
                        && "LEFT".equals(server.getGameState().ghost.requestedDirection), "두 연결의 입력을 본인 역할에 적용");
            }
            String moved = first.await("\"self\":{\"id\":1,\"role\":\"PACMAN\",\"x\":1,\"y\":1");
            check(!moved.contains("[2,2]"), "실제 TCP 이동 후 먹이 제거 전송");
            String otherView = second.await("\"opponent\":{\"id\":1,\"role\":\"PACMAN\",\"x\":1,\"y\":1");
            check(!otherView.contains("[2,2]"), "상대에게도 같은 위치와 먹이 전송");
            synchronized (server) {
                server.getGameState().pacman.requestedDirection = null;
                server.getGameState().ghost.requestedDirection = null;
            }

            // 메시지가 나뉘어 오거나 한 번에 여러 개 와도 줄 단위로 받는지 확인
            first.sendRaw("PI");
            first.sendRaw("NG\nPING\n");
            check(first.await("PONG").contains("PONG"), "나뉘어 도착한 메시지");
            check(first.await("PONG").contains("PONG"), "연속으로 도착한 메시지");

            second.send("QUIT");
            check(first.await("PLAYER_LEFT").contains("\"playerId\":2"), "상대 종료 알림");
            String afterLeave = first.await("LOBBY");
            first.expectNoMessage();
            check(server.getGameState() == null, "상대 종료 후 게임 상태 제거");
            check(!afterLeave.contains("\"id\":2") && afterLeave.contains("\"ready\":false"),
                    "상대 종료 후 준비 상태 초기화");
            first.send("ROLE GHOST");
            check(first.await("LOBBY").contains("\"role\":\"GHOST\""), "비어 있는 역할로 변경");

            try (TestClient replacement = new TestClient(server.getPort())) {
                check(replacement.await("WELCOME").contains("\"playerId\":3"), "빈자리 재접속");
                replacement.send("ROLE PACMAN");
                check(replacement.await("\"role\":\"PACMAN\"").contains("\"id\":3"), "변경 후 비워진 역할 선택");
                first.send("READY");
                first.await("\"id\":1,\"role\":\"GHOST\",\"ready\":true");
                replacement.send("READY");
                check(replacement.await("GAME_START").contains("\"roundId\":2"), "새 상대와 두 번째 판 시작");
                first.await("GAME_START");
                synchronized (server) {
                    GameState state = server.getGameState();
                    check(state.pacman.id == 3 && state.ghost.id == 1 && state.lives == 2
                            && state.remainingMs > 175000 && state.pacman.x == state.map.pacmanX,
                            "새 역할로 초기 상태 생성");
                }
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
        check(server.getGameState() == null, "서버 종료 후 상태 제거");
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

        void expectNoMessage() throws IOException {
            socket.setSoTimeout(250);
            try {
                String message = reader.readLine();
                if (message != null) throw new AssertionError("아직 시작하면 안 되는 메시지 / " + message);
            } catch (java.net.SocketTimeoutException expected) {
                // 한 명만 준비한 상태에서는 시작 메시지가 오지 않는 것이 정상
            } finally {
                socket.setSoTimeout(3000);
            }
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
