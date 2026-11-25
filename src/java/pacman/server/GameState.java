package pacman.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

// 이 데이터는 서버가 관리하고, 클라이언트는 전달받은 값으로 화면만 그리기
final class GameState {
    static final int INITIAL_LIVES = 2;
    static final int ROUND_TIME_MS = 180000;
    final GameMap map;
    final int bodySize;
    final int roundId;
    final PlayerState pacman;
    final PlayerState ghost;
    final boolean[][] pellets;
    final boolean[][] powerItems;
    int lives = INITIAL_LIVES;
    int remainingMs = ROUND_TIME_MS;
    int powerRemainingMs;
    long sequence;
    String phase = "PLAYING";

    GameState(GameMap map, int roundId, long pacmanId, long ghostId) {
        this(map, roundId, pacmanId, ghostId, 4);
    }

    // 작은 테스트 맵에서도 같은 갱신 흐름을 검증할 수 있게 충돌 크기를 지정한다.
    GameState(GameMap map, int roundId, long pacmanId, long ghostId, int bodySize) {
        if (roundId < 1 || pacmanId <= 0 || ghostId <= 0 || pacmanId == ghostId) {
            throw new IllegalArgumentException("라운드 번호와 두 사용자 번호를 확인해주세요.");
        }
        this.map = map;
        if (bodySize < 1) throw new IllegalArgumentException("Invalid body size");
        this.bodySize = bodySize;
        this.roundId = roundId;
        pacman = new PlayerState(pacmanId, "PACMAN", map.pacmanX, map.pacmanY);
        ghost = new PlayerState(ghostId, "GHOST", map.ghostX, map.ghostY);
        pellets = new boolean[map.height][map.width];
        powerItems = new boolean[map.height][map.width];
        // 매 라운드 새 배열을 만들어서 이전 판에서 먹은 내용이 남지 않게 하기
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                pellets[y][x] = map.tileAt(x, y) == '.';
                powerItems[y][x] = map.tileAt(x, y) == 'o';
            }
        }
    }

    PlayerState player(long id) {
        if (pacman.id == id) return pacman;
        if (ghost.id == id) return ghost;
        return null;
    }

    static boolean validDirection(String direction) {
        return "UP".equals(direction) || "DOWN".equals(direction)
                || "LEFT".equals(direction) || "RIGHT".equals(direction);
    }

    boolean move(long id, String direction) {
        PlayerState player = player(id);
        if (!validDirection(direction) || player == null || !"PLAYING".equals(phase)
                || lives <= 0 || remainingMs <= 0 || !"ACTIVE".equals(player.status)) return false;
        player.requestedDirection = direction;
        return true;
    }

    // 1ms 단위로 나눠 처리해 큰 갱신에서도 먹이·접촉 칸을 건너뛰지 않는다.
    // 반환값은 수집·접촉·대기 종료 등 즉시 전송할 변화가 있었는지 나타낸다.
    boolean advance(int elapsedMs) {
        boolean changed = false;
        for (int i = 0; i < elapsedMs && "PLAYING".equals(phase)
                && lives > 0 && remainingMs > 0; i++) {
            if ("RESPAWNING".equals(pacman.status)) {
                pacman.waitRemainingMs--;
                ghost.waitRemainingMs = pacman.waitRemainingMs;
                if (pacman.waitRemainingMs == 0) {
                    pacman.status = ghost.status = "ACTIVE";
                    changed = true;
                }
                continue; // 재배치 중 제한 시간과 효과 시간도 정지
            }
            remainingMs--;
            if (powerRemainingMs > 0 && --powerRemainingMs == 0) changed = true;
            boolean ghostWasWaiting = "RETURNING".equals(ghost.status);
            if (ghostWasWaiting && --ghost.waitRemainingMs == 0) {
                ghost.status = "ACTIVE";
                changed = true;
            }
            int px = pacman.x, py = pacman.y, gx = ghost.x, gy = ghost.y;
            step(pacman, 20);
            if (!ghostWasWaiting) step(ghost, 23);
            // 원본 먹이 렌더링: 일반 먹이는 CSV 좌표 + (8px,8px), 파워는 16px.
            int centerX = pacman.x + bodySize / 2, centerY = pacman.y + bodySize / 2;
            int pelletX = centerX - (bodySize == 1 ? 0 : 1);
            int pelletY = centerY - (bodySize == 1 ? 0 : 1);
            if (map.contains(pelletX, pelletY) && pellets[pelletY][pelletX]) {
                pellets[pelletY][pelletX] = false;
                changed = true;
            }
            for (int y = centerY - (bodySize == 1 ? 0 : 1); y <= centerY; y++) {
                for (int x = centerX - (bodySize == 1 ? 0 : 1); x <= centerX; x++) {
                    if (map.contains(x, y) && powerItems[y][x]) {
                        powerItems[y][x] = false;
                        powerRemainingMs = 5000;
                        changed = true;
                    }
                }
            }
            // 원본처럼 팩맨 중심점이 유령 사각형 안에 들어오면 접촉한다.
            boolean sameCell = centerX >= ghost.x && centerX < ghost.x + bodySize
                    && centerY >= ghost.y && centerY < ghost.y + bodySize;
            boolean crossed = px == ghost.x && py == ghost.y && gx == pacman.x && gy == pacman.y;
            if (!ghostWasWaiting && "ACTIVE".equals(ghost.status) && (sameCell || crossed)) {
                changed = true;
                if (powerRemainingMs > 0) {
                    reset(ghost, "RETURNING", 3000);
                } else {
                    lives--;
                    if (lives > 0) {
                        reset(pacman, "RESPAWNING", 3000);
                        reset(ghost, "RESPAWNING", 3000);
                    } else {
                        pacman.requestedDirection = ghost.requestedDirection = null;
                    }
                }
            }
        }
        return changed;
    }

    private void step(PlayerState player, int speed) {
        if (player.requestedDirection == null) return;
        player.movementCredit += speed;
        if (player.movementCredit < 4000) return;
        player.movementCredit -= 4000;
        String direction = player.requestedDirection;
        int x = player.x + ("LEFT".equals(direction) ? -1 : "RIGHT".equals(direction) ? 1 : 0);
        int y = player.y + ("UP".equals(direction) ? -1 : "DOWN".equals(direction) ? 1 : 0);
        for (int yy = y; yy < y + bodySize; yy++) for (int xx = x; xx < x + bodySize; xx++) {
            if (map.isWall(xx, yy) || (player == pacman && map.isGhostDoor(xx, yy))) return;
        }
        player.x = x;
        player.y = y;
        player.direction = direction;
    }

    private void reset(PlayerState player, String status, int wait) {
        player.x = player.startX;
        player.y = player.startY;
        player.direction = "LEFT";
        player.requestedDirection = null;
        player.movementCredit = 0;
        player.status = status;
        player.waitRemainingMs = wait;
    }

    Map<String, Object> snapshot(long id) {
        PlayerState self = player(id);
        if (self == null) throw new IllegalArgumentException("Unknown player");
        // #16 전까지 개발용으로 상대 전체 좌표 공개. 최종 탐지는 여기서 제한한다.
        return Json.object("type", "GAME_STATE", "roundId", roundId, "sequence", sequence,
                "phase", phase, "remainingMs", remainingMs, "self", playerData(self),
                "opponent", playerData(self == pacman ? ghost : pacman),
                "food", Json.object("pellets", coordinates(pellets), "powerItems", coordinates(powerItems)));
    }

    private Map<String, Object> playerData(PlayerState player) {
        Map<String, Object> data = Json.object("id", player.id, "role", player.role,
                "x", player.x, "y", player.y, "direction", player.direction, "status", player.status);
        if (player == pacman) {
            data.put("lives", lives);
            data.put("powerRemainingMs", powerRemainingMs);
        }
        return data;
    }

    private List<List<Integer>> coordinates(boolean[][] cells) {
        List<List<Integer>> result = new ArrayList<>();
        for (int y = 0; y < map.height; y++) for (int x = 0; x < map.width; x++) {
            if (cells[y][x]) result.add(Arrays.asList(x, y));
        }
        return result;
    }
}
