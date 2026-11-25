package pacman.server;

public final class GameUiScenario {
    private GameUiScenario() {
    }

    public static void placePlayersInContact(PacmanServer server) {
        synchronized (server) {
            GameState game = server.getGameState();
            game.pacman.requestedDirection = null;
            game.pacman.x = game.pacman.startX;
            game.pacman.y = game.pacman.startY;
            game.ghost.requestedDirection = null;
            game.ghost.x = game.pacman.x + 1;
            game.ghost.y = game.pacman.y;
        }
    }

    public static void placePacmanByPowerItem(PacmanServer server) {
        synchronized (server) {
            GameState game = server.getGameState();
            // The next server tick collects the original map's power item at (2,6).
            game.pacman.x = 1;
            game.pacman.y = 5;
        }
    }
}
