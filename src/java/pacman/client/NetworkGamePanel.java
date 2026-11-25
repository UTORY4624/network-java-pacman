package pacman.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

final class NetworkGamePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int TILE_SIZE = 8;
    private static final int PLAYER_SIZE = 4 * TILE_SIZE;
    private static final Color WALL = new Color(41, 70, 157);
    private static final Color DOOR = new Color(214, 151, 194);
    private static final Color PELLET = new Color(255, 217, 178);
    private static final Color POWER_ITEM = new Color(112, 230, 224);
    private static final Color PACMAN = new Color(250, 215, 61);
    private static final Color GHOST = new Color(237, 85, 111);

    private final long playerId;
    private final List<String[]> map = loadMap();
    private final GameStatusPanel status = new GameStatusPanel();
    private Long roundId;
    private long sequence;
    private boolean canMove;
    private Player self;
    private Player opponent;
    private List<Point> pellets = Collections.emptyList();
    private List<Point> powerItems = Collections.emptyList();

    NetworkGamePanel(long playerId, Consumer<String> send) {
        this.playerId = playerId;
        setBackground(new Color(15, 19, 35));
        setPreferredSize(map.isEmpty() ? new Dimension(448, 496)
                : new Dimension(map.get(0).length * TILE_SIZE, map.size() * TILE_SIZE));
        for (String direction : new String[] {"UP", "DOWN", "LEFT", "RIGHT"}) {
            String command = "MOVE " + direction;
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(direction), command);
            getActionMap().put(command, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    if (canMove) send.accept(command);
                }
            });
        }
    }

    JPanel statusPanel() {
        return status;
    }

    void start(long id) {
        clear();
        roundId = id;
    }

    void clear() {
        roundId = null;
        sequence = 0;
        canMove = false;
        self = null;
        opponent = null;
        pellets = Collections.emptyList();
        powerItems = Collections.emptyList();
        status.clear();
        repaint();
    }

    void finish(Object id) {
        if (roundId != null && roundId.equals(id)) {
            roundId = null;
            canMove = false;
        }
    }

    void receive(Map<String, Object> message) {
        if (roundId == null || !roundId.equals(message.get("roundId"))) return;
        Object receivedSequence = message.get("sequence");
        if (!(receivedSequence instanceof Long) || (Long) receivedSequence <= sequence) return;

        Object receivedSelf = message.get("self");
        if (!(receivedSelf instanceof Map<?, ?>)) return;
        Map<?, ?> selfData = (Map<?, ?>) receivedSelf;
        if (!Long.valueOf(playerId).equals(selfData.get("id"))) return;
        Player nextSelf = Player.from(selfData);
        if (nextSelf == null) return;

        sequence = (Long) receivedSequence;
        self = nextSelf;
        Object receivedOpponent = message.get("opponent");
        Map<?, ?> opponentData = receivedOpponent instanceof Map<?, ?> ? (Map<?, ?>) receivedOpponent : null;
        opponent = opponentData == null ? null : Player.from(opponentData);
        Object food = message.get("food");
        Map<?, ?> foodData = food instanceof Map<?, ?> ? (Map<?, ?>) food : Collections.emptyMap();
        pellets = foodPositions(foodData.get("pellets"));
        powerItems = foodPositions(foodData.get("powerItems"));
        status.update(message.get("remainingMs"), selfData, opponentData);
        canMove = "PLAYING".equals(message.get("phase")) && "ACTIVE".equals(selfData.get("status"))
                && !Long.valueOf(0).equals(message.get("remainingMs"))
                && !Long.valueOf(0).equals(selfData.get("lives"))
                && (opponentData == null || !Long.valueOf(0).equals(opponentData.get("lives")));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (map.isEmpty()) {
            graphics.setColor(Color.WHITE);
            graphics.drawString("맵 파일을 찾을 수 없어요.", 12, 20);
            return;
        }

        Dimension board = getPreferredSize();
        double scale = Math.min((double) getWidth() / board.width, (double) getHeight() / board.height);
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate((getWidth() - board.width * scale) / 2, (getHeight() - board.height * scale) / 2);
        g.scale(scale, scale);
        for (int y = 0; y < map.size(); y++) {
            String[] row = map.get(y);
            for (int x = 0; x < row.length; x++) {
                if ("x".equals(row[x])) {
                    g.setColor(WALL);
                    g.fillRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                } else if ("-".equals(row[x])) {
                    g.setColor(DOOR);
                    g.fillRect(x * TILE_SIZE, y * TILE_SIZE + 3, TILE_SIZE, 2);
                }
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawFood(g, pellets, TILE_SIZE, 4, PELLET);
        drawFood(g, powerItems, 0, 2 * TILE_SIZE, POWER_ITEM);
        if (opponent != null) opponent.draw(g, false);
        if (self != null) self.draw(g, true);
        g.dispose();
    }

    private List<Point> foodPositions(Object value) {
        if (!(value instanceof List<?>) || map.isEmpty()) return Collections.emptyList();
        List<Point> positions = new ArrayList<>();
        for (Object entry : (List<?>) value) {
            if (!(entry instanceof List<?>)) continue;
            List<?> pair = (List<?>) entry;
            if (pair.size() != 2 || !(pair.get(0) instanceof Long) || !(pair.get(1) instanceof Long)) continue;
            long x = (Long) pair.get(0), y = (Long) pair.get(1);
            if (y >= 0 && y < map.size() && x >= 0 && x < map.get((int) y).length) {
                positions.add(new Point((int) x * TILE_SIZE, (int) y * TILE_SIZE));
            }
        }
        return positions;
    }

    private static void drawFood(Graphics2D graphics, List<Point> positions, int offset, int size, Color color) {
        graphics.setColor(color);
        for (Point position : positions) {
            graphics.fillOval(position.x + offset, position.y + offset, size, size);
        }
    }

    private static List<String[]> loadMap() {
        InputStream input = NetworkGamePanel.class.getResourceAsStream("/level/level.csv");
        if (input == null) return Collections.emptyList();
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) rows.add(line.split(";"));
            return rows;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static final class Player {
        private final int x;
        private final int y;
        private final Color color;
        private final Object direction;

        private Player(int x, int y, Color color, Object direction) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.direction = direction;
        }

        static Player from(Map<?, ?> data) {
            Object x = data.get("x"), y = data.get("y"), role = data.get("role");
            if (!(x instanceof Long) || !(y instanceof Long)) return null;
            if (!"PACMAN".equals(role) && !"GHOST".equals(role)) return null;
            return new Player(((Long) x).intValue() * TILE_SIZE, ((Long) y).intValue() * TILE_SIZE,
                    "PACMAN".equals(role) ? PACMAN : GHOST, data.get("direction"));
        }

        void draw(Graphics2D g, boolean isSelf) {
            g.setColor(color);
            g.fillOval(x + 2, y + 2, PLAYER_SIZE - 4, PLAYER_SIZE - 4);
            if (isSelf) {
                g.setColor(Color.WHITE);
                g.drawOval(x + 2, y + 2, PLAYER_SIZE - 4, PLAYER_SIZE - 4);
            }
            int centerX = x + PLAYER_SIZE / 2, centerY = y + PLAYER_SIZE / 2;
            int dx = "RIGHT".equals(direction) ? 8 : "LEFT".equals(direction) ? -8 : 0;
            int dy = "DOWN".equals(direction) ? 8 : "UP".equals(direction) ? -8 : 0;
            g.setColor(Color.BLACK);
            g.fillOval(centerX + dx - 2, centerY + dy - 2, 5, 5);
        }
    }
}
