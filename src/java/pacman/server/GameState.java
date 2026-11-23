package pacman.server;

// 이 데이터는 서버가 관리하고, 클라이언트는 전달받은 값으로 화면만 그리기
final class GameState {
    static final int INITIAL_LIVES = 2;
    static final int ROUND_TIME_MS = 180000;
    final GameMap map;
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
        if (roundId < 1 || pacmanId <= 0 || ghostId <= 0 || pacmanId == ghostId) {
            throw new IllegalArgumentException("라운드 번호와 두 사용자 번호를 확인해주세요.");
        }
        this.map = map;
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
}
