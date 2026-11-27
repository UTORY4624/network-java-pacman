package pacman.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** 실제 Swing 컴포넌트를 캡처한다. 서버에 연결하거나 요청을 전송하지 않는다. */
public class DesignPreview {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ConnectionWindow connection = new ConnectionWindow();
            JFrame lobbyFrame = new JFrame();
            try {
                capture(connection.getContentPane(), "connection", 720, 620);
                LobbyPanel lobby = new LobbyPanel(1L, command -> {}, () -> {});
                Map<String,Object> me = player(1L, "PACMAN");
                Map<String,Object> other = player(2L, "GHOST");
                Map<String,Object> message = new LinkedHashMap<>();
                message.put("type", "LOBBY");
                message.put("phase", "LOBBY");
                message.put("players", Arrays.asList(me,other));
                lobby.receive(message);
                lobbyFrame.setContentPane(lobby);
                lobbyFrame.pack();
                capture(lobby, "lobby", 720, 760);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally { connection.dispose(); lobbyFrame.dispose(); }
        });
    }

    private static Map<String,Object> player(long id, String role) {
        Map<String,Object> p = new LinkedHashMap<>();
        p.put("id",id); p.put("role",role); p.put("ready",false);
        return p;
    }

    private static void layout(Container c) {
        c.doLayout();
        for(Component child:c.getComponents()) if(child instanceof Container) layout((Container)child);
    }

    private static void capture(Container panel, String name, int width, int height) throws Exception {
        panel.setSize(width,height);
        layout(panel);
        BufferedImage img=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        panel.printAll(g);
        g.dispose();
        Path dir=Paths.get("build/previews");
        Files.createDirectories(dir);
        ImageIO.write(img,"png",dir.resolve(name+".png").toFile());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ImageIO.write(img,"png",bytes);
        System.out.println("PREVIEW_" + name + "=" + Base64.getEncoder().encodeToString(bytes.toByteArray()));
    }
}
