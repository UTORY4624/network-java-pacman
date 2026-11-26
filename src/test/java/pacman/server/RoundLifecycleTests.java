package pacman.server;

import java.io.StringReader;

public class RoundLifecycleTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        // 같은 1ms 안에 마지막 먹이·접촉·시간 초과가 겹치는 경계를 실제 advance로 검사한다.
        GameState s = atLastPellet();
        s.advance(1000);
        outcome(s, "PACMAN", "ALL_PELLETS");
        check(s.remainingMs == 179999 && s.pacman.x == 1, "승리 후 남은 갱신 중지");

        s = atLastPellet(); s.remainingMs = 1;
        s.advance(1);
        outcome(s, "PACMAN", "ALL_PELLETS");

        s = atLastPellet(); s.lives = 1; s.ghost.x = 1;
        s.advance(1);
        outcome(s, "GHOST", "NO_LIVES");
        check(!s.pellets[0][1], "치명적 접촉과 마지막 먹이 처리 반영");

        s = atLastPellet(); s.lives = 1; s.ghost.x = 1; s.remainingMs = 1;
        s.advance(1);
        outcome(s, "GHOST", "NO_LIVES");
        check(s.remainingMs == 0, "세 조건 동시 발생");

        s = atLastPellet(); s.ghost.x = 1;
        s.advance(1);
        outcome(s, "PACMAN", "ALL_PELLETS");
        check(s.lives == 1 && s.pacman.waitRemainingMs == 0,
                "마지막 먹이와 비치명적 접촉은 승리하고 재배치 대기 종료");

        s = atLastPellet(); s.lives = 1; s.ghost.x = 1;
        s.powerItems[0][1] = true;
        s.advance(1);
        outcome(s, "PACMAN", "ALL_PELLETS");
        check(s.lives == 1, "접촉 판정 전에 파워 획득 적용");

        s = arena(); s.remainingMs = 1;
        s.advance(100);
        outcome(s, "GHOST", "TIMEOUT");
        check(s.remainingMs == 0 && s.pellets[0][1], "먹이를 남기고 시간 초과");

        s = arena(); s.lives = 1; s.ghost.x = 0;
        s.advance(1);
        outcome(s, "GHOST", "NO_LIVES");

        s = arena();
        s.advance(1);
        check(s.result() == null && "PLAYING".equals(s.phase), "조건 미충족 시 계속 진행");
        System.out.println("PASS / 라운드 종료 경계 " + checks + " checks");
    }

    private static GameState arena() throws Exception {
        return new GameState(GameMap.read(new StringReader("P;.;o;b\n")), 1, 1, 2, 1);
    }

    private static GameState atLastPellet() throws Exception {
        GameState s = arena();
        s.move(1, "RIGHT");
        s.pacman.movementCredit = 3980;
        return s;
    }

    private static void outcome(GameState s, String winner, String reason) {
        check(s.result() != null && winner.equals(s.result().winner)
                && reason.equals(s.result().reason) && "RESULT".equals(s.phase), reason);
        RoundResult original = s.result();
        String before = Json.encode(s.snapshot(1));
        check(!s.move(1, "LEFT") && !s.move(2, "RIGHT"), "종료 후 입력 거절");
        check(s.pacman.requestedDirection == null && s.ghost.requestedDirection == null
                && s.pacman.movementCredit == 0 && s.ghost.movementCredit == 0,
                "이전 라운드 입력과 이동 적립 제거");
        check(!s.advance(10000) && before.equals(Json.encode(s.snapshot(1)))
                && original == s.result(), "종료 상태와 결과 고정");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
}
