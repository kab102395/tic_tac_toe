# Implementation Checklist & Documents

## 📋 Documents Created

- ✅ **MODULARIZATION_PLAN.md** - Complete step-by-step implementation plan with exact code snippets
- ✅ **ARCHITECTURE_DIAGRAMS.md** - Visual diagrams showing before/after architecture
- ✅ **THIS FILE** - Checklist and summary

---

## 🎯 What You're About To Get

### Phase 1: Core Modularization
- 8 new Java files (core abstractions + placeholder games)
- 4 modified Java files (minimal, non-breaking changes)
- Full schema migration (SQLite, backward compatible)
- Zero changes to threading/pooling/WebSocket layer

### Phase 2: Regression Testing
- Verify Tic-Tac-Toe works identically
- No behavioral changes to existing client
- All scores saved correctly
- Database queries optimized

### Phase 3: Future Enhancements
- Concurrent play support (optional)
- New game types (just extend Room + factory change)
- Defold campus quad UI (when ready)

---

## 📁 Files to Create (Phase 1A)

**Location:** `java-server/src/main/java/com/stanstate/ttt/`

```
NEW FILES:
├─ GameType.java                  (Enum: TICTACTOE, PUZZLE, PING_PONG, etc)
├─ Room.java                       (Abstract base for all games)
├─ GameResult.java                 (DTO for game outcomes)
├─ RoomFactory.java                (Factory: gameId → Room subclass)
├─ PuzzleRoom.java                 (Placeholder game impl)
├─ PingPongRoom.java               (Placeholder game impl)
├─ DuckHuntRoom.java               (Placeholder game impl)
└─ SpaceShooterRoom.java           (Placeholder game impl)
```

---

## 📝 Files to Modify (Phase 1B)

**Location:** `java-server/src/main/java/com/stanstate/ttt/`

```
MODIFIED FILES:
├─ DatabaseManager.java
│  └─ Add 4 new CREATE TABLE statements in createFreshDatabase()
│     └─ games, player_stats, scoring_formulas, player_current_matches
│  └─ Add initialization data for 6 games + scoring formulas
│  └─ Alter existing tables to add game_id field
│
├─ GameStateCache.java
│  └─ Add gameId field to GameState inner class
│  └─ Update createGame() signature to include gameId parameter
│  └─ Impact: 2 changes, fully backward compatible
│
├─ GameService.java
│  └─ Route game creation through RoomFactory
│  └─ Change: Use RoomFactory.createRoom(matchId, gameId, scheduler)
│  └─ Impact: Localized to game creation logic
│
└─ TttRoom.java
   └─ Change: extends Room (was standalone)
   └─ Add: Constructor calling super(matchId, "tictactoe", scheduler)
   └─ Add: Implement abstract methods from Room
   └─ Impact: All existing logic unchanged, just wrapped
```

---

## 🔍 What Stays 100% Unchanged

- ✅ Main.java
- ✅ Server.java (WebSocket)
- ✅ RestApiServer.java (HTTP REST)
- ✅ ConnectionPool.java (database pooling)
- ✅ WebSocketNotifier.java (notifications)
- ✅ ClientSession.java
- ✅ All Defold files (for now)
- ✅ Threading model (8-thread pool)
- ✅ All ports (8080, 8081)
- ✅ Database file location

---

## 🎮 Game Placeholder Scoring (Easy Testing)

```
Tic-Tac-Toe:
├─ Win = 10 points
├─ Draw = 5 points
└─ Loss = 0 points

Puzzle:
├─ Pieces collected: 0-100
└─ Max score: 100

Ping Pong:
├─ Rally count: 0-1000
└─ Winner at 100 rallies

Duck Hunt:
├─ Targets hit: 0-50
└─ Max targets: 50

Space Shooter:
├─ Enemies destroyed: 0-200
└─ Max enemies: 200
```

All automatically normalized to 0-100 range for comparison.

---

## ✅ Pre-Implementation Checklist

Before I write the code, confirm:

- [ ] You've read **MODULARIZATION_PLAN.md**
- [ ] You've reviewed **ARCHITECTURE_DIAGRAMS.md**
- [ ] You understand the changes won't break Tic-Tac-Toe
- [ ] You're ready to test regression (Tic-Tac-Toe still works)
- [ ] You want map-based concurrent play support
- [ ] You agree with single SQLite database approach
- [ ] You're ready for Phase 1 implementation

---

## 🚀 Next Steps (When You're Ready)

1. **Confirm above checklist** - Reply with approval
2. **I'll create all 8 new files** - Copy-paste ready, fully documented
3. **I'll modify the 4 files** - Show exact diffs, explain each change
4. **You build & test** - `gradle build` then `java -jar ...`
5. **Verify Tic-Tac-Toe works** - Same behavior as before
6. **Then Phase 2** - Add placeholder games one-by-one
7. **Then Phase 3** - Optional concurrent play support
8. **Then Phase 4** - Defold campus quad when you're ready

---

## 📊 Expected Timeline

| Phase | Task | Time |
|-------|------|------|
| 1A | Create 8 new files | ~30 min |
| 1B | Modify 4 files | ~30 min |
| Build | Gradle build | ~2-5 min |
| Test | Regression test TTT | ~10 min |
| **TOTAL** | **To working multi-game baseline** | **~1-1.5 hours** |

---

## 🎯 Success Criteria

After Phase 1 & 2, you'll have:

✅ Modular game architecture
✅ 5 placeholder games ready to extend
✅ Tic-Tac-Toe works identically to before
✅ Single SQLite with game_type tracking
✅ Reused threading/pooling/WebSocket
✅ Foundation for campus quad UI
✅ Foundation for concurrent play

---

**Ready? Just say the word and I'll create all the code! 🚀**
