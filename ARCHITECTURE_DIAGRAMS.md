# Multi-Game Architecture - Visual Overview

## Current Architecture (Tic-Tac-Toe Only)

```
┌─────────────────────────────────────────────────────────────────┐
│                           Main.java                             │
│                    (Bootstrap & Initialization)                 │
└──────────────────┬────────────────────────────────┬─────────────┘
                   │                                │
                   ▼                                ▼
        ┌──────────────────────┐         ┌──────────────────────┐
        │  WebSocket Server    │         │   HTTP REST API      │
        │   (Port 8080)        │         │   (Port 8081)        │
        └──────────┬───────────┘         └──────────┬───────────┘
                   │                                │
                   └────────────┬───────────────────┘
                                │
                                ▼
                        ┌───────────────────────┐
                        │   GameService (8      │
                        │   thread pool)        │
                        └─────┬─────────────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
        ┌──────────┐   ┌──────────────┐   ┌──────────────┐
        │ TttRoom  │   │GameStateCache│   │ Database     │
        │(Hardcoded)  │              │   │ Manager      │
        └──────────┘   └──────────────┘   └──────────────┘
                            │                      │
                            ▼                      ▼
                      ┌─────────────┐       ┌───────────┐
                      │ConcurrentHM │       │ConnectionPool
                      │(1 game type)│       │(10 conns) │
                      └─────────────┘       └───────────┘
                                                  │
                                                  ▼
                                            ┌───────────┐
                                            │SQLite DB  │
                                            │(1 file)   │
                                            └───────────┘

PROBLEM: TttRoom is hardcoded for Tic-Tac-Toe only
         Can't add other games without duplicating code
```

---

## After Modularization (Multi-Game)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Main.java (SAME)                              │
└──────────────┬──────────────────────────────────┬──────────────────────┬┘
               │                                  │                      │
               ▼                                  ▼                      ▼
    ┌────────────────────┐            ┌────────────────────┐  ┌─────────────────┐
    │ WebSocket Server   │            │   HTTP REST API    │  │ Game Registry   │
    │   (Port 8080)      │            │   (Port 8081)      │  │  (NEW: queries  │
    └────────┬───────────┘            └────────┬───────────┘  │ /api/games)     │
             │                                 │              └─────────────────┘
             └─────────────────┬───────────────┘
                               │
                               ▼
                    ┌──────────────────────────┐
                    │    GameService           │
                    │   (8 thread pool)        │
                    └────────┬─────────────────┘
                             │
                             ▼
                    ┌──────────────────────────┐
                    │    RoomFactory (NEW)     │
                    │  (Routes to correct game)│
                    └──────┬──────────────────┘
                           │
      ┌────────┬────────┬──┴───┬────────┬──────────┐
      │        │        │      │        │          │
      ▼        ▼        ▼      ▼        ▼          ▼
  ┌────────┐┌────────┐┌──────┐┌─────┐┌────────┐┌──────┐
  │TttRoom ││Puzzle  ││PingP ││Duck ││Space   ││Future│
  │extends ││Room   ││Pong  ││Hunt ││Shooter ││  etc │
  │  Room  ││ext.   ││ext.  ││ext. ││  ext.  ││      │
  └────────┘└────────┘└──────┘└─────┘└────────┘└──────┘
      ▲        ▲         ▲        ▲        ▲           ▲
      │        │         │        │        │           │
      └────────┴─────────┴────────┴────────┴───────────┘
               (All inherit from Room abstract base)
               
                         ▼▼▼
                    
             ┌─────────────────────────────┐
             │  GameStateCache (ENHANCED)  │
             │  Now tracks game_type per   │
             │  match for routing          │
             └──────────┬──────────────────┘
                        │
           ┌────────────┴────────────┐
           │                         │
           ▼                         ▼
    ┌────────────────────┐  ┌──────────────────────┐
    │ConcurrentHashMap:  │  │ DatabaseManager      │
    │activeGames         │  │ (ENHANCED with new   │
    │activeSessions      │  │  tables & game_id)   │
    │                    │  └──────────┬───────────┘
    │Map<matchId,        │            │
    │    GameState>      │            ▼
    │                    │  ┌──────────────────────┐
    │(games with         │  │  ConnectionPool      │
    │ game_type field)   │  │  (SAME: 10 conns)    │
    └────────────────────┘  └──────────┬───────────┘
                                       │
                                       ▼
                            ┌────────────────────┐
                            │   SQLite DB        │
                            │   (SAME file: +4   │
                            │    new tables)     │
                            │                    │
                            │ Tables:            │
                            │ ✓ games            │
                            │ ✓ player_stats     │
                            │ ✓ scoring_formulas │
                            │ ✓ player_current_  │
                            │   matches          │
                            │ ✓ (+ all existing) │
                            └────────────────────┘

SOLUTION: Room is abstract base class
          All games extend Room
          Factory routes to correct game type
          Same thread pool, same connection pool
          Same database, just organized better
```

---

## Data Flow Comparison

### BEFORE (Tic-Tac-Toe only)

```
Client (Defold)
    │
    ├─ POST /api/join
    │     │
    │     ▼
    │  RestApiServer
    │     │
    │     ▼
    │  GameService.joinGame()
    │     │
    │     ▼
    │  GameStateCache (stores match)
    │     │
    │     ▼
    │  TttRoom CREATED (hardcoded)
    │     │
    │     └─ Only TttRoom possible
    │
    └─ POST /api/move
         │
         ▼
      RestApiServer
         │
         ▼
      GameService.makeMove()
         │
         ▼
      TttRoom.onMove() (hardcoded)
         │
         ▼
      Win check (bitmask)
         │
         ▼
      WebSocket notify
         │
         ▼
      Client (Defold) receives update
```

### AFTER (Multi-Game)

```
Client (Defold)
    │
    ├─ POST /api/join?gameType=tictactoe  (or puzzle, etc)
    │     │
    │     ▼
    │  RestApiServer (passes gameType)
    │     │
    │     ▼
    │  GameService.joinGame(gameType)
    │     │
    │     ▼
    │  GameStateCache (stores match + gameType)
    │     │
    │     ▼
    │  RoomFactory.createRoom(gameType)
    │     │
    │     ├─ If TICTACTOE → TttRoom
    │     ├─ If PUZZLE → PuzzleRoom
    │     ├─ If PINGPONG → PingPongRoom
    │     ├─ If DUCKHUNT → DuckHuntRoom
    │     ├─ If SPACESHOOTER → SpaceShooterRoom
    │     └─ If CUSTOM → CustomRoom
    │
    └─ POST /api/move
         │
         ▼
      RestApiServer
         │
         ▼
      GameService.makeMove()
         │
         ▼
      GameStateCache.getGame()
         │
         ▼
      Determine Room type from gameType
         │
         ▼
      Call correct Room.onMove()
         │
         ├─ TttRoom.onMove() (bitmask check)
         ├─ PuzzleRoom.onMove() (piece collect)
         ├─ PingPongRoom.onMove() (rally count)
         ├─ DuckHuntRoom.onMove() (target hit)
         └─ SpaceShooterRoom.onMove() (enemy kill)
         │
         ▼
      Save result to database
         │
         ▼
      Update player_stats table
         │
         ▼
      WebSocket notify
         │
         ▼
      Client (Defold) receives update
```

---

## Database Schema Before & After

### BEFORE

```
player_sessions:
├─ session_id
├─ player_name
├─ connected_at
├─ last_heartbeat
├─ connection_status
└─ retry_count

game_matches:
├─ match_id
├─ player1_session → player_sessions
├─ player2_session → player_sessions
├─ board (Tic-Tac-Toe specific!)
├─ current_turn
├─ status
└─ result

game_moves:
├─ move_id
├─ match_id → game_matches
├─ session_id → player_sessions
├─ cell_position (Tic-Tac-Toe specific!)
├─ mark
└─ timestamp

player_stats:
├─ player_name
├─ total_games
├─ wins
├─ losses
└─ draws

(All hardcoded for Tic-Tac-Toe)
```

### AFTER (Enhanced)

```
games (NEW):
├─ game_id (PK)
├─ game_name
├─ is_multiplayer
└─ max_players

player_stats (NEW FIELDS):
├─ stat_id
├─ player_name
├─ game_id → games  ⭐ Track per-game stats
├─ total_games
├─ wins
├─ losses
├─ draws
├─ avg_normalized_score
└─ highest_score

scoring_formulas (NEW):
├─ formula_id
├─ game_id → games
├─ raw_score_min (Puzzle: 0-100, Shooter: 0-200, etc)
├─ raw_score_max
└─ calculation_type

player_current_matches (NEW):
├─ id
├─ session_id → player_sessions  ⭐ Can have up to 6
├─ table_number (0-5)
├─ match_id
└─ game_id → games  ⭐ Track which game at each table

game_matches (ENHANCED):
├─ match_id
├─ player1_session → player_sessions
├─ player2_session → player_sessions
├─ game_id → games  ⭐ NEW: Which game type
├─ board (generic now, per-game uses differently)
├─ current_turn
├─ status
└─ result

game_moves (ENHANCED):
├─ move_id
├─ match_id → game_matches
├─ session_id → player_sessions
├─ game_id → games  ⭐ NEW: Which game
├─ move_data (generic: cell_position, paddle_y, etc)
└─ timestamp

(All backward compatible - Tic-Tac-Toe defaults to game_id='tictactoe')
```

---

## Threading Model (UNCHANGED)

```
Main.java
  │
  └─ GameService
      └─ ExecutorService.newFixedThreadPool(8)
          │
          ├─ Thread 1: Processing joins/moves
          ├─ Thread 2: Processing joins/moves
          ├─ Thread 3: Processing joins/moves
          ├─ Thread 4: Processing joins/moves
          ├─ Thread 5: Processing joins/moves
          ├─ Thread 6: Processing joins/moves
          ├─ Thread 7: Processing joins/moves
          └─ Thread 8: Processing joins/moves

BEFORE: All 8 threads run TttRoom logic
AFTER:  All 8 threads run any Room subclass logic

Result: NO CHANGE to threading model
        Same concurrency guarantees
        Same performance characteristics
```

---

## Connection Pool (UNCHANGED)

```
DatabaseManager.getInstance()
  │
  └─ ConnectionPool.getInstance()
      └─ BlockingQueue<Connection> (max 10)
          │
          ├─ Connection 1: Available
          ├─ Connection 2: In use by Thread 3
          ├─ Connection 3: In use by Thread 1
          ├─ Connection 4: Available
          ├─ Connection 5: In use by Thread 7
          ├─ Connection 6: Available
          ├─ Connection 7: Available
          ├─ Connection 8: In use by Thread 2
          ├─ Connection 9: In use by Thread 5
          └─ Connection 10: Available

BEFORE: All 10 connections query Tic-Tac-Toe data
AFTER:  All 10 connections query ANY game data (with game_id filter)

Result: NO CHANGE to connection pooling
        Same reuse mechanism
        Same SQLite concurrency handling
```

---

## Modular Game Instance Lifecycle

```
Game Creation:
┌────────────────────────────────────────────────────────────────┐
│ Client POST /api/join?gameType=puzzle                          │
│                                                                 │
│ RestApiServer                                                  │
│ ├─ Parse gameType from request                               │
│ ├─ Call GameService.joinGame(sessionId, name, "puzzle")      │
│ │                                                              │
│ │ GameService (in thread pool)                               │
│ │ ├─ Check lobby (synchronized lobbyLock)                    │
│ │ ├─ Create matchId                                          │
│ │ ├─ Call GameStateCache.createGame(matchId, "puzzle", ...)│
│ │ │                                                           │
│ │ │ GameStateCache                                           │
│ │ │ ├─ Store GameState with gameId="puzzle"                │
│ │ │ ├─ Call RoomFactory.createRoom(matchId, "puzzle", ...)│
│ │ │ │                                                        │
│ │ │ │ RoomFactory                                           │
│ │ │ │ └─ return new PuzzleRoom(matchId, scheduler)        │
│ │ │ │                                                        │
│ │ │ └─ Store Room reference                                │
│ │ │                                                           │
│ │ └─ Return matchId to client                               │
│ │                                                              │
│ └─ Client receives matchId and connects WebSocket            │
│                                                                 │
└────────────────────────────────────────────────────────────────┘

Move Processing:
┌────────────────────────────────────────────────────────────────┐
│ Client POST /api/move with matchId and moveData               │
│                                                                 │
│ RestApiServer                                                  │
│ ├─ Parse matchId, moveData                                    │
│ ├─ Call GameService.makeMove(sessionId, matchId, moveData)   │
│ │                                                              │
│ │ GameService (in thread pool)                               │
│ │ ├─ Call GameStateCache.getGame(matchId)                   │
│ │ ├─ Get gameId from GameState                              │
│ │ ├─ Get Room from cache                                    │
│ │ ├─ Call room.onMove(player, moveData)                     │
│ │ │                                                           │
│ │ │ (Correct Room subclass handles it)                      │
│ │ │ ├─ TttRoom:         cell_position → bitmask check      │
│ │ │ ├─ PuzzleRoom:      piece_id → increment counter       │
│ │ │ ├─ PingPongRoom:    paddle_y → rally count             │
│ │ │ ├─ DuckHuntRoom:    shot_pos → target check            │
│ │ │ └─ SpaceShooterRoom: shot_pos → enemy check            │
│ │ │                                                           │
│ │ ├─ Check if game ended                                    │
│ │ │                                                           │
│ │ └─ If ended: Save result to database                      │
│ │     ├─ Insert game_matches row                            │
│ │     ├─ Insert game_moves rows                             │
│ │     └─ Update player_stats for both players               │
│ │                                                              │
│ └─ WebSocketNotifier broadcasts state to both players        │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

---

## Backward Compatibility

```
Existing Tic-Tac-Toe Clients:
┌─────────────────────────────────────────────────────────────┐
│ Old Defold client calls:                                    │
│ POST /api/join {sessionId, name}                           │
│                                                             │
│ NEW behavior:                                              │
│ └─ DefaultgameType = "tictactoe"                           │
│    └─ Game stored with game_id="tictactoe"                │
│       └─ RoomFactory routes to TttRoom                    │
│          └─ IDENTICAL behavior to before!                 │
│                                                             │
│ Result: ✅ No client changes needed                        │
│         ✅ Old behavior 100% preserved                     │
│         ✅ Can migrate to new API at own pace              │
└─────────────────────────────────────────────────────────────┘

New Multi-Game Clients:
┌─────────────────────────────────────────────────────────────┐
│ New Defold client calls:                                    │
│ POST /api/join?gameType=puzzle {sessionId, name}           │
│                                                             │
│ NEW behavior:                                              │
│ └─ Game stored with game_id="puzzle"                       │
│    └─ RoomFactory routes to PuzzleRoom                    │
│       └─ NEW game instance!                               │
│                                                             │
│ Result: ✅ New features available                          │
│         ✅ Can mix old and new clients                     │
└─────────────────────────────────────────────────────────────┘
```

---

## What Stays The Same

| Component | Before | After | Change |
|-----------|--------|-------|--------|
| Threading | 8-thread ExecutorService | 8-thread ExecutorService | ✅ NONE |
| Connection Pool | Max 10 SQLite connections | Max 10 SQLite connections | ✅ NONE |
| WebSocket Server | Netty on 8080 | Netty on 8080 | ✅ NONE |
| HTTP Server | Spark on 8081 | Spark on 8081 | ✅ NONE |
| Database File | `database/ttt_game.db` | `database/ttt_game.db` | ✅ NONE |
| Retry Logic | Exponential backoff | Exponential backoff | ✅ NONE |
| Message Format | JSON | JSON | ✅ NONE |
| Port 8080 | WebSocket | WebSocket | ✅ NONE |
| Port 8081 | HTTP REST | HTTP REST | ✅ NONE |

---

## What Changes

| Component | Before | After | Change |
|-----------|--------|-------|--------|
| Game Logic | Hardcoded TttRoom | Abstract Room + subclasses | 🔄 Modularized |
| Game Routing | Direct TttRoom creation | RoomFactory.createRoom() | 🔄 Routed |
| Database | 6 tables | 10 tables | 🔄 Enhanced |
| Game Type | Implicit (only Tic-Tac-Toe) | Explicit (game_id field) | 🔄 Explicit |
| Player Stats | All games merged | Per-game stats | 🔄 Separated |
| Concurrent Play | Not supported | Supported (future) | 🔄 Optional |

---

This modularization maintains 100% backward compatibility while enabling unlimited game types!
