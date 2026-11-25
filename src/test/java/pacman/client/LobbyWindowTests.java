package pacman.client;

import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import pacman.server.GameUiScenario;
import pacman.server.PacmanServer;

// 준비·시작을 지원하는 #9 서버와 연결하는 Swing 통합 테스트.
public class LobbyWindowTests {
    public static void main(String[] args) throws Exception {
        try (PacmanServer server = new PacmanServer("127.0.0.1", 0)) {
            Thread serverThread = new Thread(() -> {
                try {
                    server.run();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            ConnectionWindow[] windows = new ConnectionWindow[2];
            try {
                SwingUtilities.invokeAndWait(() -> {
                    for (int i = 0; i < windows.length; i++) {
                        ConnectionWindow window = new ConnectionWindow();
                        windows[i] = window;
                        JTextField port = find(window.getContentPane(), JTextField.class,
                                "5555");
                        port.setText(Integer.toString(server.getPort()));
                        button(window, "접속하기").doClick(0);
                    }
                });
                ConnectionWindow first = windows[0];
                ConnectionWindow second = windows[1];
                waitFor(() -> enabled(first, "팩맨 선택") && enabled(second, "팩맨 선택"), "두 대기 화면");
                SwingUtilities.invokeAndWait(() -> {
                    check(!button(first, "준비").isEnabled(), "역할 선택 전 준비 버튼");
                    button(first, "팩맨 선택").doClick(0);
                });
                expectLabel(first, "나 · 팩맨 · 준비 전");
                expectLabel(second, "상대 · 팩맨 · 준비 전");

                click(second, "팩맨 선택");
                expectLabel(second, "상대가 이미 선택한 역할이에요. 다른 역할을 선택해 주세요.");
                click(second, "유령 선택");
                expectLabel(second, "나 · 유령 · 준비 전");
                expectLabel(first, "상대 · 유령 · 준비 전");

                SwingUtilities.invokeAndWait(() -> {
                    JButton ready = button(first, "준비");
                    check(ready.isEnabled(), "역할 선택 후 준비 버튼");
                    ready.doClick(0);
                    check(!ready.isEnabled(), "준비 요청 중 중복 클릭 방지");
                });
                expectLabel(first, "나 · 팩맨 · 준비 완료");
                expectLabel(second, "상대 · 팩맨 · 준비 완료");
                SwingUtilities.invokeAndWait(() -> {
                    check(label(first, "역할 선택과 준비") != null, "한 명만 준비하면 대기");
                    check(!button(first, "준비").isEnabled(), "준비 완료 후 중복 클릭 방지");
                });

                click(second, "준비");
                expectLabel(first, "1라운드 게임 시작");
                expectLabel(second, "1라운드 게임 시작");
                expectLabel(first, "내 역할 · 팩맨");
                expectLabel(second, "내 역할 · 유령");
                checkGameplay(server, first, second);

                click(second, "연결 해제");
                expectLabel(second, "연결을 해제했어요.");
                expectLabel(first, "상대가 연결을 종료했어요.");
                expectLabel(first, "상대 · 접속 대기");
                expectLabel(first, "나 · 팩맨 · 준비 전");
                waitFor(() -> enabled(first, "준비"), "퇴장 후 다시 준비 가능");
                System.out.println("PASS / 두 창에서 대기실, 이동, 먹이, 목숨, 파워 및 재배치·복귀 표시");
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    for (ConnectionWindow window : windows) {
                        if (window != null) window.dispose();
                    }
                });
                server.close();
                serverThread.join(3000);
            }
        }
    }

    private static void checkGameplay(PacmanServer server, ConnectionWindow first, ConnectionWindow second) throws Exception {
        JComponent[] boards = new JComponent[2];
        int[] colors = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            KeyStroke left = KeyStroke.getKeyStroke("LEFT");
            boards[0] = NetworkGameplayTests.findBinding(first.getContentPane(), left);
            boards[1] = NetworkGameplayTests.findBinding(second.getContentPane(), left);
            for (JComponent board : boards) {
                check(board != null, "each client shows the gameplay board");
                board.setSize(board.getPreferredSize());
            }
        });

        // Center pixels at the original map's spawn tiles (26,45) and (26,21).
        int centerX = 26 * 8 + 16, pacmanY = 45 * 8 + 16, ghostY = 21 * 8 + 16;
        waitFor(() -> {
            colors[0] = NetworkGameplayTests.pixel(boards[0], centerX, pacmanY);
            colors[1] = NetworkGameplayTests.pixel(boards[1], centerX, ghostY);
            return (colors[0] & 0x00ff0000) > 0x00d00000 && (colors[1] & 0x00ff0000) > 0x00d00000
                    && colors[0] != colors[1];
        }, "both clients receive the initial server positions");

        int leftOfPacman = centerX - 18;
        int rightOfGhost = centerX + 18;
        // The leftward route crosses the pellet at (24,46).
        int pelletX = 24 * 8 + 10, pelletY = 46 * 8 + 10;
        int background = boards[0].getBackground().getRGB();
        SwingUtilities.invokeAndWait(() -> {
            int pelletColor = NetworkGameplayTests.pixel(boards[0], pelletX, pelletY);
            check(pelletColor != background && matchesBoth(boards, pelletX, pelletY, pelletColor),
                    "both clients initially see the pellet");
        });
        SwingUtilities.invokeAndWait(() -> NetworkGameplayTests.press(boards[0], "LEFT"));
        waitFor(() -> matchesBoth(boards, leftOfPacman, pacmanY, colors[0])
                && matchesBoth(boards, centerX, ghostY, colors[1]), "first input moves only Pacman in both clients");

        SwingUtilities.invokeAndWait(() -> NetworkGameplayTests.press(boards[1], "RIGHT"));
        waitFor(() -> matchesBoth(boards, rightOfGhost, ghostY, colors[1]), "second input moves Ghost in both clients");
        waitFor(() -> matchesBoth(boards, pelletX, pelletY, background), "both clients remove the collected pellet");

        GameUiScenario.placePlayersInContact(server);
        waitFor(() -> bothShow(first, second, "팩맨 재배치 대기 중") && bothShow(first, second, "팩맨 목숨 · 1")
                && matchesBoth(boards, centerX, pacmanY, colors[0])
                && matchesBoth(boards, centerX, ghostY, colors[1]), "both clients show the updated lives and spawn positions");
        waitFor(() -> bothHide(first, second, "팩맨 재배치 대기 중"), "both clients clear the respawn notice");

        GameUiScenario.placePacmanByPowerItem(server);
        waitFor(() -> bothShow(first, second, "파워 · 5초"), "both clients show the acquired power");
        GameUiScenario.placePlayersInContact(server);
        waitFor(() -> bothShow(first, second, "유령 복귀 대기 중") && bothShow(first, second, "팩맨 목숨 · 1")
                && matchesBoth(boards, centerX, ghostY, colors[1]), "both clients show the returning Ghost at its spawn");
        waitFor(() -> bothHide(first, second, "유령 복귀 대기 중"), "both clients clear the return notice");
        waitFor(() -> bothShow(first, second, "파워 · 없음"), "both clients clear the expired power");
    }

    private static boolean bothShow(ConnectionWindow first, ConnectionWindow second, String text) {
        return label(first, text) != null && label(second, text) != null;
    }

    private static boolean bothHide(ConnectionWindow first, ConnectionWindow second, String text) {
        return label(first, text) == null && label(second, text) == null;
    }

    private static boolean matchesBoth(JComponent[] boards, int x, int y, int color) {
        return NetworkGameplayTests.pixel(boards[0], x, y) == color
                && NetworkGameplayTests.pixel(boards[1], x, y) == color;
    }

    private static void click(ConnectionWindow window, String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton button = button(window, text);
            check(button != null && button.isEnabled(), "클릭 가능한 버튼: " + text);
            button.doClick(0);
        });
    }

    private static void expectLabel(ConnectionWindow window, String text) throws Exception {
        waitFor(() -> label(window, text) != null, text);
    }

    private static void waitFor(BooleanSupplier condition, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        AtomicBoolean found = new AtomicBoolean();
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> found.set(condition.getAsBoolean()));
            if (found.get()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("화면 확인 실패: " + description);
    }

    private static boolean enabled(ConnectionWindow window, String text) {
        JButton button = button(window, text);
        return button != null && button.isEnabled();
    }

    private static JButton button(ConnectionWindow window, String text) {
        return find(window.getContentPane(), JButton.class, text);
    }

    private static String label(ConnectionWindow window, String text) {
        JLabel label = find(window.getContentPane(), JLabel.class, text);
        return label == null ? null : label.getText();
    }

    private static <T extends Component> T find(Container parent, Class<T> type, String text) {
        for (Component component : parent.getComponents()) {
            if (!component.isVisible()) continue;
            if (type.isInstance(component)) {
                String value;
                if (component instanceof JButton) value = ((JButton) component).getText();
                else if (component instanceof JLabel) value = ((JLabel) component).getText();
                else value = ((JTextField) component).getText();
                if (text.equals(value)) return type.cast(component);
            }
            if (component instanceof Container) {
                T match = find((Container) component, type, text);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
