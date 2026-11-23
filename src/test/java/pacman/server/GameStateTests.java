package pacman.server;

import java.io.IOException;
import java.io.StringReader;

public class GameStateTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        GameMap map = GameMap.load();
        check(map.width == 56 && map.height == 62, "원본 맵 크기");
        check(map.pacmanX == 26 && map.pacmanY == 45, "팩맨 시작 위치");
        check(map.ghostX == 26 && map.ghostY == 21, "빨간 유령 시작 위치");
        check(map.isWall(0, 0) && !map.isWall(26, 45), "벽과 시작 칸 구분");
        check(map.isWall(-1, 0) && map.isWall(map.width, 0)
                && map.isWall(0, -1) && map.isWall(0, map.height), "맵 밖 좌표 구분");
        check(map.isGhostDoor(26, 25) && !map.isWall(26, 25), "유령 출입문 구분");
        GameState first = new GameState(map, 1, 7, 3);
        check(first.player(7) == first.pacman && first.player(3) == first.ghost
                && first.player(999) == null, "접속 번호로 사용자 찾기");
        check(first.pacman.x == 26 && first.pacman.y == 45
                && first.ghost.x == 26 && first.ghost.y == 21, "두 캐릭터 위치 초기화");
        check(first.lives == 2 && first.remainingMs == 180000
                && first.powerRemainingMs == 0, "목숨과 시간 초기화");
        check("PLAYING".equals(first.phase) && first.sequence == 0, "새 라운드 상태");
        check(count(first.pellets) == 240 && count(first.powerItems) == 4, "먹이 개수");
        check(first.pacman.requestedDirection == null && first.ghost.requestedDirection == null,
                "새 판에 이동 입력 없음");

        // 이전 판에서 바뀐 값이 다음 판에 남지 않는지 확인
        first.pellets[2][2] = false;
        first.powerItems[6][2] = false;
        first.pacman.x = 12;
        first.pacman.requestedDirection = "UP";
        first.ghost.status = "RETURNING";
        first.ghost.waitRemainingMs = 3000;
        first.lives = 1;
        first.remainingMs = 5000;
        first.powerRemainingMs = 2000;
        GameState next = new GameState(map, 2, 3, 7);
        check(next.roundId == 2 && next.pacman.id == 3 && next.ghost.id == 7, "역할 바꾼 새 판");
        check(next.pacman.x == map.pacmanX && next.ghost.x == map.ghostX
                && next.pacman.requestedDirection == null && next.ghost.waitRemainingMs == 0
                && "ACTIVE".equals(next.ghost.status), "위치와 입력 및 대기 초기화");
        check(next.lives == 2 && next.remainingMs == 180000 && next.powerRemainingMs == 0,
                "새 판 목숨과 효과 시간 초기화");
        check(count(next.pellets) == 240 && count(next.powerItems) == 4
                && count(first.pellets) == 239 && count(first.powerItems) == 3, "라운드별 먹이 배열 분리");
        check(map.tileAt(2, 2) == '.' && map.tileAt(2, 6) == 'o', "원본 맵 보존");
        badMap("");
        badMap("P; ;b;\nx;x;\n");
        badMap("P;?;b;\n");
        badMap("P;P;b;\n");
        badMap("P;b;b;\n");
        badMap("P; ; ;\n");
        try {
            new GameState(map, 1, 7, 7);
            throw new AssertionError("같은 사용자가 두 역할을 맡으면 안 됨");
        } catch (IllegalArgumentException expected) { checks++; }
        System.out.println("PASS / 서버 게임 상태 테스트 " + checks + "개 통과");
    }

    private static int count(boolean[][] cells) {
        int total = 0;
        for (boolean[] row : cells) for (boolean value : row) if (value) total++;
        return total;
    }

    private static void badMap(String text) throws Exception {
        try {
            GameMap.read(new StringReader(text));
            throw new AssertionError("잘못된 맵을 허용하면 안 됨");
        } catch (IOException expected) { checks++; }
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        checks++;
    }
}
