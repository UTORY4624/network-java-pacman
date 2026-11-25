package pacman.client;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Locale;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class GameStatusPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel clock = new JLabel();
    private final JLabel lives = new JLabel();
    private final JLabel power = new JLabel();
    private final JLabel notice = new JLabel();

    GameStatusPanel() {
        super(new GridLayout(0, 1));
        JPanel metrics = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 0));
        metrics.add(clock);
        metrics.add(lives);
        metrics.add(power);
        add(metrics);
        add(notice);
        clear();
    }

    void clear() {
        clock.setText("남은 시간 · —");
        lives.setVisible(false);
        power.setVisible(false);
        notice.setText(" ");
    }

    void update(Object remainingMs, Map<?, ?> self, Map<?, ?> opponent) {
        clock.setText("남은 시간 · " + (remainingMs instanceof Long ? formatClock((Long) remainingMs) : "—"));

        Map<?, ?> pacman = "PACMAN".equals(self.get("role")) ? self : opponent;
        Object remainingLives = pacman == null ? null : pacman.get("lives");
        Object powerRemainingMs = pacman == null ? null : pacman.get("powerRemainingMs");
        lives.setVisible(remainingLives instanceof Long);
        if (lives.isVisible()) lives.setText("팩맨 목숨 · " + remainingLives);
        power.setVisible(powerRemainingMs instanceof Long);
        if (power.isVisible()) {
            long seconds = secondsLeft((Long) powerRemainingMs);
            power.setText("파워 · " + (seconds == 0 ? "없음" : seconds + "초"));
        }

        Object selfStatus = self.get("status");
        Object opponentStatus = opponent == null ? null : opponent.get("status");
        if ("RESPAWNING".equals(selfStatus) || "RESPAWNING".equals(opponentStatus)) {
            notice.setText("팩맨 재배치 대기 중");
        } else if ("RETURNING".equals(selfStatus) || "RETURNING".equals(opponentStatus)) {
            notice.setText("유령 복귀 대기 중");
        } else {
            notice.setText(" ");
        }
    }

    private static String formatClock(long remainingMs) {
        long seconds = secondsLeft(remainingMs);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static long secondsLeft(long millis) {
        return millis <= 0 ? 0 : 1 + (millis - 1) / 1000;
    }
}
