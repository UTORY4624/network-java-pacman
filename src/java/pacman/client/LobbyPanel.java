package pacman.client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class LobbyPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String LOBBY = "LOBBY";
    private static final String PLAYING = "PLAYING";

    private final CardLayout cards = new CardLayout();
    private final JPanel screens = new JPanel(cards);
    private final JLabel self = new JLabel("나 · 역할 미선택");
    private final JLabel opponent = new JLabel("상대 · 접속 대기");
    private final JLabel notice = new JLabel("대기실 정보를 받는 중이에요.");
    private final JLabel gameTitle = new JLabel();
    private final JLabel gameRole = new JLabel();
    private final NetworkGamePanel board;
    private final JButton pacman = new JButton("팩맨 선택");
    private final JButton ghost = new JButton("유령 선택");
    private final JButton ready = new JButton("준비");
    private final long playerId;
    private String role;
    private boolean readyConfirmed;
    private String pendingRole;
    private boolean readyPending;
    private boolean inLobby;
    private boolean supportsReady;

    LobbyPanel(long playerId, Consumer<String> send, Runnable disconnect) {
        super(new BorderLayout(0, 16));
        this.playerId = playerId;
        board = new NetworkGamePanel(playerId, send);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        JPanel lobby = new JPanel(new GridLayout(0, 1, 0, 8));
        lobby.add(new JLabel("역할 선택과 준비"));
        lobby.add(self);
        lobby.add(opponent);
        JPanel actions = new JPanel(new GridLayout(1, 0, 8, 0));
        actions.add(pacman);
        actions.add(ghost);
        actions.add(ready);
        lobby.add(actions);
        screens.add(lobby, LOBBY);

        JPanel game = new JPanel(new BorderLayout(0, 8));
        JPanel labels = new JPanel(new GridLayout(0, 1, 0, 4));
        labels.add(gameTitle);
        labels.add(gameRole);
        JPanel heading = new JPanel(new BorderLayout(0, 4));
        heading.add(labels, BorderLayout.NORTH);
        heading.add(board.statusPanel(), BorderLayout.CENTER);
        game.add(heading, BorderLayout.NORTH);
        game.add(board, BorderLayout.CENTER);
        screens.add(game, PLAYING);
        add(screens, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.add(notice, BorderLayout.CENTER);
        JButton leave = new JButton("연결 해제");
        leave.addActionListener(event -> disconnect.run());
        footer.add(leave, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        pacman.addActionListener(event -> selectRole("PACMAN", send));
        ghost.addActionListener(event -> selectRole("GHOST", send));
        ready.addActionListener(event -> {
            readyPending = true;
            notice.setText("준비 상태를 보내는 중이에요.");
            updateButtons();
            send.accept("READY");
        });
        updateButtons();
    }

    void receive(Map<String, Object> message) {
        Object type = message.get("type");
        if ("LOBBY".equals(type)) {
            if (!message.containsKey("phase") || LOBBY.equals(message.get("phase"))) {
                updateLobby(message.get("players"));
            }
        } else if ("GAME_START".equals(type)) {
            startGame(message);
        } else if ("GAME_STATE".equals(type)) {
            board.receive(message);
        } else if ("ROUND_RESULT".equals(type)) {
            board.finish(message.get("roundId"));
        } else if ("PLAYER_LEFT".equals(type)) {
            board.clear();
            opponent.setText("상대 · 접속 대기");
            notice.setText("상대가 연결을 종료했어요.");
            readyConfirmed = false;
            pendingRole = null;
            readyPending = false;
            inLobby = false;
            self.setText(playerLabel("나", role, false));
            cards.show(screens, LOBBY);
            updateButtons();
        } else if ("ERROR".equals(type)) {
            pendingRole = null;
            readyPending = false;
            notice.setText("ROLE_TAKEN".equals(message.get("code"))
                    ? "상대가 이미 선택한 역할이에요. 다른 역할을 선택해 주세요."
                    : "요청을 처리하지 못했어요. 다시 시도해 주세요.");
            updateButtons();
        }
    }

    private void selectRole(String selected, Consumer<String> send) {
        pendingRole = selected;
        notice.setText("역할 선택을 보내는 중이에요.");
        updateButtons();
        send.accept("ROLE " + selected);
    }

    private void updateLobby(Object players) {
        if (!(players instanceof List<?>)) return;
        Map<?, ?> me = null;
        Map<?, ?> other = null;
        for (Object item : (List<?>) players) {
            if (!(item instanceof Map<?, ?>)) continue;
            Map<?, ?> player = (Map<?, ?>) item;
            if (Long.valueOf(playerId).equals(player.get("id"))) me = player;
            else other = player;
        }
        if (me == null) return;

        role = roleOf(me);
        supportsReady = me.get("ready") instanceof Boolean;
        readyConfirmed = Boolean.TRUE.equals(me.get("ready"));
        self.setText(playerLabel("나", role, readyConfirmed));
        opponent.setText(other == null ? "상대 · 접속 대기"
                : playerLabel("상대", roleOf(other), Boolean.TRUE.equals(other.get("ready"))));

        if (role != null && role.equals(pendingRole)) {
            pendingRole = null;
            notice.setText(supportsReady ? "준비 버튼을 눌러 주세요." : "서버에서 준비 기능을 아직 지원하지 않아요.");
        }
        if (readyConfirmed && readyPending) {
            readyPending = false;
            notice.setText("상대가 준비하면 게임을 시작해요.");
        }
        if (!inLobby && "대기실 정보를 받는 중이에요.".equals(notice.getText())) {
            notice.setText("역할을 선택해 주세요.");
        }
        board.clear();
        inLobby = true;
        cards.show(screens, LOBBY);
        updateButtons();
    }

    private void startGame(Map<String, Object> message) {
        if (!(message.get("roundId") instanceof Long)) return;
        board.start((Long) message.get("roundId"));
        inLobby = false;
        pendingRole = null;
        readyPending = false;
        updateButtons();
        gameTitle.setText(message.get("roundId") + "라운드 게임 시작");
        gameRole.setText("내 역할 · " + roleLabel(role));
        notice.setText("서버에서 게임 시작을 알렸어요.");
        cards.show(screens, PLAYING);
    }

    private void updateButtons() {
        boolean canChoose = inLobby && pendingRole == null && !readyPending;
        pacman.setEnabled(canChoose && !"PACMAN".equals(role));
        ghost.setEnabled(canChoose && !"GHOST".equals(role));
        ready.setEnabled(canChoose && supportsReady && role != null && !readyConfirmed);
    }

    private static String roleOf(Map<?, ?> player) {
        Object value = player.get("role");
        return "PACMAN".equals(value) ? "PACMAN" : "GHOST".equals(value) ? "GHOST" : null;
    }

    private static String playerLabel(String who, String role, boolean ready) {
        return who + " · " + roleLabel(role) + " · " + (ready ? "준비 완료" : "준비 전");
    }

    private static String roleLabel(String role) {
        if ("PACMAN".equals(role)) return "팩맨";
        if ("GHOST".equals(role)) return "유령";
        return "역할 미선택";
    }
}
