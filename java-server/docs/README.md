# Java Server - TicTacToe Multiplayer System

## Directory Structure

### `src/`

Contains all source code for the server.

- `ClientSession.java`: Manages individual player sessions.
- `ConnectionPool.java`: Handles database connection pooling.
- `DatabaseInspector.java`: Utility for inspecting the database.
- `DatabaseManager.java`: Manages database operations.
- `GameService.java`: Implements game logic.
- `GameStateCache.java`: Caches game states for performance.
- `Main.java`: Entry point for the server.
- `Matchmaker.java`: Handles player matchmaking.
- `RestApiServer.java`: Manages REST API endpoints.
- `Room.java`: Represents a game room.
- `Server.java`: Core server logic.
- `TestRunner.java`: Runs unit tests.
- `TttRoom.java`: Specialized room for TicTacToe.
- `WebSocketNotifier.java`: Handles WebSocket notifications.

### `tests/`

Contains test scripts for various functionalities.

- `test_turn_logic.ps1`: Tests turn logic.
- `test_websocket_debug.ps1`: Debugs WebSocket functionality.
- `test_websocket_flow.ps1`: Tests WebSocket message flow.

### `database/`

Stores database files.

- `ttt_game.db`: Main database file.
- `ttt_game.db-shm`: Shared memory file for SQLite.
- `ttt_game.db-wal`: Write-ahead log file for SQLite.

### `build/`

Contains build artifacts and Gradle files.

- `build.gradle`: Gradle build configuration.
- `gradlew`, `gradlew.bat`: Gradle wrapper scripts.
- `settings.gradle`: Gradle settings file.

### `docs/`

Contains documentation files.

- `README.md`: This file.

## Running the Server

1. **Build the Project**

   ```bash
   ./gradlew build
   ```

2. **Run the Server**

   ```bash
   java -cp "build/libs/*" com.stanstate.ttt.Main
   ```

3. **Test the Server**
   Use the provided PowerShell scripts in the `tests/` directory to test various functionalities.

## Notes

- Ensure Java 11+ is installed.
- Modify `build.gradle` for additional dependencies if needed.