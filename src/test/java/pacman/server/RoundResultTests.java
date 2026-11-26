package pacman.server;

import java.io.StringReader;
import java.util.Map;

/** #18 판정 우선순위 검사. 실제 게임 루프/소켓 연동 검사는 후속 작업이다. */
public class RoundResultTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(RoundResult.evaluate(2, 1, 1) == null, "조건 미충족이면 계속 진행");
        expect(2, 0, 1, "PACMAN", "ALL_PELLETS");
        expect(0, 1, 1, "GHOST", "NO_LIVES");
        expect(2, 1, 0, "GHOST", "TIMEOUT");
        expect(0, 0, 1, "GHOST", "NO_LIVES");
        expect(2, 0, 0, "PACMAN", "ALL_PELLETS");
        expect(0, 1, 0, "GHOST", "NO_LIVES");
        expect(0, 0, 0, "GHOST", "NO_LIVES");
        expect(-1, 1, 100, "GHOST", "NO_LIVES");
        expect(1, 1, -1, "GHOST", "TIMEOUT");
        boolean rejected = false;
        try { RoundResult.evaluate(2, -1, 100); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "잘못된 먹이 개수 거절");

        GameState state = new GameState(
                GameMap.read(new StringReader("P;.;o;b\n")), 7, 11, 22, 1);
        check(RoundResult.evaluate(state) == null, "일반 먹이가 남아 있으면 계속 진행");
        state.pellets[0][1] = false;
        RoundResult result = RoundResult.evaluate(state);
        check(result != null && "ALL_PELLETS".equals(result.reason),
                "파워 아이템이 남아 있어도 일반 먹이 완료 시 승리");
        check(state.powerItems[0][2] && state.lives == 2
                && state.remainingMs == 180000 && "PLAYING".equals(state.phase),
                "판정 함수 자체는 게임 상태를 변경하지 않음");

        Map<String, Object> first = result.message(7);
        Map<String, Object> second = result.message(7);
        check(first.equals(second), "양측에 동일한 결과 데이터 생성");
        check("ROUND_RESULT".equals(first.get("type"))
                && Integer.valueOf(7).equals(first.get("roundId"))
                && "RESULT".equals(first.get("phase"))
                && "PACMAN".equals(first.get("winner"))
                && "ALL_PELLETS".equals(first.get("reason")),
                "프로토콜의 결과 필드");
        first.put("winner", "GHOST");
        check("PACMAN".equals(result.message(7).get("winner")),
                "메시지 수정이 확정된 결과를 변경하지 않음");
        check(Json.encode(second).contains("\"reason\":\"ALL_PELLETS\""),
                "결과 메시지 JSON 직렬화");
        System.out.println("PASS / 승패 판정 테스트 " + checks + "개 통과");
    }

    private static void expect(int lives, int pellets, int ms, String winner, String reason) {
        RoundResult result = RoundResult.evaluate(lives, pellets, ms);
        check(result != null && winner.equals(result.winner) && reason.equals(result.reason),
                "우선순위: lives=" + lives + ", pellets=" + pellets + ", ms=" + ms);
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        checks++;
    }
}
