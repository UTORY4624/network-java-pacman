package pacman.client;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public class NetworkGameplayTests {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(NetworkGameplayTests::checkGameplay);
        System.out.println("PASS / gameplay input, server positions, round and visibility changes");
    }

    private static void checkGameplay() {
        List<String> sent = new ArrayList<>();
        LobbyPanel lobby = new LobbyPanel(1, sent::add, () -> {});
        check(findBinding(lobby, KeyStroke.getKeyStroke("UP")) == null, "lobby has no move binding");
        receive(lobby, "{\"type\":\"GAME_START\",\"roundId\":1}");
        JComponent board = findBinding(lobby, KeyStroke.getKeyStroke("UP"));
        check(board != null, "gameplay card has arrow bindings");
        board.setSize(board.getPreferredSize());
        press(board, "UP");
        check(sent.isEmpty(), "gameplay waits for the initial state before sending input");

        int emptySelf = pixel(board, 64, 64);
        int emptyOpponent = pixel(board, 144, 64);
        receive(lobby, state(1, 1, 6, "ACTIVE", true));
        int self = pixel(board, 64, 64);
        int opponent = pixel(board, 144, 64);
        check(self != emptySelf && opponent != emptyOpponent && self != opponent,
                "players appear at the two positions sent by the server");
        for (String direction : Arrays.asList("UP", "DOWN", "LEFT", "RIGHT")) press(board, direction);
        check(sent.equals(Arrays.asList("MOVE UP", "MOVE DOWN", "MOVE LEFT", "MOVE RIGHT")),
                "arrow bindings send the four protocol commands");
        check(pixel(board, 64, 64) == self, "local input does not change the rendered position");

        receive(lobby, state(2, 2, 6, "ACTIVE", false));
        check(pixel(board, 144, 64) == opponent, "another round cannot alter the current view");
        receive(lobby, state(1, 2, 10, "ACTIVE", false));
        check(pixel(board, 64, 64) == emptySelf && pixel(board, 96, 64) == self,
                "only the server state moves the local character");
        check(pixel(board, 144, 64) == emptyOpponent, "null opponent removes the old position");
        receive(lobby, state(1, 1, 6, "ACTIVE", true));
        check(pixel(board, 144, 64) == emptyOpponent && pixel(board, 96, 64) == self,
                "stale state cannot restore old positions");

        receive(lobby, state(1, 3, 10, "RESPAWNING", false));
        press(board, "UP");
        check(sent.size() == 4, "inactive players cannot send movement");
        receive(lobby, "{\"type\":\"PLAYER_LEFT\",\"playerId\":2}");
        receive(lobby, state(1, 4, 6, "ACTIVE", true));
        press(board, "UP");
        check(sent.size() == 4 && pixel(board, 64, 64) == emptySelf,
                "player departure clears the board and drops subsequent game state");

        receive(lobby, "{\"type\":\"GAME_START\",\"roundId\":2}");
        receive(lobby, state(2, 1, 6, "ACTIVE", true));
        press(board, "LEFT");
        check(sent.size() == 5 && pixel(board, 64, 64) == self, "new round accepts its first sequence");
        receive(lobby, "{\"type\":\"ROUND_RESULT\",\"roundId\":2}");
        receive(lobby, state(2, 2, 10, "ACTIVE", false));
        press(board, "LEFT");
        check(sent.size() == 5 && pixel(board, 64, 64) == self, "round result stops input and state updates");
    }

    private static String state(long round, long sequence, int x, String status, boolean visible) {
        return "{\"type\":\"GAME_STATE\",\"phase\":\"PLAYING\",\"remainingMs\":180000,\"roundId\":" + round
                + ",\"sequence\":" + sequence + ",\"self\":{\"id\":1,\"role\":\"PACMAN\",\"x\":" + x
                + ",\"y\":6,\"direction\":\"LEFT\",\"status\":\"" + status + "\"},\"opponent\":"
                + (visible ? "{\"id\":2,\"role\":\"GHOST\",\"x\":16,\"y\":6,\"direction\":\"UP\",\"status\":\"ACTIVE\"}" : "null")
                + "}";
    }

    private static void receive(LobbyPanel lobby, String json) {
        lobby.receive(ServerJson.parse(json));
    }

    static JComponent findBinding(Container parent, KeyStroke key) {
        for (Component component : parent.getComponents()) {
            if (!component.isVisible() || !(component instanceof JComponent)) continue;
            JComponent child = (JComponent) component;
            if (child.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(key) != null) return child;
            JComponent match = findBinding(child, key);
            if (match != null) return match;
        }
        return null;
    }

    static void press(JComponent board, String direction) {
        Object binding = board.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(direction));
        Action action = board.getActionMap().get(binding);
        check(action != null, "move action exists for " + direction);
        action.actionPerformed(new ActionEvent(board, ActionEvent.ACTION_PERFORMED, direction));
    }

    static int pixel(JComponent board, int x, int y) {
        BufferedImage image = new BufferedImage(board.getWidth(), board.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        board.paint(graphics);
        graphics.dispose();
        return image.getRGB(x, y);
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
