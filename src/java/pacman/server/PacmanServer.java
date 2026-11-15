package pacman.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PacmanServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final List<ClientSession> clients = new ArrayList<>();
    private volatile boolean closed;
    private int nextPlayerId = 1;
    private int connectionVersion;
    private int roundId;
    private boolean gameRunning;

    public PacmanServer(String address, int port) throws IOException {
        serverSocket = new ServerSocket();
        try {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(address, port));
        } catch (IOException | RuntimeException e) {
            serverSocket.close();
            throw e;
        }
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void run() throws IOException {
        System.out.println("서버 시작 / 포트 " + getPort());
        try {
            while (!closed) {
                // 누가 접속할 때까지 여기서 기다리기
                Socket socket = serverSocket.accept();
                try {
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(30000);
                    if (!connect(socket)) {
                        String message = Json.encode(error("SERVER_FULL")) + "\n";
                        socket.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
                        socket.close();
                    }
                } catch (IOException e) {
                    socket.close();
                }
            }
        } catch (SocketException e) {
            if (!closed) throw e;
        } finally {
            close();
        }
    }

    private synchronized boolean connect(Socket socket) {
        // 두 명까지만 접속할 수 있게 하기
        if (closed || clients.size() >= 2) return false;

        ClientSession client = new ClientSession(nextPlayerId++, socket, this);
        clients.add(client);
        connectionVersion++;
        client.send(Json.object("type", "WELCOME", "protocol", 1,
                "playerId", client.id, "heartbeatMs", 10000));
        client.start();
        sendLobby();
        System.out.println(client.id + "번 접속 / 현재 " + clients.size() + "명");
        return true;
    }

    synchronized void command(ClientSession client, String message) {
        if (closed || !clients.contains(client)) return;
        String[] parts = message.trim().split("\\s+");

        if (parts[0].equals("ROLE")) {
            if (parts.length != 2) {
                client.send(error("BAD_COMMAND"));
                return;
            }
            selectRole(client, parts[1]);
        } else if (parts.length == 1 && parts[0].equals("READY")) {
            selectReady(client);
        } else if (parts.length == 1 && parts[0].equals("PING")) {
            client.send(Json.object("type", "PONG"));
        } else if (parts.length == 1 && parts[0].equals("QUIT")) {
            client.close();
        } else {
            client.send(error("BAD_COMMAND"));
        }
    }

    private void selectRole(ClientSession client, String role) {
        if (gameRunning) {
            client.send(error("INVALID_PHASE"));
            return;
        }
        if (!role.equals("PACMAN") && !role.equals("GHOST")) {
            client.send(error("INVALID_ROLE"));
            return;
        }

        // 동시에 선택해도 같은 역할이 겹치지 않게 확인
        for (ClientSession other : clients) {
            if (other != client && role.equals(other.role)) {
                client.send(error("ROLE_TAKEN"));
                return;
            }
        }

        client.role = role;
        // 역할을 다시 고르면 준비부터 다시 누르게 하기
        client.ready = false;
        sendLobby();
        System.out.println(client.id + "번 역할 선택 / " + role);
    }

    private void selectReady(ClientSession client) {
        if (gameRunning || client.role == null) {
            client.send(error("INVALID_PHASE"));
            return;
        }

        client.ready = true;
        sendLobby();
        startWhenReady();
    }

    private void startWhenReady() {
        // 두 명이 다른 역할을 고르고 준비했을 때만 게임 시작 알림 보내기
        if (gameRunning || clients.size() != 2) return;

        ClientSession first = clients.get(0);
        ClientSession second = clients.get(1);
        if (first.role == null || second.role == null || first.role.equals(second.role)
                || !first.ready || !second.ready) return;

        gameRunning = true;
        roundId++;
        broadcast(Json.object("type", "GAME_START", "roundId", roundId,
                "phase", "PLAYING", "remainingMs", 180000, "players", players()));
        System.out.println("게임 시작 / " + roundId + "라운드");
    }

    private void sendLobby() {
        broadcast(Json.object("type", "LOBBY", "phase", gameRunning ? "PLAYING" : "LOBBY",
                "players", players()));
    }

    private List<Map<String, Object>> players() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClientSession client : clients) {
            result.add(Json.object("id", client.id, "role", client.role, "ready", client.ready));
        }
        return result;
    }

    private void broadcast(Object message) {
        int version = connectionVersion;
        for (ClientSession client : new ArrayList<>(clients)) {
            // 보내는 중에 누가 나갔으면 새로 보낸 접속 목록을 쓰기
            if (version != connectionVersion) return;
            client.send(message);
        }
    }

    synchronized void disconnected(ClientSession client) {
        // 수신이랑 송신 쪽에서 동시에 종료돼도 한 번만 목록에서 빼기
        if (!clients.remove(client)) return;
        connectionVersion++;
        gameRunning = false;
        for (ClientSession remaining : clients) {
            // 상대가 나가면 새 상대와 다시 준비하도록 하기
            remaining.ready = false;
        }
        if (!closed) {
            broadcast(Json.object("type", "PLAYER_LEFT", "playerId", client.id));
            sendLobby();
        }
        System.out.println(client.id + "번 종료 / 현재 " + clients.size() + "명");
    }

    private Map<String, Object> error(String code) {
        return Json.object("type", "ERROR", "code", code);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.err.println("서버 소켓 종료 오류 / " + e.getMessage());
        }
        for (ClientSession client : new ArrayList<>(clients)) {
            client.close();
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length > 1) throw new IllegalArgumentException("사용법: java -jar pacman-server.jar [포트]");
            int port = args.length == 0 ? 5555 : Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) throw new IllegalArgumentException("포트 범위는 1~65535입니다.");

            try (PacmanServer server = new PacmanServer("0.0.0.0", port)) {
                Runtime.getRuntime().addShutdownHook(new Thread(server::close));
                server.run();
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("서버 실행 오류 / " + e.getMessage());
            System.exit(1);
        }
    }
}
