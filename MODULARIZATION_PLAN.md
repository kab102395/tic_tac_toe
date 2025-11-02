# Multi-Game Architecture Modularization Plan

## Executive Summary

This document outlines the **complete refactoring strategy** to convert the existing Tic-Tac-Toe game server into a modular multi-game platform that supports:
- ✅ 6 different game types (Tic-Tac-Toe, Puzzle, Ping Pong, Duck Hunt, Space Shooter, + 1 more)
- ✅ Concurrent play (players can play multiple games simultaneously)
- ✅ Single SQLite database with game_type fields
- ✅ Reused threading, connection pooling, and multi-threading infrastructure
- ✅ Zero breaking changes to existing Tic-Tac-Toe functionality during refactoring

**Key Principle:** Modularize first → Test on Tic-Tac-Toe → Add new games incrementally

---

## Phase 1: Database Schema & Core Abstractions

### What We're Adding

#### 1.1 New Database Tables

**`games` table** - Metadata for each game type
```sql
CREATE TABLE games (
    game_id TEXT PRIMARY KEY,           -- "tictactoe", "puzzle", "pingpong", etc
    game_name TEXT NOT NULL,             -- "Tic-Tac-Toe", "Puzzle Master", etc
    description TEXT,                    -- Game description
    min_players INTEGER DEFAULT 1,       -- 1 for single-player, 2 for multiplayer
    max_players INTEGER DEFAULT 2,
    is_multiplayer BOOLEAN DEFAULT 0,    -- 1 if requires opponent
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Why?** Centralized game registry. Defold can query this to show available games in campus lobby.

---

**`player_stats` table** - Per-player, per-game statistics
```sql
CREATE TABLE player_stats (
    stat_id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_name TEXT NOT NULL,
    game_id TEXT NOT NULL,               -- FK to games.game_id
    total_games INTEGER DEFAULT 0,
    wins INTEGER DEFAULT 0,
    losses INTEGER DEFAULT 0,
    draws INTEGER DEFAULT 0,
    total_raw_score INTEGER DEFAULT 0,
    avg_normalized_score REAL DEFAULT 0.0,
    highest_score INTEGER DEFAULT 0,
    last_played TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (game_id) REFERENCES games(game_id),
    UNIQUE(player_name, game_id)
);
```

**Why?** Track per-game stats separately. Player sees their Tic-Tac-Toe record vs Puzzle record vs Duck Hunt record. Leaderboards can be per-game.

---

**`scoring_formulas` table** - Define how each game normalizes scores
```sql
CREATE TABLE scoring_formulas (
    formula_id INTEGER PRIMARY KEY AUTOINCREMENT,
    game_id TEXT NOT NULL,
    raw_score_min INTEGER DEFAULT 0,     -- Minimum possible score
    raw_score_max INTEGER DEFAULT 100,   -- Maximum possible score
    normalized_max INTEGER DEFAULT 100,  -- All normalized to 0-100
    calculation_type TEXT DEFAULT 'linear', -- 'linear', 'exponential', 'custom'
    description TEXT,                    -- "Points for pieces collected"
    FOREIGN KEY (game_id) REFERENCES games(game_id)
);
```

**Why?** Each game has different scoring:
- Tic-Tac-Toe: 0-10 points
- Puzzle: 0-100 pieces
- Ping Pong: 0-1000 rally count
- Duck Hunt: 0-50 targets
- Space Shooter: 0-200 enemies

This table lets us convert all to 0-100 normalized range for fair comparison.

---

**`player_current_matches` table** - Track concurrent games per player
```sql
CREATE TABLE player_current_matches (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    table_number INTEGER NOT NULL,      -- 0-5 (six tables)
    match_id TEXT NOT NULL,
    game_id TEXT NOT NULL,              -- "tictactoe", "puzzle", etc
    status TEXT DEFAULT 'active',       -- 'active', 'completed', 'forfeited'
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES player_sessions(session_id),
    FOREIGN KEY (game_id) REFERENCES games(game_id),
    UNIQUE(session_id, table_number)    -- One game per table per player
);
```

**Why?** Support concurrent play. Player session_id can have up to 6 rows (one per table). When player leaves Table 2, we know it's specifically that game that forfeits, not their other 5 games.

---

**Modified `game_matches` table** - Add game_type tracking
```sql
-- NEW FIELD:
ALTER TABLE game_matches ADD COLUMN game_id TEXT DEFAULT 'tictactoe';

-- Plus update any game_moves references:
ALTER TABLE game_moves ADD COLUMN game_id TEXT DEFAULT 'tictactoe';
```

**Why?** Query games by type. Example: "Get all Tic-Tac-Toe matches for this player" vs "Get all Puzzle matches".

---

#### 1.2 New Java Classes

**`GameType.java`** - Enum defining all game types
```java
public enum GameType {
    TICTACTOE("tictactoe", "Tic-Tac-Toe", 2, true),
    PUZZLE("puzzle", "Puzzle Master", 1, false),
    PING_PONG("pingpong", "Ping Pong", 2, true),
    DUCK_HUNT("duckhunt", "Duck Hunt", 1, false),
    SPACE_SHOOTER("spaceshooter", "Space Shooter", 1, false),
    CUSTOM("custom", "Custom Game", 1, false);
    
    public final String id;
    public final String displayName;
    public final int maxPlayers;
    public final boolean isMultiplayer;
    
    GameType(String id, String displayName, int maxPlayers, boolean isMultiplayer) {
        this.id = id;
        this.displayName = displayName;
        this.maxPlayers = maxPlayers;
        this.isMultiplayer = isMultiplayer;
    }
    
    public static GameType fromId(String id) {
        for (GameType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return TICTACTOE; // Default fallback
    }
}
```

**Why?** Type-safe game selection. Prevents typos like "ticatoe" vs "tictactoe". Central registry.

---

**`Room.java`** - Abstract base class for all games
```java
public abstract class Room {
    protected String matchId;
    protected String gameId;  // Which game type
    protected ScheduledExecutorService scheduler;
    protected ClientSession player1;
    protected ClientSession player2;
    protected long startTime;
    protected GameResult result;  // Will be set at end
    
    public Room(String matchId, String gameId, ScheduledExecutorService scheduler) {
        this.matchId = matchId;
        this.gameId = gameId;
        this.scheduler = scheduler;
        this.startTime = System.currentTimeMillis();
    }
    
    // Abstract methods - each game implements
    public abstract void start();
    public abstract void onMove(ClientSession player, int moveData);
    public abstract void onLeave(ClientSession player);
    public abstract GameResult getResult();
    public abstract void broadcastState(String state);
}
```

**Why?** Interface contract for all games. Ensures every game implements the same lifecycle: start() → onMove() → onLeave() → getResult().

---

**`GameResult.java`** - DTO for game outcomes
```java
public class GameResult {
    public String matchId;
    public String gameId;
    public String winner;        // Player name or "draw"
    public int player1RawScore;
    public int player2RawScore;
    public int player1NormalizedScore;
    public int player2NormalizedScore;
    public String result;        // "X_wins", "draw", "forfeit:O", etc
    public long durationMs;
    
    // Constructor, getters, setters...
}
```

**Why?** Unified way to return game results. Server saves these consistently to DB.

---

**`RoomFactory.java`** - Creates correct Room type based on game_id
```java
public class RoomFactory {
    
    public static Room createRoom(
        String matchId,
        String gameId,
        ScheduledExecutorService scheduler
    ) {
        GameType type = GameType.fromId(gameId);
        
        switch (type) {
            case TICTACTOE:
                return new TttRoom(matchId, scheduler);
            case PUZZLE:
                return new PuzzleRoom(matchId, scheduler);
            case PING_PONG:
                return new PingPongRoom(matchId, scheduler);
            case DUCK_HUNT:
                return new DuckHuntRoom(matchId, scheduler);
            case SPACE_SHOOTER:
                return new SpaceShooterRoom(matchId, scheduler);
            default:
                throw new IllegalArgumentException("Unknown game type: " + gameId);
        }
    }
}
```

**Why?** Single point to instantiate games. Adding a new game = one line in the switch statement.

---

### 1.3 Modified Java Classes (Minimal Changes)

#### **DatabaseManager.java** - Add new tables

**What changes:** `createFreshDatabase()` method adds new CREATE TABLE statements

**Location:** After existing tables (~line 90 in `createFreshDatabase()`)

**New SQL:**
```java
// In createFreshDatabase() method, add these after existing tables:

conn.createStatement().execute("""
    CREATE TABLE IF NOT EXISTS games (
        game_id TEXT PRIMARY KEY,
        game_name TEXT NOT NULL,
        description TEXT,
        min_players INTEGER DEFAULT 1,
        max_players INTEGER DEFAULT 2,
        is_multiplayer BOOLEAN DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
""");

conn.createStatement().execute("""
    CREATE TABLE IF NOT EXISTS player_stats (
        stat_id INTEGER PRIMARY KEY AUTOINCREMENT,
        player_name TEXT NOT NULL,
        game_id TEXT NOT NULL,
        total_games INTEGER DEFAULT 0,
        wins INTEGER DEFAULT 0,
        losses INTEGER DEFAULT 0,
        draws INTEGER DEFAULT 0,
        total_raw_score INTEGER DEFAULT 0,
        avg_normalized_score REAL DEFAULT 0.0,
        highest_score INTEGER DEFAULT 0,
        last_played TIMESTAMP,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (game_id) REFERENCES games(game_id),
        UNIQUE(player_name, game_id)
    )
""");

conn.createStatement().execute("""
    CREATE TABLE IF NOT EXISTS scoring_formulas (
        formula_id INTEGER PRIMARY KEY AUTOINCREMENT,
        game_id TEXT NOT NULL,
        raw_score_min INTEGER DEFAULT 0,
        raw_score_max INTEGER DEFAULT 100,
        normalized_max INTEGER DEFAULT 100,
        calculation_type TEXT DEFAULT 'linear',
        description TEXT,
        FOREIGN KEY (game_id) REFERENCES games(game_id)
    )
""");

conn.createStatement().execute("""
    CREATE TABLE IF NOT EXISTS player_current_matches (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        session_id TEXT NOT NULL,
        table_number INTEGER NOT NULL,
        match_id TEXT NOT NULL,
        game_id TEXT NOT NULL,
        status TEXT DEFAULT 'active',
        started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (session_id) REFERENCES player_sessions(session_id),
        FOREIGN KEY (game_id) REFERENCES games(game_id),
        UNIQUE(session_id, table_number)
    )
""");

// Initialize games registry
conn.createStatement().execute("""
    INSERT OR IGNORE INTO games (game_id, game_name, is_multiplayer, max_players) VALUES
    ('tictactoe', 'Tic-Tac-Toe', 1, 2),
    ('puzzle', 'Puzzle Master', 0, 1),
    ('pingpong', 'Ping Pong', 1, 2),
    ('duckhunt', 'Duck Hunt', 0, 1),
    ('spaceshooter', 'Space Shooter', 0, 1),
    ('custom', 'Custom Game', 0, 1)
""");

// Initialize scoring formulas
conn.createStatement().execute("""
    INSERT OR IGNORE INTO scoring_formulas (game_id, raw_score_min, raw_score_max, description) VALUES
    ('tictactoe', 0, 10, 'Win=10, Draw=5, Loss=0'),
    ('puzzle', 0, 100, 'Pieces collected'),
    ('pingpong', 0, 1000, 'Rally count'),
    ('duckhunt', 0, 50, 'Targets hit'),
    ('spaceshooter', 0, 200, 'Enemies destroyed'),
    ('custom', 0, 100, 'Custom scoring')
""");

// Alter existing tables to add game_id
try {
    conn.createStatement().execute("ALTER TABLE game_matches ADD COLUMN game_id TEXT DEFAULT 'tictactoe'");
} catch (SQLException e) {
    System.out.println("Column game_id already exists in game_matches");
}

try {
    conn.createStatement().execute("ALTER TABLE game_moves ADD COLUMN game_id TEXT DEFAULT 'tictactoe'");
} catch (SQLException e) {
    System.out.println("Column game_id already exists in game_moves");
}
```

**Why?** Initializes the entire multi-game schema. Existing Tic-Tac-Toe rows get `game_id='tictactoe'` by default (backward compatible).

---

#### **GameStateCache.java** - Track gameId in GameState

**What changes:** Two small additions

**Change 1** (~line 30): Add field to GameState inner class
```java
public static class GameState {
    public String matchId;
    public String gameId;           // NEW - "tictactoe", "puzzle", etc
    public String sessionId1;
    // ... rest of fields
    
    public GameState(String matchId, String gameId, String sessionId1, String sessionId2, 
                    String player1Name, String player2Name) {
        this.matchId = matchId;
        this.gameId = gameId;       // NEW
        this.sessionId1 = sessionId1;
        // ... rest of init
    }
}
```

**Change 2** (~line 90): Update createGame method
```java
public void createGame(String matchId, String gameId, String sessionId1, String sessionId2, 
                      String player1Name, String player2Name) {
    GameState game = new GameState(matchId, gameId, sessionId1, sessionId2, player1Name, player2Name);
    activeGames.put(matchId, game);
    // ... rest unchanged
}
```

**Why?** Minimal change. GameStateCache now knows which game type each match is. Used later for scoring calculations.

---

#### **GameService.java** - Route via RoomFactory (MAJOR but isolated)

**What changes:** The part that creates/manages games

**Current code (~line 200):**
```java
// OLD - Tic-Tac-Toe hardcoded
gameCache.createGame(matchId, player1Id, sessionId, player1Name, playerName);
// Then somewhere else, TttRoom is created...
```

**New code:**
```java
// NEW - Route via factory
gameCache.createGame(matchId, "tictactoe", player1Id, sessionId, player1Name, playerName);

// Later when storing results:
Room room = RoomFactory.createRoom(matchId, "tictactoe", gameThreadPool);
// ... or we pull gameId from gameCache.getGame(matchId).gameId
```

**Why?** This change is localized to GameService. RestApiServer and other parts don't change yet. We're just internally routing through the factory.

---

#### **TttRoom.java** - Refactor to extends Room

**What changes:** Class declaration and constructor

**Current code (Line 1):**
```java
public class TttRoom extends Room {
```

**Add to top of file:**
```java
public class TttRoom extends Room {
    // Add this constructor
    public TttRoom(String matchId, ScheduledExecutorService scheduler) {
        super(matchId, "tictactoe", scheduler);
    }
    
    // Keep EVERYTHING ELSE EXACTLY THE SAME
    // All existing methods: onMove(), onLeave(), result(), etc.
```

**Why?** Minimal refactor. Tic-Tac-Toe logic stays identical. Just inherits from Room instead of being standalone.

---

### 1.4 File Summary - Phase 1

| File | Type | Change | Impact |
|------|------|--------|--------|
| `GameType.java` | NEW | Create enum | None - new file |
| `Room.java` | NEW | Abstract base | None - new file |
| `GameResult.java` | NEW | Result DTO | None - new file |
| `RoomFactory.java` | NEW | Factory pattern | None - new file |
| `DatabaseManager.java` | MODIFY | Add new tables | ✅ Backward compatible (schema migration) |
| `GameStateCache.java` | MODIFY | Add gameId field | ✅ Minimal (2 changes) |
| `GameService.java` | MODIFY | Route via factory | ✅ Isolated to game creation logic |
| `TttRoom.java` | MODIFY | Extend Room | ✅ Behavior unchanged |
| All other files | - | No change | ✅ Unaffected |

---

## Phase 2: Placeholder Game Implementations

Quick implementations for testing. These are scaffolds; real game logic comes later.

### 2.1 PuzzleRoom.java (Single-player)

```java
public class PuzzleRoom extends Room {
    private int piecesCollected = 0;
    private static final int MAX_PIECES = 100;
    
    public PuzzleRoom(String matchId, ScheduledExecutorService scheduler) {
        super(matchId, "puzzle", scheduler);
    }
    
    @Override
    public void start() {
        // Send puzzle board to player1
        broadcastState("puzzle_start");
    }
    
    @Override
    public void onMove(ClientSession player, int moveData) {
        // moveData = piece_position
        piecesCollected++;
        
        if (piecesCollected >= MAX_PIECES) {
            result = new GameResult();
            result.player1RawScore = piecesCollected;
            result.result = "completed";
        }
        
        broadcastState("piece_collected:" + piecesCollected);
    }
    
    @Override
    public void onLeave(ClientSession player) {
        result = new GameResult();
        result.result = "forfeit";
    }
    
    @Override
    public GameResult getResult() {
        return result;
    }
    
    @Override
    public void broadcastState(String state) {
        // Send state to player1
    }
}
```

### 2.2 PingPongRoom.java (Multiplayer)

```java
public class PingPongRoom extends Room {
    private int player1Score = 0;
    private int player2Score = 0;
    private int rallyCount = 0;
    
    public PingPongRoom(String matchId, ScheduledExecutorService scheduler) {
        super(matchId, "pingpong", scheduler);
    }
    
    @Override
    public void start() {
        broadcastState("rally_start");
    }
    
    @Override
    public void onMove(ClientSession player, int moveData) {
        // moveData = paddle position
        rallyCount++;
        
        if (rallyCount >= 100) {  // Placeholder win condition
            result = new GameResult();
            result.winner = player == player1 ? "X" : "O";
            result.result = "rally_won";
        }
        
        broadcastState("rally:" + rallyCount);
    }
    
    @Override
    public void onLeave(ClientSession player) {
        result = new GameResult();
        result.result = "forfeit";
    }
    
    @Override
    public GameResult getResult() {
        return result;
    }
    
    @Override
    public void broadcastState(String state) {
        // Broadcast to both players
    }
}
```

### 2.3 DuckHuntRoom.java (Single-player)

```java
public class DuckHuntRoom extends Room {
    private int targetsHit = 0;
    private static final int MAX_TARGETS = 50;
    
    public DuckHuntRoom(String matchId, ScheduledExecutorService scheduler) {
        super(matchId, "duckhunt", scheduler);
    }
    
    @Override
    public void start() {
        broadcastState("hunt_start");
    }
    
    @Override
    public void onMove(ClientSession player, int moveData) {
        // moveData = duck_position
        if (Math.random() < 0.7) {  // 70% hit rate for testing
            targetsHit++;
        }
        
        if (targetsHit >= MAX_TARGETS) {
            result = new GameResult();
            result.player1RawScore = targetsHit;
            result.result = "all_targets_hit";
        }
        
        broadcastState("targets:" + targetsHit);
    }
    
    @Override
    public void onLeave(ClientSession player) {
        result = new GameResult();
        result.result = "forfeit";
    }
    
    @Override
    public GameResult getResult() {
        return result;
    }
    
    @Override
    public void broadcastState(String state) {
        player1.send(new JsonObject() {{
            addProperty("t", "state");
            addProperty("game", "duckhunt");
            addProperty("state", state);
        }});
    }
}
```

### 2.4 SpaceShooterRoom.java (Single-player)

```java
public class SpaceShooterRoom extends Room {
    private int enemiesDestroyed = 0;
    private static final int MAX_ENEMIES = 200;
    
    public SpaceShooterRoom(String matchId, ScheduledExecutorService scheduler) {
        super(matchId, "spaceshooter", scheduler);
    }
    
    @Override
    public void start() {
        broadcastState("wave_start");
    }
    
    @Override
    public void onMove(ClientSession player, int moveData) {
        // moveData = shot_position
        if (Math.random() < 0.5) {  // 50% hit rate
            enemiesDestroyed++;
        }
        
        if (enemiesDestroyed >= MAX_ENEMIES) {
            result = new GameResult();
            result.player1RawScore = enemiesDestroyed;
            result.result = "wave_cleared";
        }
        
        broadcastState("enemies:" + enemiesDestroyed);
    }
    
    @Override
    public void onLeave(ClientSession player) {
        result = new GameResult();
        result.result = "forfeit";
    }
    
    @Override
    public GameResult getResult() {
        return result;
    }
    
    @Override
    public void broadcastState(String state) {
        player1.send(new JsonObject() {{
            addProperty("t", "state");
            addProperty("game", "spaceshooter");
            addProperty("state", state);
        }});
    }
}
```

---

## Phase 3: Update TttRoom to Use New Room Base

Refactor TttRoom to extend Room properly.

**Key changes:**
1. Add constructor that calls `super(matchId, "tictactoe", scheduler)`
2. Change `this.id` to `this.matchId` (inherit from Room)
3. Everything else stays identical

---

## Phase 4: Regression Testing

**Test Tic-Tac-Toe still works exactly the same:**

1. Start server: `java -jar ttt-server-1.0.0.jar`
2. Join game: `POST /api/join` with sessionId and name
3. Make move: `POST /api/move` with cell position
4. Verify: Game ends correctly, scores saved, database updated
5. Verify: No changes to WebSocket behavior, timeout logic, forfeit logic

**No Defold changes needed yet.** The Defold client continues using existing `/api/join` endpoint.

---

## Phase 5: Concurrent Play Support (Future, Optional)

Once all phases 1-4 are working, add support for multiple active games per player.

**What changes:**
- `PlayerSession.currentMatch` → `Map<Integer, String> currentMatches` (table_number → matchId)
- GameService: Check if player has game at table_number before joining
- database: `player_current_matches` table tracks all active games per player

**This is isolated and can be added last.**

---

## Architecture Diagram - After Modularization

```
Main.java
  │
  ├─ DatabaseManager (with new tables)
  │   └─ ConnectionPool (10 connections, reused)
  │
  ├─ GameService
  │   ├─ Creates games via RoomFactory
  │   └─ Routes to correct game type
  │
  ├─ RoomFactory
  │   ├─ GameType enum
  │   └─ Instantiates Room subclasses
  │
  ├─ Room (Abstract)
  │   ├─ TttRoom (Tic-Tac-Toe) ← extends Room
  │   ├─ PuzzleRoom (Puzzle) ← extends Room
  │   ├─ PingPongRoom (Ping Pong) ← extends Room
  │   ├─ DuckHuntRoom (Duck Hunt) ← extends Room
  │   └─ SpaceShooterRoom (Space Shooter) ← extends Room
  │
  ├─ GameStateCache
  │   ├─ Tracks gameId for each match
  │   └─ Handles concurrent games (future)
  │
  └─ WebSocketNotifier (unchanged)
```

---

## Why This Approach?

### ✅ Minimal Disruption
- Existing Tic-Tac-Toe code barely changes
- New games are additions, not replacements
- Threading model unchanged (still 8-thread pool)
- Connection pooling unchanged

### ✅ Reuses Existing Infrastructure
- Same DatabaseManager → one ConnectionPool
- Same GameService executor threads
- Same WebSocketNotifier retry logic
- Same RestApiServer (routes through same /api/join)

### ✅ Modular & Extensible
- Adding game 7? Create `CustomRoom extends Room` + one line in RoomFactory
- New scoring system? Add row to `scoring_formulas` table
- New player stat? Add column to `player_stats` table

### ✅ Testable
- Phase 2 test: Tic-Tac-Toe still works identically
- Phase 3 test: Add Puzzle, verify separate stats
- Phase 4 test: Add Ping Pong, verify multiplayer
- Etc.

### ✅ Scalable
- Map-based `PlayerSession.currentMatches` → supports 6+ tables
- Concurrent games run in same thread pool (no new threads)
- Database indexed on (session_id, game_id) for fast queries

---

## Summary: What I'll Build

| Phase | What | Files | Estimated Impact |
|-------|------|-------|------------------|
| 1 | Database + core abstractions | 4 new, 4 modified | Schema migration, minimal code changes |
| 2 | Placeholder games | 4 new | Scaffolds for testing |
| 3 | TttRoom refactor | 1 modified | Tiny change, behavior preserved |
| 4 | Regression test | Manual | Verify Tic-Tac-Toe unchanged |
| 5 | Concurrent play | 2 modified | Optional, added last |

---

## Files to Create

1. `GameType.java` - Enum
2. `Room.java` - Abstract base
3. `GameResult.java` - Result DTO
4. `RoomFactory.java` - Factory
5. `PuzzleRoom.java` - Placeholder
6. `PingPongRoom.java` - Placeholder
7. `DuckHuntRoom.java` - Placeholder
8. `SpaceShooterRoom.java` - Placeholder

## Files to Modify

1. `DatabaseManager.java` - Add new tables
2. `GameStateCache.java` - Add gameId tracking
3. `GameService.java` - Route via factory
4. `TttRoom.java` - Extend Room

## Files Unchanged

- `Main.java` - No changes
- `Server.java` - No changes
- `RestApiServer.java` - No changes (yet)
- `ConnectionPool.java` - No changes
- `WebSocketNotifier.java` - No changes
- `ClientSession.java` - No changes
- All Defold files - No changes (yet)

---

## Ready?

Once you confirm this plan looks good, I'll:

1. Create all 8 new files
2. Modify the 4 target files
3. Test that Tic-Tac-Toe works exactly the same
4. Document any issues

Sound good? 🚀
