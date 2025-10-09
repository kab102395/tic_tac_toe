# TicTacToe Multiplayer Game

A real-time multiplayer TicTacToe game built for CS4800. Features a Java backend server with WebSocket communication and a Defold game client.

## Quick Start

### Server Setup

1. Navigate to the `java-server` folder
2. Run the server: `./gradlew run` (or `gradlew.bat run` on Windows)
3. Server starts on ports 4567 (HTTP) and 8080 (WebSocket)

### Client Setup

1. Install [Defold 1.11.1](https://defold.com/download/)
2. Open the `defold-client` folder in Defold
3. Click "Build & Run" or press Ctrl+R

## How to Play

1. Start the server first
2. Launch the game client
3. Enter your name when prompted
4. Wait for another player to join
5. Take turns clicking squares to place X or O
6. First to get 3 in a row wins!

## Testing the System

We've included several test scripts in `java-server/tests/`:

- **`StressTest.ps1`** - Tests server under load with multiple connections
- **`test_turn_logic.ps1`** - Validates game rules and turn handling  
- **`test_websocket_flow.ps1`** - Tests real-time communication
- **`test_websocket.html`** - Interactive WebSocket test page

Run any PowerShell script from the `java-server` directory.

## Project Structure

```text
├── defold-client/          # Game client (Defold/Lua)
│   ├── main/              # Main game scenes
│   ├── ttt/               # TicTacToe game logic
│   └── net/               # WebSocket networking
├── java-server/           # Backend server (Java 17)
│   ├── src/main/java/     # Server source code
│   ├── tests/             # Test scripts
│   └── docs/              # Technical documentation
└── database_er_diagram.html  # Interactive database schema
```

## Requirements

- **Server:** Java 17+, Gradle (included via wrapper)
- **Client:** Defold 1.11.1
- **Platform:** Windows, macOS, or Linux

## Architecture

- **Backend:** Java with WebSocket library, Spark framework, SQLite database
- **Frontend:** Defold game engine with WebSocket extension
- **Communication:** HTTP REST API for actions, WebSocket for real-time updates
- **Database:** SQLite with WAL mode for concurrent access

## Troubleshooting

**Server won't start?**

- Make sure Java 17+ is installed
- Check if ports 4567 and 8080 are available

**Client can't connect?**

- Verify the server is running first
- Check that WebSocket extension is enabled in Defold

**Game freezes?**

- Restart both server and client
- Check the server console for error messages

## Development Notes

This project demonstrates:

- Real-time multiplayer networking
- Session-based player management  
- Concurrent database access with connection pooling
- Cross-platform game development
- WebSocket communication patterns

Built for California State University, Stanislaus - CS4800 Software Engineering.
