package pacman.client;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import pacman.server.PacmanServer;

public class ConnectionWindowTests {
    private static ConnectionWindow window;
    private static JButton button;
    private static List<JTextField> fields;

    public static void main(String[] args) throws Exception {
        PacmanServer server = new PacmanServer("127.0.0.1", 0);
        Thread serverThread = new Thread(() -> {
            try {
                server.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        serverThread.start();
        try {
            SwingUtilities.invokeAndWait(() -> {
                window = new ConnectionWindow();
                fields = find(window, JTextField.class);
                button = find(window, JButton.class).get(0);
                fields.get(1).setText("invalid");
                button.doClick();
            });
            waitFor("포트는 1~65535 사이 숫자로 입력해주세요.");
            SwingUtilities.invokeAndWait(() -> {
                fields.get(1).setText(Integer.toString(server.getPort()));
                button.doClick();
            });
            waitFor("서버에 접속했어요.");
            SwingUtilities.invokeAndWait(() -> {
                if (!button.getText().equals("연결 해제") || fields.get(0).isEnabled()) {
                    throw new AssertionError("접속 후 버튼과 입력 상태");
                }
                button.doClick();
            });
            waitFor("연결을 해제했어요.");
            SwingUtilities.invokeAndWait(() -> {
                if (!button.getText().equals("접속하기") || !fields.get(0).isEnabled()) {
                    throw new AssertionError("연결 해제 후 입력 상태");
                }
                button.doClick();
                button.doClick();
                button.doClick();
            });
            // 취소 직후 다시 눌렀을 때 이전 응답이 화면을 덮지 않는지 확인
            waitFor("서버에 접속했어요.");
            if (args.length == 1) {
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        Container content = window.getContentPane();
                        BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
                        java.awt.Graphics2D graphics = image.createGraphics();
                        content.printAll(graphics);
                        graphics.dispose();
                        ImageIO.write(image, "png", new File(args[0]));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            server.close();
            waitFor("서버와 연결이 종료됐어요.");
            SwingUtilities.invokeAndWait(() -> {
                if (!button.getText().equals("접속하기") || !fields.get(1).isEnabled()) {
                    throw new AssertionError("서버 종료 후 재접속 가능 상태");
                }
            });
            System.out.println("PASS / 버튼 접속, 해제, 입력 검사, 취소 후 재접속, 서버 종료 화면");
        } finally {
            server.close();
            if (window != null) SwingUtilities.invokeAndWait(window::dispose);
            serverThread.join(3000);
        }
    }

    private static void waitFor(String expected) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> {
                for (JLabel label : find(window, JLabel.class)) {
                    if (expected.equals(label.getText())) found.set(true);
                }
            });
            if (found.get()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("화면 안내 확인 실패 / " + expected);
    }

    private static <T> List<T> find(Container parent, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component component : parent.getComponents()) {
            if (type.isInstance(component)) result.add(type.cast(component));
            if (component instanceof Container) result.addAll(find((Container) component, type));
        }
        return result;
    }
}
