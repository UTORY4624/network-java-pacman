package pacman.server;

import java.io.StringReader;
import java.util.Map;

/** 가상 경과 시간으로 검사하므로 실제 180초를 기다릴 필요가 없다. */
public class GameplayTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        GameState original = new GameState(GameMap.load(), 1, 1, 2);
        original.move(1, "LEFT"); original.advance(10000);
        check(original.pacman.x < original.map.pacmanX, "원본 맵에서 32px 캐릭터 이동");
        for (int y = original.pacman.y; y < original.pacman.y + 4; y++) {
            for (int x = original.pacman.x; x < original.pacman.x + 4; x++) {
                check(!original.map.isWall(x, y), "32px 전체 충돌 영역은 벽 밖");
            }
        }
        original.pacman.x = 1; original.pacman.y = 1;
        original.pacman.requestedDirection = null; original.advance(1);
        check(!original.pellets[2][2], "원본 먹이의 8px 오프셋과 캐릭터 중심 판정");
        original.pacman.x = 1; original.pacman.y = 5; original.advance(1);
        check(!original.powerItems[6][2] && original.powerRemainingMs == 5000, "원본 파워 아이템 16px 영역 판정");
        GameState s = arena();
        s.advance(1000);
        check(s.pacman.x == 0 && s.ghost.x == 0, "입력 전 정지");
        check(!s.move(999, "RIGHT") && !s.move(1, "INVALID"), "사용자 및 방향 검증");
        s.move(1, "RIGHT"); s.move(2, "RIGHT");
        s.advance(4000);
        check(s.pacman.x == 20 && s.ghost.x == 23, "초당 5칸 / 5.75칸 독립 이동");
        s.move(1, "LEFT"); s.advance(200);
        check(s.pacman.x == 19 && "LEFT".equals(s.pacman.direction), "방향 전환");
        s.phase = "RESULT";
        check(!s.move(1, "RIGHT"), "결과 상태 입력 거절");
        s.advance(1000);
        check(s.pacman.x == 19, "결과 상태 갱신 중지");

        s = state("P;x;.;b\n ; ; ; \n");
        s.move(1, "RIGHT"); s.advance(2000);
        check(s.pacman.x == 0, "벽 통과 및 이동 누적 방지");
        s.move(1, "DOWN"); s.advance(199);
        check(s.pacman.y == 0, "벽에서 대기한 시간을 이동에 적립하지 않음");
        s.advance(1); check(s.pacman.y == 1, "열린 방향 전환");
        s.move(1, "LEFT"); s.advance(1000);
        check(s.pacman.x == 0, "왼쪽 맵 경계");
        s.move(1, "DOWN"); s.advance(1000);
        check(s.pacman.y == 1, "아래쪽 맵 경계");
        s.move(2, "RIGHT"); s.advance(1000);
        check(s.ghost.x == 3, "오른쪽 맵 경계");
        s.move(2, "UP"); s.advance(1000);
        check(s.ghost.y == 0, "위쪽 맵 경계");

        s = state("P;-; ;b\n");
        s.move(1, "RIGHT"); s.move(2, "LEFT"); s.advance(348);
        check(s.pacman.x == 0 && s.ghost.x == 1, "팩맨 문 통과 금지, 유령 허용");

        s = state("P;.;o; ;b\n");
        s.move(1, "RIGHT"); s.advance(200);
        check(!s.pellets[0][1], "이동 후 일반 먹이 제거");
        s.advance(200);
        check(!s.powerItems[0][2] && s.powerRemainingMs == 5000, "파워 획득 5초");
        s.advance(400);
        check(s.lives == 2 && "RETURNING".equals(s.ghost.status), "파워 접촉은 유령 포획");
        check(s.ghost.x == s.ghost.startX && s.ghost.waitRemainingMs == 3000, "유령 시작 위치 복귀");
        check(!s.move(2, "LEFT"), "복귀 대기 중 입력 저장 금지");
        int time = s.remainingMs, power = s.powerRemainingMs;
        s.advance(2999);
        check(s.remainingMs == time - 2999 && s.powerRemainingMs == power - 2999,
                "유령 대기 중 제한 시간 및 효과 진행");
        check(s.ghost.waitRemainingMs == 1 && s.lives == 2, "대기 중 접촉 제외");
        s.pacman.x = 0; s.pacman.requestedDirection = null;
        s.advance(1);
        check("ACTIVE".equals(s.ghost.status) && s.ghost.requestedDirection == null, "유령 복귀 후 새 입력 필요");
        s.advance(s.powerRemainingMs);
        check(s.powerRemainingMs == 0, "파워 만료");

        s = state("P;o;o; ;b\n");
        s.move(1, "RIGHT"); s.advance(400);
        check(s.powerRemainingMs == 5000 && !s.powerItems[0][1] && !s.powerItems[0][2], "재획득은 5초로 갱신");

        s = state("P;.;b\n");
        s.move(1, "RIGHT"); s.advance(400);
        check(s.lives == 1 && s.pacman.x == 0 && s.ghost.x == 2, "일반 접촉 목숨 감소 및 양측 재배치");
        check("RESPAWNING".equals(s.pacman.status) && "RESPAWNING".equals(s.ghost.status), "양측 재배치 상태");
        check(!s.move(1, "RIGHT") && !s.move(2, "LEFT"), "재배치 중 입력 거절");
        time = s.remainingMs;
        s.advance(2999);
        check(s.remainingMs == time && s.lives == 1 && !s.pellets[0][1], "대기 중 시간 정지, 중복 차감 없음, 먹이 유지");
        s.advance(1); s.advance(1000);
        check(s.pacman.x == 0 && s.ghost.x == 2, "재개 후 이전 입력으로 움직이지 않음");
        s.move(1, "RIGHT"); s.advance(400);
        check(s.lives == 0, "두 번째 일반 접촉 목숨 소진");
        time = s.remainingMs; s.advance(1000);
        check(s.lives == 0 && s.remainingMs == time && !s.move(1, "LEFT"), "목숨 소진 후 판정 중지 (#18 연결 대기)");

        s = state("P;.;b\n");
        s.pacman.x = 1; s.ghost.x = 2;
        s.pacman.movementCredit = 3980; s.ghost.movementCredit = 3977;
        s.move(1, "RIGHT"); s.move(2, "LEFT"); s.advance(1);
        check(s.lives == 1, "서로 칸을 교차하는 접촉도 판정");

        s = state("P;o;b\n");
        s.ghost.x = 1; s.move(1, "RIGHT"); s.advance(200);
        check(s.lives == 2 && "RETURNING".equals(s.ghost.status), "아이템 획득 후 접촉 순서");

        s = state("P;.;b\n");
        s.lives = 1; s.ghost.x = 1; s.move(1, "RIGHT"); s.advance(200);
        check(s.lives == 0 && !s.pellets[0][1], "마지막 먹이와 치명적 접촉 모두 반영 (#18 판정용)");
        s = arena(); s.remainingMs = 2; s.advance(100);
        check(s.remainingMs == 0 && !s.move(1, "RIGHT"), "제한 시간은 0 아래로 내려가지 않음");

        s = arena(); s.sequence = 7;
        s.ghost.x = s.pacman.x + 3; s.ghost.y = s.pacman.y + 4;
        Map<String, Object> first = s.snapshot(1), second = s.snapshot(2);
        check(first.get("self").equals(second.get("opponent"))
                && first.get("opponent").equals(second.get("self")), "사용자별 self / opponent 전환");
        check(first.get("food").equals(second.get("food")) && first.get("sequence").equals(second.get("sequence")),
                "먹이 및 동일 갱신 번호 공유");
        check(Json.encode(first).contains("\"lives\":2") && Json.encode(first).contains("\"powerRemainingMs\":0"),
                "팩맨 정보 직렬화");
        System.out.println("PASS / 이동·아이템 판정 테스트 " + checks + "개 통과");
    }

    private static GameState state(String csv) throws Exception {
        return new GameState(GameMap.read(new StringReader(csv)), 1, 1, 2, 1);
    }

    private static GameState arena() throws Exception {
        StringBuilder top = new StringBuilder("P"), bottom = new StringBuilder("b");
        for (int i = 0; i < 40; i++) { top.append("; "); bottom.append("; "); }
        return state(top + "\n" + bottom + "\n");
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        checks++;
    }
}
