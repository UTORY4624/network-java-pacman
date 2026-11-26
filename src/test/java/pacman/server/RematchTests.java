package pacman.server;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/** 실제 TCP 연결을 유지한 채 결과 1회 전송 -> 3초 -> 역할 교대 -> 재준비를 반복한다. */
public class RematchTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        PacmanServer server = new PacmanServer("127.0.0.1", 0);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try { server.run(); } catch (Throwable t) { failure.set(t); }
        });
        runner.start();
        try (Client first = new Client(server.getPort()); Client second = new Client(server.getPort())) {
            first.await("WELCOME"); second.await("WELCOME");
            first.send("ROLE PACMAN"); first.await("\"role\":\"PACMAN\"");
            second.send("ROLE GHOST"); second.await("\"role\":\"GHOST\"");
            first.send("READY"); first.await("\"id\":1,\"role\":\"PACMAN\",\"ready\":true");
            second.send("READY");
            first.await("GAME_START"); second.await("GAME_START");
            String initial = first.await("GAME_STATE");
            second.await("GAME_STATE");
            check(initial.contains("\"sequence\":1"), "첫 초기 상태");

            for (int round = 1; round <= 3; round++) {
                GameState ended;
                synchronized (server) {
                    ended = server.getGameState();
                    check(ended.roundId == round, "라운드 번호 증가");
                    ended.pacman.requestedDirection = ended.ghost.requestedDirection = null;
                    ended.pacman.x = 10; ended.pacman.y = 10;
                    ended.ghost.x = 40; ended.ghost.y = 40;
                    ended.powerRemainingMs = 0;
                    if (round == 1) {
                        // 마지막 먹이 + 목숨 소진 + 시간 초과.
                        for (boolean[] row : ended.pellets) Arrays.fill(row, false);
                        ended.pellets[11][11] = true;
                        ended.ghost.x = 11; ended.ghost.y = 10;
                        ended.lives = 1; ended.remainingMs = 1;
                    } else if (round == 2) {
                        for (boolean[] row : ended.pellets) Arrays.fill(row, false);
                        ended.pellets[11][11] = true;
                    } else {
                        ended.pellets[2][2] = true;
                        ended.remainingMs = 1;
                    }
                }
                String a = first.resultAfterFinalState();
                String b = second.resultAfterFinalState();
                String reason = round == 1 ? "NO_LIVES" : round == 2 ? "ALL_PELLETS" : "TIMEOUT";
                check(a.equals(b) && a.contains("\"reason\":\"" + reason + "\"")
                        && a.contains("\"roundId\":" + round), "양측 동일 결과");
                String frozen;
                synchronized (server) { frozen = Json.encode(ended.snapshot(ended.pacman.id)); }
                for (String cmd : new String[] {"MOVE LEFT", "READY", "ROLE PACMAN"}) {
                    first.send(cmd);
                    check(first.await("ERROR").contains("INVALID_PHASE"), "RESULT 중 요청 거절: " + cmd);
                }
                first.quiet(150); second.quiet(150);
                synchronized (server) {
                    check(frozen.equals(Json.encode(ended.snapshot(ended.pacman.id))), "결과 대기 중 상태와 sequence 고정");
                }
                String lobbyA = first.await("LOBBY");
                String lobbyB = second.await("LOBBY");
                check(lobbyA.equals(lobbyB), "양측 동일 교대 대기실");
                String firstRole = round % 2 == 1 ? "GHOST" : "PACMAN";
                String secondRole = round % 2 == 1 ? "PACMAN" : "GHOST";
                check(lobbyA.contains("\"id\":1,\"role\":\"" + firstRole + "\",\"ready\":false")
                        && lobbyA.contains("\"id\":2,\"role\":\"" + secondRole + "\",\"ready\":false"),
                        "역할 교대 및 준비 초기화");
                check(server.getGameState() == null, "재준비 전에 상태 생성 안 함");
                first.send("MOVE LEFT");
                check(first.await("ERROR").contains("INVALID_PHASE"), "대기 중 이전 라운드 이동 거절");
                first.send("READY");
                first.await("\"id\":1,\"role\":\"" + firstRole + "\",\"ready\":true");
                first.quiet(150);
                check(server.getGameState() == null, "한 명만 준비하면 재시작 안 함");
                second.send("READY");
                first.await("GAME_START"); second.await("GAME_START");
                String fresh = first.await("GAME_STATE"); second.await("GAME_STATE");
                check(fresh.contains("\"sequence\":1") && fresh.contains("\"remainingMs\":180000"),
                        "새 라운드 sequence와 시간 초기화");
                synchronized (server) {
                    GameState next = server.getGameState();
                    check(next != ended && next.result() == null && next.lives == 2
                            && next.powerRemainingMs == 0 && "PLAYING".equals(next.phase), "결과·목숨·효과 초기화");
                    check(next.pacman.id == (round % 2 == 1 ? 2 : 1), "교대한 역할로 새 상태 생성");
                    check(next.pacman.x == next.map.pacmanX && next.pacman.y == next.map.pacmanY
                            && next.ghost.x == next.map.ghostX && next.ghost.y == next.map.ghostY, "양측 위치 초기화");
                    check(next.pacman.requestedDirection == null && next.ghost.requestedDirection == null
                            && next.pacman.waitRemainingMs == 0 && next.ghost.waitRemainingMs == 0
                            && next.pacman.movementCredit == 0 && next.ghost.movementCredit == 0,
                            "이전 입력·대기·이동 적립 제거");
                    check("ACTIVE".equals(next.pacman.status) && "ACTIVE".equals(next.ghost.status)
                            && count(next.pellets) == 240 && count(next.powerItems) == 4, "상태·먹이·아이템 복원");
                    check(next.snapshot(next.pacman.id).get("opponent") == null, "초기 탐지 상태 복원");
                }
            }
            // RESULT 중 상대 종료는 3초 뒤 교대 작업을 취소해야 한다.
            synchronized (server) { server.getGameState().remainingMs = 1; }
            first.resultAfterFinalState(); second.resultAfterFinalState();
            second.send("QUIT");
            first.await("PLAYER_LEFT"); first.await("LOBBY");
            check(server.getGameState() == null, "결과 대기 중 퇴장 정리");
            first.quiet(3200);
            first.send("PING");
            check(first.await("PONG").contains("PONG"), "취소된 교대 타이머가 연결에 영향 없음");
        } finally {
            server.close();
            runner.join(3000);
        }
        check(!runner.isAlive() && failure.get() == null, "서버 종료");
        System.out.println("PASS / 실제 TCP 재대결 " + checks + "개 통과");
    }

    private static int count(boolean[][] values) {
        int n = 0;
        for (boolean[] row : values) for (boolean value : row) if (value) n++;
        return n;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }

    private static final class Client implements AutoCloseable {
        final Socket socket;
        final BufferedReader reader;
        final BufferedWriter writer;

        Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(6000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void send(String command) throws IOException {
            writer.write(command + "\n");
            writer.flush();
        }

        String await(String expected) throws IOException {
            long deadline = System.nanoTime() + 6_000_000_000L;
            while (System.nanoTime() < deadline) {
                String message = reader.readLine();
                if (message == null) throw new AssertionError("연결 종료: " + expected);
                if (message.contains("ROUND_RESULT")) throw new AssertionError("결과 중복/예상 외 전송: " + message);
                if (message.contains(expected)) return message;
            }
            throw new AssertionError("응답 시간 초과: " + expected);
        }

        String resultAfterFinalState() throws IOException {
            boolean finalState = false;
            long deadline = System.nanoTime() + 6_000_000_000L;
            while (System.nanoTime() < deadline) {
                String message = reader.readLine();
                if (message == null) throw new AssertionError("결과 전 연결 종료");
                if (message.contains("GAME_STATE") && message.contains("\"phase\":\"RESULT\"")) {
                    finalState = true;
                }
                if (message.contains("ROUND_RESULT")) {
                    check(finalState, "최종 GAME_STATE 이후 결과 전송");
                    return message;
                }
                if (message.contains("LOBBY")) throw new AssertionError("결과보다 앞선 대기실");
            }
            throw new AssertionError("결과 시간 초과");
        }

        void quiet(int ms) throws IOException {
            socket.setSoTimeout(ms);
            try {
                String message = reader.readLine();
                throw new AssertionError("예상하지 않은 메시지: " + message);
            } catch (SocketTimeoutException expected) {
                // 결과/시작/교대 메시지가 추가로 오지 않아야 한다.
            } finally { socket.setSoTimeout(6000); }
        }

        public void close() throws IOException { socket.close(); }
    }
}
