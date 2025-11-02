package com.stanstate.ttt;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Factory for creating game rooms based on game type.
 * Routes GameType enum to the correct Room implementation.
 * 
 * Adding a new game: 
 * 1. Create a class extending Room (e.g., CustomGameRoom)
 * 2. Add case to createRoom() below
 * 3. Add entry to GameType enum
 * 4. Done! No other files need modification.
 */
public class RoomFactory {
    private final ScheduledExecutorService scheduler;

    public RoomFactory(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Create a room instance for the specified game type.
     * @param gameType Type of game to create
     * @param matchId Unique match identifier
     * @return Room instance ready for gameplay
     */
    public Room createRoom(GameType gameType, String matchId) {
        switch (gameType) {
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
            
            case CUSTOM:
                // For now, default to Tic-Tac-Toe for custom games
                return new TttRoom(matchId, scheduler);
            
            default:
                // Fallback to Tic-Tac-Toe if unknown type
                return new TttRoom(matchId, scheduler);
        }
    }

    /**
     * Create a room from a string game type ID.
     * @param gameTypeId String ID (e.g., "tictactoe")
     * @param matchId Unique match identifier
     * @return Room instance
     */
    public Room createRoom(String gameTypeId, String matchId) {
        GameType gameType = GameType.fromId(gameTypeId);
        return createRoom(gameType, matchId);
    }
}
