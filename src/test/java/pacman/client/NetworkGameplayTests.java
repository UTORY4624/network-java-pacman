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
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public class NetworkGameplayTests {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(NetworkGameplayTests::checkGameplay);
        SwingUtilities.invokeAndWait(NetworkGameplayTests::checkGameStatus);
        System.out.println("PASS / gameplay input, positions, food, clock, lives and player status");
    }

    private static void checkGameStatus() {
        List<String> sent = new ArrayList<>();
        LobbyPanel pacman = new LobbyPanel(1, sent::add, () -> {});
        LobbyPanel ghost = new LobbyPanel(2, sent::add, () -> {});
        LobbyPanel[] clients = {pacman, ghost};
        JComponent[] boards = new JComponent[clients.length];
        int pelletX = 90, pelletY = 130, powerX = 104, powerY = 128;
        for (int i = 0; i < clients.length; i++) {
            receive(clients[i], "{\"type\":\"GAME_START\",\"roundId\":1}");
            boards[i] = findBinding(clients[i], KeyStroke.getKeyStroke("UP"));
            boards[i].setSize(boards[i].getPreferredSize());
        }
        int emptyPellet = pixel(boards[0], pelletX, pelletY);
        int emptyPower = pixel(boards[0], powerX, powerY);
        String initialFood = "{\"pellets\":[[10,15]],\"powerItems\":[[12,15]]}";
        String noFood = "{\"pellets\":[],\"powerItems\":[]}";
        String normalPacman = player(1, "PACMAN", 6, "ACTIVE", ",\"lives\":2,\"powerRemainingMs\":0");
        String normalGhost = player(2, "GHOST", 16, "ACTIVE", "");
        receive(pacman, gameState(1, 1, 180000, normalPacman, normalGhost, initialFood));
        receive(ghost, gameState(1, 1, 180000, normalGhost, normalPacman, initialFood));
        for (int i = 0; i < clients.length; i++) {
            check(hasLabel(clients[i], "남은 시간 · 03:00") && hasLabel(clients[i], "팩맨 목숨 · 2"),
                    "both clients show the initial server time and lives");
            check(pixel(boards[i], pelletX, pelletY) != emptyPellet && pixel(boards[i], powerX, powerY) != emptyPower,
                    "both clients show the food from the server");
        }

        String poweredPacman = player(1, "PACMAN", 6, "ACTIVE", ",\"lives\":2,\"powerRemainingMs\":4100");
        String returningGhost = player(2, "GHOST", 16, "RETURNING", "");
        receive(pacman, gameState(1, 2, 179000, poweredPacman, returningGhost, noFood));
        receive(ghost, gameState(1, 2, 179000, returningGhost, poweredPacman, noFood));
        for (int i = 0; i < clients.length; i++) {
            check(pixel(boards[i], pelletX, pelletY) == emptyPellet && pixel(boards[i], powerX, powerY) == emptyPower,
                    "both clients remove collected food from the latest snapshot");
            check(hasLabel(clients[i], "파워 · 5초") && hasLabel(clients[i], "유령 복귀 대기 중"),
                    "both clients show the server power and return status");
        }
        press(boards[1], "UP");
        press(boards[0], "LEFT");
        check(sent.equals(Arrays.asList("MOVE LEFT")), "returning ghost waits while Pacman can still move");

        String respawningPacman = player(1, "PACMAN", 10, "RESPAWNING", ",\"lives\":1,\"powerRemainingMs\":0");
        String respawningGhost = player(2, "GHOST", 16, "RESPAWNING", "");
        receive(pacman, gameState(1, 3, 178000, respawningPacman, respawningGhost, noFood));
        receive(pacman, gameState(1, 4, 178000, respawningPacman, respawningGhost, noFood));
        receive(pacman, gameState(1, 2, 179000, poweredPacman, returningGhost, initialFood));
        check(hasLabel(pacman, "팩맨 목숨 · 1") && hasLabel(pacman, "남은 시간 · 02:58")
                        && hasLabel(pacman, "파워 · 없음") && hasLabel(pacman, "팩맨 재배치 대기 중"),
                "contact updates lives and wait status while an older snapshot cannot replace them");
        check(pixel(boards[0], pelletX, pelletY) == emptyPellet, "stale state and respawn do not restore food");

        receive(ghost, gameState(1, 3, 178000, normalGhost, "null", noFood));
        check(!hasLabel(ghost, "팩맨 목숨 · 2") && !hasLabel(ghost, "파워 · 5초")
                        && !hasLabel(ghost, "유령 복귀 대기 중") && hasLabel(ghost, "남은 시간 · 02:58"),
                "active state clears the wait and a hidden Pacman does not leave stale data");
        receive(pacman, "{\"type\":\"PLAYER_LEFT\",\"playerId\":2}");
        receive(pacman, "{\"type\":\"GAME_START\",\"roundId\":2}");
        receive(pacman, gameState(1, 5, 177000, poweredPacman, normalGhost, initialFood));
        check(!hasLabel(pacman, "팩맨 재배치 대기 중") && !hasLabel(pacman, "팩맨 목숨 · 1")
                        && pixel(boards[0], pelletX, pelletY) == emptyPellet,
                "new round waits for its own game info and food");
        receive(pacman, gameState(2, 1, 180000, normalPacman, normalGhost, initialFood));
        check(hasLabel(pacman, "남은 시간 · 03:00") && hasLabel(pacman, "팩맨 목숨 · 2")
                        && pixel(boards[0], pelletX, pelletY) != emptyPellet,
                "new round sets status and food from its first snapshot");
    }

    private static String gameState(long round, long sequence, long time, String self, String opponent, String food) {
        return "{\"type\":\"GAME_STATE\",\"phase\":\"PLAYING\",\"roundId\":" + round + ",\"sequence\":" + sequence
                + ",\"remainingMs\":" + time + ",\"self\":" + self + ",\"opponent\":" + opponent + ",\"food\":" + food + "}";
    }

    private static String player(long id, String role, int x, String status, String extraFields) {
        return "{\"id\":" + id + ",\"role\":\"" + role + "\",\"x\":" + x + ",\"y\":6,\"direction\":\"LEFT\",\"status\":\""
                + status + "\"" + extraFields + "}";
    }

    private static boolean hasLabel(Container parent, String text) {
        for (Component component : parent.getComponents()) {
            if (!component.isVisible()) continue;
            if (component instanceof JLabel && text.equals(((JLabel) component).getText())) return true;
            if (component instanceof Container && hasLabel((Container) component, text)) return true;
        }
        return false;
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
