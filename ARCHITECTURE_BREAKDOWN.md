# Tic-Tac-Toe Server Architecture - Senior Bachelor Level Technical Breakdown

## Executive Summary

This document provides a comprehensive analysis of a **multithreaded, real-time Tic-Tac-Toe game server** built with Java. The system demonstrates advanced software engineering principles including **concurrent programming, distributed systems communication, caching strategies, and game state management**.

The architecture employs a **dual-protocol approach** (WebSocket + HTTP REST), **in-memory caching with database persistence**, and **asynchronous message handling** to support real-time multiplayer gameplay while maintaining game state consistency.

---

## 1. System Architecture Overview

### 1.1 High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     Defold Game Client (Lua)                 │
│                                                               │
│  • Real-time rendering                                       │
│  • User input handling                                       │
│  • WebSocket client (net.client)                             │
│  • HTTP REST client for game actions                         │
└────────────────┬──────────────────────────────────┬──────────┘
                 │                                  │
                 │ WebSocket (ws://localhost:8080)  │ HTTP REST (http://localhost:8081)
                 │ (Real-time notifications)        │ (Game actions & queries)
                 ▼                                  ▼
┌──────────────────────────────────────────────────────────────┐
│                   Java Server Layer                           │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────┐         ┌────────────────────────┐  │
│  │  WebSocket Server   │         │  HTTP REST Server      │  │
│  │ (Port 8080)         │         │  (Port 8081)           │  │
│  │                     │         │                        │  │
│  │ • onOpen()          │         │ • POST /api/join       │  │
│  │ • onMessage()       │         │ • POST /api/move       │  │
│  │ • onClose()         │         │ • GET /api/matches     │  │
│  │ • onError()         │         │ • GET /health          │  │
│  └─────────┬───────────┘         └────────┬───────────────┘  │
│            │                              │                   │
│            └──────────────┬───────────────┘                   │
│                           │                                   │
│                           ▼                                   │
│         ┌─────────────────────────────────┐                  │
│         │  GameService (Core Game Logic)  │                  │
│         │                                 │                  │
│         │ • joinGame()                    │                  │
│         │ • makeMove()                    │                  │
│         │ • getGameState()                │                  │
│         │ • matchmaking logic             │                  │
│         └────────┬────────────┬───────────┘                  │
│                  │            │                              │
│    ┌─────────────▼──┐   ┌────▼──────────────┐               │
│    │ GameStateCache │   │ DatabaseManager   │               │
│    │ (In-Memory)    │   │ (Persistent Layer)│               │
│    │                │   │                   │               │
│    │ • activeGames  │   │ • player_sessions │               │
│    │ • activeSess.. │   │ • game_matches    │               │
│    │                │   │ • game_moves      │               │
│    │ ConcurrentHash │   │ • notifications   │               │
│    │     Map        │   │                   │               │
│    └────────────────┘   └───────┬───────────┘               │
│                                 │                            │
│                                 ▼                            │
│                    ┌────────────────────────┐               │
│                    │  SQLite Database       │               │
│                    │ (database/ttt_game.db)│               │
│                    └────────────────────────┘               │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │     WebSocketNotifier (Message Distribution)        │   │
│  │                                                     │   │
│  │ • registerConnection()                             │   │
│  │ • sendToSession()                                  │   │
│  │ • notifyMatchStart()                               │   │
│  │ • storePendingNotification()                       │   │
│  │ • Retry logic with exponential backoff             │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. Core Components & Their Responsibilities

### 2.1 Main.java - Server Bootstrap & Initialization

**Purpose**: Entry point that orchestrates server startup and graceful shutdown.

```java
Key Responsibilities:
1. Initialize SQLite database with schema (DatabaseManager)
2. Create in-memory game state cache (GameStateCache)
3. Start WebSocket server on port 8080
4. Start HTTP REST API server on port 8081
5. Register shutdown hooks for clean termination
```

**Why This Design**:
- **Singleton Pattern**: DatabaseManager and GameStateCache use singleton pattern to ensure only one instance exists across the JVM, preventing race conditions and duplicate connections.
- **Graceful Shutdown**: Java's `Runtime.addShutdownHook()` ensures all resources are properly cleaned up when the server terminates (database connections closed, thread pools shut down, etc.).

**Key Code Pattern**:
```java
// Ensures exactly one database manager instance
DatabaseManager dbManager = DatabaseManager.getInstance();
dbManager.initializeDatabase();

// Ensures exactly one game cache instance
GameStateCache gameCache = GameStateCache.getInstance();
```

---

### 2.2 WebSocket Server (Server.java) - Real-Time Communication

**Purpose**: Handles WebSocket connections for real-time game state updates and notifications.

**Key Methods**:

| Method | Purpose | Why Used |
|--------|---------|----------|
| `onOpen()` | Client connects | Sends welcome message, initializes session |
| `onMessage()` | Receive client message | Registers session ID, handles heartbeats |
| `onClose()` | Client disconnects | Unregisters connection, tracks user status |
| `onError()` | Connection errors | Logs errors, degrades connection quality |

**Critical Architecture Decisions**:

1. **WebSocketNotifier Singleton**:
   - Centralized message broker that manages all active connections
   - Maintains a `ConcurrentHashMap<String, WebSocket>` mapping sessions to connections
   - Decouples game logic from transport layer

2. **Session Registration Flow**:
   ```
   Client Connects → onOpen() sends "server_hello" 
       ↓
   Client sends sessionId via JSON → onMessage() parses JSON
       ↓
   WebSocketNotifier.registerConnection(sessionId, conn)
       ↓
   Session remembered in memory for future messages
   ```

3. **Why Not Store Connections Directly?**
   - **Separation of Concerns**: Game logic shouldn't know about WebSocket implementation
   - **Flexibility**: Could later support other transports (gRPC, plain TCP, etc.)
   - **Testability**: Can mock the notifier without real WebSocket connections

---

### 2.3 HTTP REST API Server (RestApiServer.java) - Game Actions

**Purpose**: Provides synchronous REST endpoints for game actions (join, move, query state).

**Endpoints Implemented**:

```
POST /api/join
├─ Purpose: Player joins matchmaking queue
├─ Input: { sessionId, name }
└─ Output: { success, matchId } or { success: false, error }

POST /api/move
├─ Purpose: Player makes a move on the board
├─ Input: { sessionId, matchId, cellPosition }
└─ Output: { success, gameState }

GET /api/matches
├─ Purpose: List available matches to join
└─ Output: { matches: [{matchId, hostName}, ...] }

POST /api/create-match
├─ Purpose: Create a new game room for others to join
├─ Input: { sessionId, playerName, matchName }
└─ Output: { success, matchId }

GET /health
├─ Purpose: Health check for monitoring
└─ Output: { status: "ok", message: "Server is running" }
```

**Why HTTP REST + WebSocket**:
- **WebSocket for Notifications**: Efficient, persistent connection for game state changes (board updates, opponent moves)
- **HTTP for Commands**: Natural for atomic operations (join game, make move), stateless, easier to debug
- **CORS Headers**: Enables browser-based clients to make cross-origin requests

---

### 2.4 GameService - Core Game Logic Orchestrator

**Purpose**: Implements the core game mechanics and matchmaking logic.

**Key Responsibilities**:

```java
1. joinGame(sessionId, playerName)
   ├─ Single player? → Put in lobby, return "waiting"
   ├─ Second player arrives? → Create match, return matchId
   └─ Uses synchronized lobbyLock to prevent race conditions

2. makeMove(sessionId, matchId, cellPosition)
   ├─ Validate move (position 0-8, not occupied)
   ├─ Update game cache
   ├─ Check win/draw/ongoing
   ├─ Broadcast state to both players
   └─ If game ends → save to database

3. getAvailableMatches()
   ├─ Query database for waiting games
   └─ Return with host player name

4. createMatch(sessionId, playerName, matchName)
   ├─ Create new game room
   └─ Set status to "waiting" for opponents
```

**Critical Design: Synchronized Lobby Lock**

```java
synchronized (lobbyLock) {
    if (waitingPlayerId == null) {
        // First player
        waitingPlayerId = sessionId;
        return "waiting";
    } else if (!waitingPlayerId.equals(sessionId)) {
        // Second player - create match
        String matchId = UUID.randomUUID().toString();
        createGame(matchId, waitingPlayerId, sessionId, ...);
        waitingPlayerId = null; // Clear lobby
        return matchId;
    }
}
```

**Why Synchronization?**
- **Race Condition Prevention**: Two players could simultaneously call `joinGame()` while lobby is empty
- **Without Lock**: Both see `waitingPlayerId == null`, both become "first player"
- **With Lock**: Only one thread can execute the critical section; second thread waits, sees occupied slot, creates match

---

### 2.5 GameStateCache - High-Performance In-Memory Cache

**Purpose**: Eliminates database hits during active gameplay for maximum responsiveness.

**Design Pattern: Write-Through Cache**

```
Active Game Flow:
1. Player makes move → gameCache.makeMove() [instant, in-memory]
2. Move stored in ConcurrentHashMap
3. Game ends → gameCache saveCompletedGameToDatabase()
4. Only then is database updated

Why This Approach:
• Real-time gameplay doesn't need database for every move
• Database I/O is expensive (~1-10ms per query)
• In-memory operations are nanoseconds
• Only final results need persistence
```

**Data Structures**:

```java
ConcurrentHashMap<String, GameState> activeGames
├─ Key: matchId (e.g., "M-uuid-123")
└─ Value: GameState
    ├─ matchId, sessionId1, sessionId2
    ├─ board (string: ".....X.O.")
    ├─ currentPlayer ("X" or "O")
    ├─ status ("active", "finished", "waiting")
    ├─ result ("X_wins", "O_wins", "draw", "ongoing")
    └─ lastUpdate (timestamp)

ConcurrentHashMap<String, PlayerSession> activeSessions
├─ Key: sessionId
└─ Value: PlayerSession
    ├─ sessionId, playerName
    ├─ connectionStatus ("connected", "disconnected")
    ├─ lastHeartbeat
    └─ currentMatch
```

**Why ConcurrentHashMap?**
- **Thread-Safe Without Full Synchronization**: Multiple threads can read simultaneously without blocking
- **Lock Striping**: Internally uses multiple locks (buckets), not one global lock
- **Performance**: Much faster than `Collections.synchronizedMap()` for high-concurrency scenarios

**Game Result Checking**:
```java
private String checkGameResult(String board) {
    // Check 3 win patterns: rows, columns, diagonals
    for (int i = 0; i < 3; i++) {
        if (row_matches(i)) return "X_wins" or "O_wins";
    }
    if (!board.contains(".")) return "draw";
    return "ongoing";
}
```

The board is a 9-character string:
```
Index: 0 1 2
       3 4 5
       6 7 8

Example: "X.O.X...." represents:
       X . O
       . X .
       . . .
```

---

### 2.6 DatabaseManager - Persistent Storage Layer

**Purpose**: SQLite connection management and schema initialization with connection pooling.

**Database Schema**:

```sql
player_sessions
├─ session_id (TEXT PRIMARY KEY)
├─ player_name (TEXT)
├─ connected_at, last_heartbeat (TIMESTAMP)
├─ websocket_id (TEXT)
├─ connection_status (TEXT: "connected", "disconnected")
└─ retry_count (INTEGER)

game_matches
├─ match_id (TEXT PRIMARY KEY)
├─ player1_session, player2_session (FK → player_sessions)
├─ board (TEXT: ".........") 
├─ current_turn (TEXT: "X" or "O")
├─ status (TEXT: "waiting", "active", "finished")
├─ result (TEXT: "X_wins", "O_wins", "draw", "ongoing")
├─ created_at, updated_at, last_move_at (TIMESTAMP)
└─ state_version (INTEGER) [for optimistic locking]

game_moves
├─ move_id (INTEGER PRIMARY KEY AUTOINCREMENT)
├─ match_id, session_id (FKs)
├─ cell_position (INTEGER: 0-8)
├─ mark (TEXT: "X" or "O")
├─ timestamp (TIMESTAMP)
├─ state_version (INTEGER)
└─ validated (BOOLEAN)

pending_notifications
├─ id (INTEGER PRIMARY KEY AUTOINCREMENT)
├─ session_id (FK)
├─ notification_type (TEXT)
├─ data (TEXT: JSON)
├─ created_at, next_retry (TIMESTAMP)
├─ attempts, max_attempts (INTEGER)
└─ delivered (BOOLEAN)

connection_health
├─ session_id (PRIMARY KEY)
├─ last_ping, last_pong (TIMESTAMP)
├─ ping_count, missed_pings (INTEGER)
└─ connection_quality (DOUBLE: 0.0 to 1.0)
```

**Connection Pooling Strategy**:

```java
ConnectionPool (Singleton)
├─ Maintains up to 10 pooled connections to SQLite
├─ Reuses connections instead of creating new ones
├─ Auto-closes idle connections after timeout
└─ Prevents: "too many connections" errors

Why Pooling?
• Database connection creation is expensive (~10-50ms)
• With 8 game threads + cleanup threads, need multiple connections
• Without pooling: Threads would block waiting for connections
```

---

### 2.7 WebSocketNotifier - Message Distribution System

**Purpose**: Manages WebSocket connections and implements reliable message delivery.

**Key Operations**:

```java
registerConnection(sessionId, connection)
└─ Maps session → WebSocket, triggers retry of pending messages

unregisterConnection(sessionId)
└─ Removes mapping when client disconnects

sendToSession(sessionId, message)
├─ If connected? Send immediately
├─ If disconnected? Store in pending_notifications table
└─ Assume client will reconnect and receive messages

storePendingNotification(sessionId, type, data)
├─ INSERT INTO pending_notifications (...)
└─ Sets next_retry = now + 5 seconds

sendPendingMessages(sessionId)
└─ Called when client reconnects
    ├─ Query all undelivered notifications
    ├─ Send in order
    └─ Mark as delivered
```

**Retry Logic with Exponential Backoff**:

```java
startRetryTask() {
    // Every 10 seconds, check pending notifications
    retryScheduler.scheduleAtFixedRate(
        this::retryPendingNotifications, 
        10,    // Initial delay
        10,    // Repeat interval
        TimeUnit.SECONDS
    );
}

retryPendingNotifications() {
    for (Notification n : getAllPending()) {
        if (n.nextRetry <= now()) {
            if (sendToSession(n.sessionId, n.data)) {
                mark_delivered(n.id);
            } else {
                n.attempts++;
                if (n.attempts >= n.max_attempts) {
                    discard_notification(n.id); // Give up after 3 tries
                } else {
                    n.nextRetry = now() + (5 * 2^n.attempts); // Exponential backoff
                }
            }
        }
    }
}
```

**Why This Approach?**
- **Network Reliability**: Internet connections are unreliable; messages can be lost
- **Graceful Degradation**: If client disconnects temporarily, they don't lose game updates
- **Database Audit Trail**: All notification attempts logged for debugging

---

### 2.8 TttRoom & Matchmaker - Game Room Management

**Purpose**: Manages active game rooms and routes messages to appropriate games.

**TttRoom Design - Efficient Game State Representation**:

```java
public class TttRoom {
    private int xMask = 0;      // Bitmask of X positions
    private int oMask = 0;      // Bitmask of O positions
    private boolean xTurn = true; // Whose turn?
    
    // Example: X at positions 0,4,8; O at positions 1,2
    // xMask = 0b100010001 (binary) = positions where X played
    // oMask = 0b011000000 (binary) = positions where O played
}
```

**Why Bitmasks Instead of Strings?**
- **Performance**: Integer comparison is nanoseconds; string manipulation is slower
- **Memory**: 2 integers (8 bytes) vs. 9-character string (36 bytes) for board state
- **Win Detection**: 
  ```java
  // Check if X has 3 in a row
  for (int winPattern : WINS) {  // 8 predefined win patterns
      if ((xMask & winPattern) == winPattern) {
          return "X_wins";
      }
  }
  ```
  This uses bitwise AND: only 1 CPU cycle instead of checking 9 positions

**Win Patterns**:
```java
private static final int[] WINS = {
    0b111000000,  // Row 1 (positions 0,1,2)
    0b000111000,  // Row 2 (positions 3,4,5)
    0b000000111,  // Row 3 (positions 6,7,8)
    0b100100100,  // Col 1 (positions 0,3,6)
    0b010010010,  // Col 2 (positions 1,4,7)
    0b001001001,  // Col 3 (positions 2,5,8)
    0b100010001,  // Diagonal \ (positions 0,4,8)
    0b001010100   // Diagonal / (positions 2,4,6)
};
```

**Move Validation**:
```java
public void onMove(ClientSession s, int cell) {
    sched.execute(() -> {
        // 1. Check if correct player's turn
        boolean isX = (s == p1);
        if (xTurn != isX) return; // Invalid: not your turn
        
        // 2. Check if cell is empty
        int bit = 1 << (8 - cell);
        if (((xMask | oMask) & bit) != 0) return; // Invalid: occupied
        
        // 3. Make move
        if (isX) xMask |= bit;
        else oMask |= bit;
        xTurn = !xTurn;
        
        // 4. Check for end condition
        String result = result();
        if (!"ongoing".equals(result)) {
            broadcastOver(result); // Game ends
        } else {
            broadcastState(result); // Continue playing
        }
    });
}
```

**Forfeit Timer**:
```java
private void scheduleTimer() {
    // 120-second inactivity timeout
    timer = sched.schedule(() -> {
        String winner = xTurn ? "O" : "X"; // Whoever's turn it is forfeits
        broadcastOver("forfeit:" + winner);
    }, 120, TimeUnit.SECONDS);
}
```

Why 120 seconds? Prevents games from hanging forever if a player's internet drops.

**Matchmaker Pattern**:
```java
public void requestJoin(ClientSession s, String game) {
    ClientSession other = tttWait.poll(); // Try to get waiting player
    
    if (other == null) {
        tttWait.add(s);  // You're first, wait
        s.send({"t": "waiting"});
    } else {
        // Found opponent! Create room
        String id = "M-" + UUID.randomUUID();
        TttRoom room = new TttRoom(id, sched);
        rooms.put(id, room);
        room.addPlayer(other, 1);  // Existing player is X
        room.addPlayer(s, 2);      // New player is O
        room.start();
    }
}
```

---

## 3. Communication Flow: Request → Response

### 3.1 Join Game Flow

```
STEP 1: Client initiates join
┌─────────────────────────┐
│  Defold Game Client     │
│  net.join(sessionId, name)
│  Sends HTTP POST        │
└────────────┬────────────┘
             │ POST /api/join
             │ {"sessionId": "uuid-123", "name": "Swift Gamer"}
             ▼
         ┌───────────────────┐
         │ RestApiServer     │
         │ POST /api/join    │
         └────────┬──────────┘
                  │
                  ▼
         ┌─────────────────────┐
         │ GameService         │
         │ .joinGame()         │
         │ (async/executor)    │
         └────────┬────────────┘
                  │
        LOBBY STATE CHECK
                  │
         ┌────────┴─────────┐
         │                  │
    [FIRST PLAYER]   [SECOND PLAYER]
         │                  │
    Acquire lobbyLock       │
         │              Acquire lobbyLock
         │                  │
    waitingPlayerId=null → Sees waitingPlayerId set
         │                  │
    Set waitingPlayerId     │
    Return "waiting"    Create matchId
         │                  │
         │              Clear waitingPlayerId
         │              createGame() → GameStateCache
         │              Return matchId
         │
    ┌─────┴──────────────────────────┐
    │                                 │
    ▼                                 ▼
HTTP Response                    HTTP Response
{"matchId": "waiting"}          {"matchId": "M-uuid-abc"}
    │                                 │
    ▼                                 ▼
Client waits...              Client receives matchId
                            
STEP 2: Server notifies both players via WebSocket
                            
    GameService calls:
    wsNotifier.notifyMatchStart(matchId)
        │
        ├─→ Query database for player1_session, player2_session
        │
        ├─→ Send Player 1:
        │   {"t": "match_start",
        │    "matchId": "M-uuid-abc",
        │    "yourMark": "X",
        │    "opponentMark": "O"}
        │
        └─→ Send Player 2:
            {"t": "match_start",
             "matchId": "M-uuid-abc",
             "yourMark": "O",
             "opponentMark": "X"}
    
Both clients receive match_start and update UI.
```

### 3.2 Make Move Flow

```
STEP 1: Player clicks board cell
┌──────────────────────┐
│ Defold Client        │
│ ttt.gui_script       │
│ on_input (mouse)     │
│ cell_at(x, y)        │
└────────┬─────────────┘
         │
         ▼
    cell = 4 (center)
         │
         ├─ HTTP POST /api/move
         │  {
         │    "sessionId": "uuid-123",
         │    "matchId": "M-uuid-abc",
         │    "cellPosition": 4
         │  }
         │
         ▼
    ┌──────────────────┐
    │ RestApiServer    │
    │ /api/move POST   │
    └────────┬─────────┘
             │
             ▼
      ┌────────────────┐
      │ GameService    │
      │ .makeMove()    │
      └────────┬───────┘
               │
      GAME STATE VALIDATION
               │
      ┌────────┴─────────────┐
      │                      │
    [VALID]            [INVALID]
      │                      │
  Move to cache         Return error
  ├─ gameCache           ("already occupied",
  │  .makeMove()         "not your turn", etc)
  │
  ▼
gameCache updates in-memory state:
├─ Update xMask or oMask
├─ Switch currentPlayer
├─ Check for win/draw
│
├─ If game continues:
│   wsNotifier.notifyGameState()
│   ├─ Send to Player 1: {"t": "game_state", "board": "....X....", "current_turn": "O"}
│   └─ Send to Player 2: {"t": "game_state", "board": "....X....", "current_turn": "O"}
│
└─ If game ends:
    ├─ gameCache.saveCompletedGameToDatabase()
    │  └─ Writes to game_matches table
    │
    └─ wsNotifier.notifyGameState() with status="finished"
       ├─ Send to both: {"t": "game_over", "board": "X..XO.O..", "result": "X_wins"}
       └─ Save stats to player_sessions
```

### 3.3 Handling Disconnection & Reconnection

```
SCENARIO: Player's internet drops during game

STEP 1: Disconnect Event
Player loses connection
  │
  ▼
WebSocket Server
  ├─ onClose() fires
  │  ├─ Find session by connection
  │  └─ wsNotifier.unregisterConnection(sessionId)
  │      └─ Remove from sessionConnections map
  │      └─ Update DB: connection_status = 'disconnected'
  │
  └─ If in active game:
     ├─ Check TttRoom.onLeave()
     └─ Start 120-second forfeit timer

STEP 2: Server tries to notify opponent
WebSocketNotifier.sendToSession(sessionId, message)
  ├─ Check: sessionConnections.get(sessionId)
  │ └─ Returns null (disconnected)
  │
  └─ Call: storePendingNotification()
     └─ INSERT INTO pending_notifications table
        (session_id, notification_type, data, next_retry)

STEP 3: Player reconnects
Client reconnects WebSocket
  │
  ▼
Server.onOpen() fires
  ├─ Client sends new message with sessionId
  │
  ▼
Server.onMessage() → registerConnection(sessionId, newConn)
  │
  ├─ Update sessionConnections[sessionId] = newConn
  │
  └─ wsNotifier.sendPendingMessages(sessionId)
     ├─ Query: SELECT * FROM pending_notifications
     │         WHERE session_id = ? AND delivered = FALSE
     │
     └─ For each notification:
        ├─ Send via new WebSocket connection
        └─ Mark as delivered
        
All missed updates are delivered!
```

---

## 4. Threading & Concurrency Model

### 4.1 Thread Pool Architecture

```
Main Thread:
└─ Starts all servers and thread pools

WebSocket Server Thread (Netty):
├─ Listens on port 8080
├─ Calls onOpen/onMessage/onClose for each client
└─ Delegates work to GameService thread pool

GameService Thread Pool (ExecutorService):
├─ Fixed pool of 8 worker threads
├─ Each joinGame() runs in a separate thread
├─ Each makeMove() runs in a separate thread
└─ Prevents blocking the WebSocket thread

DatabaseManager Thread Pool:
├─ ScheduledExecutorService for cleanup tasks
├─ Runs connection pool cleanup every 5 minutes
└─ Runs pending notification retry every 10 seconds

WebSocketNotifier Thread Pools:
├─ Retry scheduler: Retries failed messages
└─ Heartbeat scheduler: Sends ping/pong every 30 seconds

TttRoom Thread Pool:
├─ Timers for 120-second move timeout per room
└─ Executes onMove() in separate thread
```

### 4.2 Concurrency Challenges & Solutions

| Challenge | Problem | Solution | Code |
|-----------|---------|----------|------|
| **Lobby Race Condition** | Two players call joinGame() simultaneously; both see empty lobby | `synchronized (lobbyLock)` | `synchronized block in GameService.joinGame()` |
| **Concurrent Game State Mutations** | Multiple threads accessing game board | `ConcurrentHashMap<matchId, GameState>` | GameStateCache uses ConcurrentHashMap |
| **Database Connection Exhaustion** | Too many threads opening connections | Connection pooling | ConnectionPool maintains max 10 connections |
| **WebSocket Message Ordering** | Messages arrive out of order | Message timestamping, state versioning | state_version field in game_moves table |
| **Notification Queue Overflow** | Pending messages accumulate for disconnected clients | Max 3 retry attempts, exponential backoff | pending_notifications with attempts/max_attempts |

### 4.3 CompletableFuture Usage

```java
public CompletableFuture<String> joinGame(String sessionId, String playerName) {
    return CompletableFuture.supplyAsync(() -> {
        // This code runs in GameService thread pool
        synchronized (lobbyLock) {
            // ... game logic ...
            return matchId; // Completes the Future
        }
    }, gameThreadPool);
}

// Called from HTTP request handler:
String matchId = gameService.joinGame(sessionId, playerName).get();
```

**Why CompletableFuture?**
- **Non-blocking REST API**: HTTP request doesn't block entire WebSocket server
- **Clean Async Code**: Chains operations with `.thenApply()`, `.exceptionally()`, etc.
- **Timeout Safety**: Can set timeout with `.get(timeout, unit)`

---

## 5. Game State Consistency Strategies

### 5.1 Optimistic Locking (state_version)

```sql
-- Database schema
game_moves {
    state_version INTEGER,  -- Version at time of move
    validated BOOLEAN       -- Did validation pass?
}
```

**Problem Being Solved**:
Two players make moves simultaneously. Without versioning, moves could be applied out of order.

**Solution**:
```
Player 1 move:  state_version = 3
  ├─ Board before: "X.O.X...."
  ├─ Move to position 7
  └─ Expected board after: "X.O.X..X."

Player 2 move:  state_version = 3
  ├─ Board before: "X.O.X...."  (conflicting expectation!)
  ├─ Move to position 5
  └─ Expected board after: "X.O.XO..."

Validation:
  Player 1's move applied ✓
  Player 2's move REJECTED (state_version changed)
  Player 2 must retry with new state_version
```

### 5.2 Write-Through Cache Consistency

```
Active game move:   In-memory only (GameStateCache)
                    ↓ (instant response to client)
                    
Game ends:          Save to database (atomic operation)
                    ├─ INSERT INTO game_matches (final state)
                    ├─ INSERT INTO game_moves (each move in order)
                    └─ UPDATE player_sessions (stats)
```

**Why This Works**:
- Fast response during gameplay (in-memory)
- Database always has consistent final state
- No partial state writes

---

## 6. Integration with Defold Game Engine

### 6.1 Defold Client Architecture

**File**: `defold-client/net/client.lua`

```lua
-- Network module provides two interfaces:
-- 1. WebSocket for notifications (passive)
-- 2. HTTP REST for commands (active)

M.connect(url) -- Connect to ws://127.0.0.1:8080
  ├─ Uses native WebSocket extension
  ├─ Registers callback for onOpen/onMessage/onClose
  └─ Queues outgoing messages until connected

M.send(tbl) -- Send message (JSON encoded)
  └─ Encodes Lua table to JSON string
  └─ Sends via WebSocket if connected
  └─ Queues if disconnected (retry on reconnect)

net.join(sessionId, playerName) -- HTTP POST /api/join
  ├─ Sends HTTP request
  └─ Calls callback with response

net.make_move(matchId, cellPosition) -- HTTP POST /api/move
  ├─ Sends HTTP request
  └─ Waits for response
```

**File**: `defold-client/ttt/ttt.gui_script`

```lua
-- Main game UI logic

game.init()
  ├─ Generate random player name
  ├─ Create session ID
  ├─ Connect WebSocket
  └─ Show lobby

on_input(self, action_id, action)
  ├─ Mouse click on board? → cell_at(x, y)
  │   └─ net.make_move(matchId, cell)
  │
  └─ Keyboard input?
      ├─ 'J' → Join random game (find waiting players)
      ├─ 'C' → Create new game (others can join you)
      └─ 'S' → View statistics

on_message(self, message_id, message, sender)
  ├─ Receive from WebSocket:
  │
  ├─ "match_start" → Game begins, show board
  │   └─ display_match(self, message.matchId, message.yourMark)
  │
  ├─ "game_state" → Opponent's move
  │   └─ update_board_visual(self, message.board)
  │
  ├─ "game_over" → Game ends
  │   └─ show_result(self, message.result)
  │
  └─ "waiting" → Looking for opponent
      └─ display_waiting_message()
```

### 6.2 Message Flow Diagram

```
Defold Game        HTTP                 WebSocket              Server
Client             REST                 Connection             (Java)
│                  │                    │                      │
├─ Generate name   │                    │                      │
├─ Create sessionID│                    │                      │
│                  │                    │                      │
└──────────────────┼────────POST /api/join──────────────────→  │
                   │                    │                  [HTTP Server]
                   │                    │                  [GameService]
                   │                    │                      │
                   │←───Response {matchId}──────────────────   │
                   │                    │                      │
└──────────────────────────connect WS──────────────────────→  │
                   │                    │              [WebSocket]
                   │                    │                      │
                   │                    ├──registerConnection──│
                   │                    │←─connection_confirmed│
                   │                    │                      │
                   │                    │◄──match_start─────   │
                   │                    │ {matchId,yourMark}   │
                   │                    │                      │
          [GUI shows board]             │                      │
                   │                    │                      │
          Player clicks cell 4          │                      │
                   │                    │                      │
                   ├──POST /api/move────→                      │
                   │ {matchId, cell: 4}  │             [Validate]
                   │                    │              [Update cache]
                   │                    │                      │
                   │◄──Response OK───────│                      │
                   │                    │                      │
                   │                    │◄──game_state──────   │
                   │                    │ {board, nextTurn}    │
                   │                    │                      │
          [GUI renders board]           │                      │
                   │                    │                      │
                   │ [Wait for opponent]│                      │
                   │                    │◄──game_state──────   │
                   │                    │ (opponent's move)    │
                   │                    │                      │
          [GUI renders opponent move]   │                      │
                   │                    │                      │
                   │                    │◄──game_over────────  │
                   │                    │ {result: "X_wins"}   │
                   │                    │                      │
          [Show winner, stats]          │                      │
```

---

## 7. Performance & Scalability Considerations

### 7.1 Bottleneck Analysis

| Component | Bottleneck | Impact | Mitigation |
|-----------|-----------|--------|-----------|
| **Database** | SQLite single-writer | During game cleanup | Cache active games in-memory |
| **WebSocket** | Single port 8080 | Max ~1000 concurrent | Use load balancer, Redis pub/sub for multi-server |
| **GameService thread pool (8 threads)** | Thread pool size | Matchmaking blocked if all threads busy | Increase pool size or use ForkJoinPool |
| **Memory** | ConcurrentHashMap grows unbounded | OOM if games never cleaned up | Implement game cleanup after 1 hour inactivity |
| **Network bandwidth** | JSON message size | Slow on poor networks | Binary protocol or compression |

### 7.2 Scalability Roadmap

**Current Design**: Single JVM, one server

```
As traffic grows:

Stage 1 (Current)
└─ 1 JVM (8 game threads)
   └─ Handles ~50-100 concurrent games

Stage 2
├─ Multiple JVM instances
├─ Redis for cross-server session management
├─ Shared database (PostgreSQL instead of SQLite)
└─ Nginx load balancer on port 8080/8081

Stage 3 (Microservices)
├─ Matchmaking service (finds opponents)
├─ Game service (runs game logic)
├─ Notification service (broadcasts updates)
├─ Statistics service (player data)
└─ Message broker (Kafka for event streaming)
```

### 7.3 Memory Profiling

```
Per active game (in-memory):
├─ GameState object: ~200 bytes
├─ Two PlayerSession objects: ~400 bytes
└─ Total: ~600 bytes per game

With 100 concurrent games:
└─ ~60 KB (negligible)

With 10,000 concurrent games:
└─ ~6 MB (still reasonable)

GameStateCache max memory with 10K games: ~10 MB
```

---

## 8. Error Handling & Resilience

### 8.1 Error Scenarios

```
Scenario 1: Player's internet drops mid-move
├─ WebSocket disconnects
├─ TttRoom.onLeave() triggers 120-second timer
├─ If reconnect within 120s → Resume game
└─ If timeout → Opponent wins by forfeit

Scenario 2: HTTP timeout (player takes too long to move)
├─ ClientSession times out after 120s
├─ Server marks as forfeit
├─ Opponent notified and wins
└─ Defold client updates UI accordingly

Scenario 3: Invalid JSON from client
├─ Server.onMessage() catches JsonSyntaxException
├─ Logs error
├─ Sends error response
└─ Game continues (doesn't crash)

Scenario 4: Database connection fails
├─ ConnectionPool.getConnection() throws SQLException
├─ GameService catches exception
├─ Returns error to client
└─ Game state still valid in-memory cache

Scenario 5: Server crashes with active games
├─ Clients detect disconnection
├─ Reconnect attempt fails
├─ Display "Server offline" message
└─ Server restart: Database persists game state
```

### 8.2 Logging Strategy

```java
System.out.println("=== Component Operation ===");
System.out.println("Detailed state information");
System.err.println("Error or warning");

Example:
System.out.println("=== GameService.joinGame START ===");
System.out.println("SessionId: " + sessionId);
System.out.println("Current lobby: " + waitingPlayerId);
System.out.println("Result: " + matchId);
```

Logs can be redirected to file:
```bash
java -jar ttt-server.jar > server.log 2>&1
```

---

## 9. Testing Considerations

### 9.1 Unit Testing Strategy

```java
// Test concurrent access
@Test
public void testConcurrentJoin() {
    ExecutorService exec = Executors.newFixedThreadPool(10);
    List<Future<String>> results = new ArrayList<>();
    
    for (int i = 0; i < 20; i++) {
        results.add(exec.submit(() -> 
            gameService.joinGame("session-" + UUID.random(), "Player")
        ));
    }
    
    // Should create 10 matches (pairs of 2)
    long matchCount = results.stream()
        .map(f -> f.get())
        .filter(r -> r.startsWith("M-"))
        .count();
    
    assertEquals(10, matchCount);
}

// Test move validation
@Test
public void testInvalidMove() {
    gameCache.createGame("M-1", "s1", "s2", "P1", "P2");
    
    boolean move1 = gameCache.makeMove("M-1", 0, "s1"); // Valid
    assertTrue(move1);
    
    boolean move2 = gameCache.makeMove("M-1", 0, "s2"); // Same cell!
    assertFalse(move2);
    
    boolean move3 = gameCache.makeMove("M-1", 1, "s1"); // Wrong turn!
    assertFalse(move3);
}

// Test win condition
@Test
public void testWinDetection() {
    gameCache.createGame("M-1", "s1", "s2", "P1", "P2");
    
    // X wins: position 0,1,2
    gameCache.makeMove("M-1", 0, "s1"); // X
    gameCache.makeMove("M-1", 3, "s2"); // O
    gameCache.makeMove("M-1", 1, "s1"); // X
    gameCache.makeMove("M-1", 4, "s2"); // O
    gameCache.makeMove("M-1", 2, "s1"); // X → wins
    
    GameState game = gameCache.getGame("M-1");
    assertEquals("finished", game.status);
    assertEquals("X_wins", game.result);
}
```

### 9.2 Integration Testing

```
Test: Full game from join to finish

1. Client A joins
   → Returns "waiting"

2. Client B joins
   → Returns matchId "M-xyz"
   → Both receive WebSocket match_start message

3. Client A makes move (X) at position 4
   → Both receive game_state update

4. Client B makes move (O) at position 0
   → Both receive game_state update

5. Client A makes move (X) at position 1
   → Both receive game_state update

6. Client B makes move (O) at position 2
   → Both receive game_state update

7. Client A makes move (X) at position 3
   → Game ends: X wins (0,3,6 diagonal)
   → Both receive game_over message

Verify:
├─ Database has all moves recorded
├─ Player stats updated
├─ Room cleaned up from memory
└─ Players can start new game
```

---

## 10. Key Takeaways for Your Teacher

### 10.1 Architecture Principles Demonstrated

1. **Separation of Concerns**
   - WebSocket server handles transport (onMessage, onClose)
   - GameService handles business logic (joinGame, makeMove)
   - DatabaseManager handles persistence
   - Each class has one responsibility

2. **Concurrency Best Practices**
   - Thread pools instead of creating threads per-request
   - ConcurrentHashMap instead of synchronized collections
   - Synchronized blocks only where necessary (lobby lock)
   - CompletableFuture for async, non-blocking operations

3. **Performance Optimization**
   - In-memory cache (GameStateCache) for fast gameplay
   - Connection pooling to reuse database connections
   - Bitmasks for game state (8 bytes vs. 36 bytes)
   - Write-through cache (instant response, eventual database persistence)

4. **Reliability & Resilience**
   - Message retry with exponential backoff
   - Pending notification system for disconnected clients
   - 120-second timeout to prevent hung games
   - Graceful shutdown with resource cleanup

5. **Scalability Foundation**
   - Singleton pattern for shared resources
   - Thread pool sizing to limit resource consumption
   - Database connection pooling
   - Prepared statements (prevent SQL injection)

### 10.2 Real-World Patterns

This server demonstrates production-grade patterns used in industry:

| Pattern | Where Used |
|---------|-----------|
| **Singleton** | DatabaseManager, GameStateCache |
| **Thread Pool** | Executors.newFixedThreadPool(), ScheduledExecutorService |
| **Observer** | WebSocket callbacks (onOpen, onMessage, onClose) |
| **Repository** | GameStateCache as repository of game state |
| **Strategy** | Win detection algorithm (checking 8 patterns) |
| **Facade** | GameService as facade over cache, database, notifications |
| **Command** | REST API endpoints (POST /api/move) |
| **Circuit Breaker** | Pending notifications with max retries |

### 10.3 Trade-offs Made

**Trade-off 1: Eventual Consistency vs. Strong Consistency**
- ✓ **Chose**: Eventual consistency (in-memory cache, database syncs at end)
- **Benefit**: Instant response during gameplay (~1ms vs. ~10ms with database)
- **Cost**: Brief window where game state in-memory differs from database
- **Justification**: Gaming needs responsiveness; batch consistency acceptable

**Trade-off 2: Single-threaded Game Room vs. Multi-threaded**
- ✓ **Chose**: Single-threaded per room (moves queued via executor)
- **Benefit**: No race conditions within a room; bitmask operations atomic at CPU level
- **Cost**: Cannot parallelize game logic (minor, since moves are sequential anyway)
- **Justification**: Turn-based game doesn't benefit from parallelism

**Trade-off 3: SQLite vs. PostgreSQL**
- ✓ **Chose**: SQLite (for this prototype)
- **Benefit**: No external database server; simple deployment
- **Cost**: Single-writer limitation; not suitable for large-scale
- **Justification**: Small prototype; can migrate to PostgreSQL later

### 10.4 How It Connects: Game Logic Flow

```
User clicks board in Defold
    ↓
Defold calls net.make_move() [HTTP]
    ↓
RestApiServer.makeMove endpoint receives request
    ↓
GameService.makeMove(sessionId, matchId, cellPosition)
    ↓
GameStateCache.makeMove() (in-memory, instant)
    ↓
Update bitmasks (xMask, oMask)
    ↓
Check game result (win/draw/ongoing)
    ↓
If game continues:
  - WebSocketNotifier broadcasts game_state to both players
    ↓
    Defold clients receive via WebSocket
    ↓
    Defold GUI updates board display

If game ends:
  - Save to database
  - WebSocketNotifier broadcasts game_over
    ↓
    Defold clients receive game_over
    ↓
    Show winner/stats
```

---

## Conclusion

This Tic-Tac-Toe server represents a **production-grade architecture** balancing performance, scalability, and maintainability. It demonstrates:

- **Concurrent programming** (thread pools, thread-safe collections, synchronization)
- **Distributed systems** (dual protocols WebSocket + HTTP, connection management)
- **Database design** (schema normalization, connection pooling, atomic operations)
- **Performance optimization** (caching, efficient algorithms, connection reuse)
- **Error handling** (timeouts, retries, graceful degradation)

For your project presentation, emphasize:

1. **Why each component exists** (separation of concerns)
2. **How concurrency is managed** (thread pools, synchronized blocks)
3. **How performance is achieved** (caching, bitmasks, connection pooling)
4. **How reliability is ensured** (retries, timeouts, logging)
5. **How it scales** (architecture foundation for growth)

Good luck with your presentation!
