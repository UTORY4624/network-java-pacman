package pacman.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// 원본 맵은 그대로 읽고, 이미지나 게임 창은 만들지 않기
final class GameMap {
    private final char[][] tiles;
    final int width;
    final int height;
    final int pacmanX;
    final int pacmanY;
    final int ghostX;
    final int ghostY;

    private GameMap(char[][] tiles, int px, int py, int gx, int gy) {
        this.tiles = tiles;
        height = tiles.length;
        width = tiles[0].length;
        pacmanX = px;
        pacmanY = py;
        ghostX = gx;
        ghostY = gy;
    }

    static GameMap load() throws IOException {
        InputStream input = GameMap.class.getResourceAsStream("/level/level.csv");
        if (input == null) throw new IOException("맵 파일을 찾을 수 없어요: level/level.csv");
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    static GameMap read(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        List<char[]> rows = new ArrayList<>();
        int width = -1;
        int px = -1, py = -1, gx = -1, gy = -1;
        String line;
        while ((line = reader.readLine()) != null) {
            // CSV 끝의 세미콜론 하나는 새 칸으로 세지 않기
            if (line.endsWith(";")) line = line.substring(0, line.length() - 1);
            String[] cells = line.split(";", -1);
            if (width == -1) width = cells.length;
            if (cells.length != width) throw new IOException("맵의 줄마다 칸 수가 달라요.");
            char[] row = new char[width];
            for (int x = 0; x < width; x++) {
                if (cells[x].length() != 1 || "x .o-Pbpic".indexOf(cells[x].charAt(0)) < 0) {
                    throw new IOException("맵에 알 수 없는 기호가 있어요.");
                }
                char tile = cells[x].charAt(0);
                row[x] = tile;
                if (tile == 'P') {
                    if (px != -1) throw new IOException("팩맨 시작 위치가 여러 개예요.");
                    px = x; py = rows.size();
                } else if (tile == 'b') {
                    if (gx != -1) throw new IOException("유령 시작 위치가 여러 개예요.");
                    gx = x; gy = rows.size();
                }
            }
            rows.add(row);
        }
        if (px == -1 || gx == -1) throw new IOException("팩맨과 유령 시작 위치가 필요해요.");
        return new GameMap(rows.toArray(new char[rows.size()][]), px, py, gx, gy);
    }

    boolean contains(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    char tileAt(int x, int y) {
        if (!contains(x, y)) throw new IndexOutOfBoundsException("맵 밖 좌표예요.");
        return tiles[y][x];
    }

    boolean isWall(int x, int y) {
        return !contains(x, y) || tiles[y][x] == 'x';
    }

    boolean isGhostDoor(int x, int y) {
        return contains(x, y) && tiles[y][x] == '-';
    }
}
