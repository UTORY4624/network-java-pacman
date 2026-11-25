package pacman.server;

import java.io.StringReader;
import java.util.Map;

public class DetectionTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        GameState state = new GameState(GameMap.load(), 1, 7, 9);
        state.pacman.x = 10; state.pacman.y = 10;
        int[][] cases = {{0, 0, 1}, {5, 0, 1}, {0, -5, 1}, {3, 4, 1},
                {-3, -4, 1}, {6, 0, 0}, {0, 6, 0}, {4, 4, 0}, {5, 1, 0}};
        for (int[] test : cases) {
            state.ghost.x = 10 + test[0]; state.ghost.y = 10 + test[1];
            check((state.snapshot(7).get("opponent") != null) == (test[2] == 1), "팩맨 직선거리 경계");
            check((state.snapshot(9).get("opponent") != null) == (test[2] == 1), "유령 직선거리 경계");
        }
        state.ghost.x = 13; state.ghost.y = 14;
        Map<String, Object> visible = state.snapshot(7);
        check(visible.get("opponent").equals(state.snapshot(9).get("self")), "탐지된 상대 정보 일치");
        Object food = visible.get("food"), self = visible.get("self");
        state.ghost.x = 16; state.ghost.y = 10;
        Map<String, Object> hidden = state.snapshot(7);
        check(hidden.containsKey("opponent") && hidden.get("opponent") == null, "범위 이탈 시 명시적 null");
        check(Json.encode(hidden).contains("\"opponent\":null")
                && !Json.encode(hidden).contains("\"id\":9"), "숨겨진 상대 ID와 좌표 미전송");
        check(hidden.get("self").equals(self) && hidden.get("food").equals(food), "본인과 먹이 정보 유지");
        state.pellets[2][2] = false;
        check(!state.snapshot(7).get("food").equals(food)
                && state.snapshot(7).get("food").equals(state.snapshot(9).get("food")), "범위 밖에서도 먹이 변화 공유");
        state.powerRemainingMs = 5000;
        check(state.snapshot(7).get("opponent") == null && state.snapshot(9).get("opponent") == null,
                "파워 효과 중에도 범위 밖 상대 숨김");
        state.ghost.x = 15;
        check(state.snapshot(7).get("opponent") != null && state.snapshot(9).get("opponent") != null,
                "파워 효과 중 경계 재진입");
        state.ghost.x = 16;
        check(state.snapshot(7).get("opponent") == null, "재이탈 시 이전 좌표 제거");
        check(visible.get("opponent") != null, "기존 스냅샷에 이후 상태를 덮어쓰지 않음");
        GameMap wall = GameMap.read(new StringReader("P; ;x; ;b;\n"));
        GameState throughWall = new GameState(wall, 1, 1, 2, 1);
        check(wall.isWall(2, 0) && throughWall.snapshot(1).get("opponent") != null
                && throughWall.snapshot(2).get("opponent") != null, "벽 너머 직선거리 탐지");
        System.out.println("PASS / 상대 탐지 테스트 " + checks + "개 통과");
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        checks++;
    }
}
