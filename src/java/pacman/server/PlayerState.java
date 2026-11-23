package pacman.server;

final class PlayerState {
    final long id;
    final String role;
    final int startX;
    final int startY;
    int x;
    int y;
    String direction = "LEFT";
    // 처음에는 가만히 있고, 실제 이동 입력은 #12에서 받기
    String requestedDirection;
    String status = "ACTIVE";
    int waitRemainingMs;

    PlayerState(long id, String role, int x, int y) {
        this.id = id;
        this.role = role;
        startX = this.x = x;
        startY = this.y = y;
    }
}
