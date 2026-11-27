package pacman.client;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicButtonUI;

/** Shared Swing styling; no resources, timers or network dependencies. */
final class ArcadeTheme {
    static final Color BG = new Color(11, 15, 29);
    static final Color PANEL = new Color(21, 28, 48);
    static final Color TEXT = new Color(236, 241, 255);
    static final Color MUTED = new Color(158, 174, 203);
    static final Color YELLOW = new Color(255, 216, 67);
    static final Color PINK = new Color(255, 107, 149);
    static final Color CYAN = new Color(82, 222, 219);

    private ArcadeTheme() { }

    static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.DIALOG, Font.BOLD, size));
        label.setForeground(color);
        return label;
    }

    static JPanel plain(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    static void style(Component c) {
        if (c instanceof NetworkGamePanel) return;
        c.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        c.setForeground(TEXT);
        if (c instanceof JPanel) ((JPanel) c).setOpaque(false);
        if (c instanceof JTextField) {
            JTextField f = (JTextField) c;
            f.setOpaque(true);
            f.setBackground(PANEL);
            f.setCaretColor(YELLOW);
            f.setDisabledTextColor(MUTED);
            f.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(65, 82, 115)),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        } else if (c instanceof JButton) {
            button((JButton) c, CYAN);
        }
        if (c instanceof Container) for (Component child : ((Container) c).getComponents()) style(child);
    }

    static void button(JButton b, Color accent) {
        b.setUI(new BasicButtonUI() {
            @Override public void paint(Graphics g, JComponent component) {
                Graphics2D p = (Graphics2D) g.create();
                p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean selected = Boolean.TRUE.equals(b.getClientProperty("chosen"));
                boolean active = b.isEnabled();
                p.setColor(selected || (active && b.getModel().isRollover())
                        ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 42) : PANEL);
                p.fillRoundRect(0, 0, b.getWidth()-1, b.getHeight()-1, 14, 14);
                p.setStroke(new BasicStroke(selected || b.hasFocus() ? 3 : 1));
                p.setColor(selected || active ? accent : new Color(59, 71, 94));
                p.drawRoundRect(2, 2, b.getWidth()-5, b.getHeight()-5, 12, 12);
                if (b.getModel().isPressed() && active) {
                    p.setColor(new Color(255,255,255,24));
                    p.fillRoundRect(2,2,b.getWidth()-5,b.getHeight()-5,12,12);
                }
                p.dispose();
                super.paint(g, component);
            }
            @Override protected void paintText(Graphics g, AbstractButton button, Rectangle rect, String text) {
                g.setFont(button.getFont());
                g.setColor(button.isEnabled() ? TEXT : MUTED);
                g.drawString(text, rect.x, rect.y + g.getFontMetrics().getAscent());
            }
        });
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));
        b.setFont(new Font(Font.DIALOG, Font.BOLD, 16));
        b.setForeground(TEXT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    static JPanel backdrop(LayoutManager layout) {
        return new JPanel(layout) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D p = (Graphics2D) g.create();
                p.setPaint(new GradientPaint(0,0,BG,getWidth(),getHeight(),new Color(20,29,53)));
                p.fillRect(0,0,getWidth(),getHeight());
                p.setColor(new Color(36,48,73));
                for (int y=20;y<getHeight();y+=28) for (int x=20;x<getWidth();x+=28) p.fillRect(x,y,2,2);
                p.dispose();
            }
        };
    }

    static JPanel hero(boolean compact) {
        JPanel hero = plain(new BorderLayout(0, 10));
        JPanel words = plain(new GridLayout(0,1,0,4));
        words.add(label("2 PLAYER  /  MAZE CHASE", 12, CYAN));
        words.add(label("잡아봐", compact ? 36 : 52, YELLOW));
        words.add(label(compact ? "역할을 고르고, 추격전을 시작하세요" : "도망칠 것인가, 쫓을 것인가", 16, TEXT));
        hero.add(words, BorderLayout.CENTER);
        JLabel art = new JLabel(new CharacterIcon(false, compact ? 70 : 100));
        art.setBorder(BorderFactory.createEmptyBorder(0,12,0,16));
        hero.add(art, BorderLayout.EAST);
        return hero;
    }

    static JPanel roleCard(JButton button, boolean ghost) {
        JPanel card = new JPanel(new BorderLayout(0,12));
        card.setBackground(PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(53,66,96)),
                BorderFactory.createEmptyBorder(20,18,18,18)));
        JLabel icon = new JLabel(new CharacterIcon(ghost, 76), SwingConstants.CENTER);
        card.add(icon, BorderLayout.NORTH);
        JPanel copy = plain(new GridLayout(0,1,0,6));
        JLabel title = label(ghost ? "GHOST" : "PACMAN", 22, ghost ? PINK : YELLOW);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        copy.add(title);
        JLabel description = label(ghost ? "추격하고 길목을 막아라" : "먹이를 모으고 끝까지 살아남아라", 12, MUTED);
        description.setHorizontalAlignment(SwingConstants.CENTER);
        copy.add(description);
        card.add(copy, BorderLayout.CENTER);
        button(button, ghost ? PINK : YELLOW);
        card.add(button, BorderLayout.SOUTH);
        return card;
    }

    private static final class CharacterIcon implements Icon {
        private final boolean ghost;
        private final int size;
        CharacterIcon(boolean ghost, int size) { this.ghost=ghost; this.size=size; }
        public int getIconWidth() { return size; }
        public int getIconHeight() { return size; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D p=(Graphics2D)g.create();
            p.translate(x,y);
            p.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            p.setColor(ghost ? PINK : YELLOW);
            if (ghost) {
                p.fillArc(4,4,size-8,size-8,0,180);
                p.fillRect(4,size/2,size-8,size/3);
                for(int i=0;i<3;i++) p.fillOval(4+i*(size-8)/3,size*2/3,(size-8)/3,size/4);
                p.setColor(Color.WHITE);
                p.fillOval(size/4,size/3,size/5,size/4);
                p.fillOval(size/2,size/3,size/5,size/4);
                p.setColor(BG);
                p.fillOval(size/3,size*2/5,size/12,size/9);
                p.fillOval(size*7/12,size*2/5,size/12,size/9);
            } else {
                p.fillArc(4,4,size-8,size-8,38,284);
                p.setColor(BG);
                p.fillOval(size/2,size/5,size/12,size/12);
            }
            p.dispose();
        }
    }
}
