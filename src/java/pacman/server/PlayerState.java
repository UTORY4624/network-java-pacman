package pacman.server;

final class PlayerState {
    final long id;
    final String role;
    final int startX;
    final int startY;
    int x;
    int y;
    String direction = "LEFT";
    // 입력 전에는 정지. 4000 단위마다 한 칸 이동 (팩맨 20/ms, 유령 23/ms).
    String requestedDirection;
    int movementCredit;
    String status = "ACTIVE";
    int waitRemainingMs;

    PlayerState(long id, String role, int x, int y) {
        this.id = id;
        this.role = role;
        startX = this.x = x;
        startY = this.y = y;
    }
}
