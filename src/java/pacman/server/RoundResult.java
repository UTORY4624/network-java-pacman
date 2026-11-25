package pacman.server;

import java.util.Map;

/**
 * 이동 -> 먹이/아이템 -> 접촉을 모두 처리한 뒤 호출하는 승패 판정.
 * 상태를 변경하지 않는다. 결과 확정과 1회 전송은 게임 루프에서 담당한다.
 */
final class RoundResult {
    final String winner;
    final String reason;

    private RoundResult(String winner, String reason) {
        this.winner = winner;
        this.reason = reason;
    }

    /** null이면 아직 라운드를 종료하지 않는다. 파워 아이템은 먹이 수에 포함하지 않는다. */
    static RoundResult evaluate(int lives, int remainingPellets, int remainingMs) {
        if (remainingPellets < 0) {
            throw new IllegalArgumentException("Remaining pellets cannot be negative");
        }
        // 마지막 먹이/시간 초과와 치명적 접촉이 겹쳐도 유령 승리가 우선이다.
        if (lives <= 0) return new RoundResult("GHOST", "NO_LIVES");
        if (remainingPellets == 0) return new RoundResult("PACMAN", "ALL_PELLETS");
        if (remainingMs <= 0) return new RoundResult("GHOST", "TIMEOUT");
        return null;
    }

    /** 일반 먹이만 세므로 파워 아이템이 남아 있어도 팩맨이 승리할 수 있다. */
    static RoundResult evaluate(GameState state) {
        int remainingPellets = 0;
        for (boolean[] row : state.pellets) {
            for (boolean pellet : row) {
                if (pellet) remainingPellets++;
            }
        }
        return evaluate(state.lives, remainingPellets, state.remainingMs);
    }

    /** 매번 새 메시지를 만들어 호출자가 수정해도 확정된 승자와 이유는 유지한다. */
    Map<String, Object> message(int roundId) {
        if (roundId < 1) throw new IllegalArgumentException("Invalid round id");
        return Json.object("type", "ROUND_RESULT", "roundId", roundId,
                "phase", "RESULT", "winner", winner, "reason", reason);
    }
}
