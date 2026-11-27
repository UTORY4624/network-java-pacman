package pacman.client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ConnectionWindow extends JFrame {
    private static final long serialVersionUID = 1L;
    private final JTextField hostField = new JTextField("127.0.0.1", 20);
    private final JTextField portField = new JTextField("5555", 20);
    private final JButton connectButton = new JButton("접속하기");
    private final JLabel statusLabel = new JLabel("서버 주소와 포트를 입력하고 접속해주세요.");
    private final CardLayout cards = new CardLayout();
    private final JPanel screens = new JPanel(cards);
    private ServerConnection connection;
    private LobbyPanel lobby;

    public ConnectionWindow() {
        super("잡아봐 · 2인 대전");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        JPanel panel = ArcadeTheme.backdrop(new BorderLayout(0, 30));
        panel.setBorder(BorderFactory.createEmptyBorder(36, 40, 30, 40));
        panel.setPreferredSize(new java.awt.Dimension(720, 620));
        panel.add(ArcadeTheme.hero(false), BorderLayout.NORTH);

        JPanel center = ArcadeTheme.plain(new BorderLayout(0, 18));
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBackground(ArcadeTheme.PANEL);
        fields.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        GridBagConstraints layout = new GridBagConstraints();
        layout.insets = new Insets(8, 0, 8, 16);
        layout.anchor = GridBagConstraints.WEST;
        layout.gridx = 0;
        layout.gridy = 0;
        fields.add(ArcadeTheme.label("서버 주소", 14, ArcadeTheme.MUTED), layout);
        layout.gridy = 1;
        fields.add(ArcadeTheme.label("포트", 14, ArcadeTheme.MUTED), layout);
        layout.gridx = 1;
        layout.gridy = 0;
        layout.weightx = 1;
        layout.fill = GridBagConstraints.HORIZONTAL;
        layout.insets = new Insets(8, 0, 8, 0);
        ArcadeTheme.style(hostField);
        ArcadeTheme.style(portField);
        fields.add(hostField, layout);
        layout.gridy = 1;
        fields.add(portField, layout);
        layout.gridy = 2;
        layout.gridx = 0;
        layout.gridwidth = 2;
        layout.insets = new Insets(20, 0, 0, 0);
        ArcadeTheme.button(connectButton, ArcadeTheme.YELLOW);
        fields.add(connectButton, layout);
        center.add(ArcadeTheme.label("01  /  대전 서버에 접속", 18, ArcadeTheme.TEXT), BorderLayout.NORTH);
        center.add(fields, BorderLayout.CENTER);
        center.add(ArcadeTheme.label("서버를 실행한 친구의 IP로 접속하세요", 13, ArcadeTheme.MUTED), BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        JPanel footer = ArcadeTheme.plain(new java.awt.GridLayout(0, 1, 0, 12));
        statusLabel.setForeground(ArcadeTheme.CYAN);
        statusLabel.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
        footer.add(statusLabel);
        footer.add(ArcadeTheme.label("2 PLAYERS    /    180 SECONDS    /    ONE WINNER", 11, ArcadeTheme.MUTED));
        panel.add(footer, BorderLayout.SOUTH);
        screens.add(panel, "connect");
        setContentPane(screens);
        getRootPane().setDefaultButton(connectButton);

        connectButton.addActionListener(event -> clickConnect());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                disconnect();
            }
        });
        pack();
        setMinimumSize(new java.awt.Dimension(640, 600));
        setLocationRelativeTo(null);
    }

    private void clickConnect() {
        if (connection != null) {
            disconnect();
            return;
        }

        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            statusLabel.setText("서버 주소를 입력해주세요.");
            hostField.requestFocusInWindow();
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            statusLabel.setText("포트는 1~65535 사이 숫자로 입력해주세요.");
            portField.requestFocusInWindow();
            return;
        }

        ServerConnection attempt = new ServerConnection();
        connection = attempt;
        setFieldsEnabled(false);
        connectButton.setText("접속 취소");
        statusLabel.setText("서버에 접속하는 중이에요...");
        attempt.connect(host, port, new ServerConnection.Listener() {
            @Override
            public void connected() {
                // 화면 변경은 Swing 화면 스레드에서 처리
                SwingUtilities.invokeLater(() -> {
                    if (connection != attempt) return;
                    connectButton.setText("연결 해제");
                    statusLabel.setText("서버에 접속했어요.");
                });
            }

            @Override
            public void messageReceived(Map<String, Object> message) {
                SwingUtilities.invokeLater(() -> {
                    if (connection != attempt) return;
                    if ("WELCOME".equals(message.get("type"))) {
                        showLobby((Long) message.get("playerId"), attempt);
                    } else if (lobby != null) {
                        lobby.receive(message);
                    }
                });
            }

            @Override
            public void disconnected(String message) {
                SwingUtilities.invokeLater(() -> {
                    // 취소한 접속의 응답이 새 접속 화면을 바꾸지 않게 확인
                    if (connection != attempt) return;
                    showConnect(message);
                });
            }
        });
    }

    private void disconnect() {
        if (connection != null) {
            ServerConnection previous = connection;
            connection = null;
            previous.close();
        }
        showConnect("연결을 해제했어요.");
    }

    private void showLobby(long playerId, ServerConnection attempt) {
        lobby = new LobbyPanel(playerId, attempt::send, this::disconnect);
        screens.add(lobby, "lobby");
        cards.show(screens, "lobby");
        getRootPane().setDefaultButton(null);
        setSize(Math.max(getWidth(), screens.getPreferredSize().width),
                Math.max(getHeight(), screens.getPreferredSize().height + getInsets().top + getInsets().bottom));
    }

    private void showConnect(String message) {
        connection = null;
        cards.show(screens, "connect");
        if (lobby != null) {
            screens.remove(lobby);
            lobby = null;
        }
        getRootPane().setDefaultButton(connectButton);
        setFieldsEnabled(true);
        connectButton.setText("접속하기");
        statusLabel.setText(message);
    }

    private void setFieldsEnabled(boolean enabled) {
        hostField.setEnabled(enabled);
        portField.setEnabled(enabled);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // 기본 테마로도 실행할 수 있게 하기
            }
            new ConnectionWindow().setVisible(true);
        });
    }
}
