package com.stanstate.ttt;

/**
 * Enum defining all supported game types in the multi-game platform.
 * Each game type maps to a specific Room implementation.
 */
public enum GameType {
    TICTACTOE("tictactoe", "Tic-Tac-Toe", 120),
    PUZZLE("puzzle", "Puzzle", 300),
    PING_PONG("pingpong", "Ping Pong", 180),
    DUCK_HUNT("duckhunt", "Duck Hunt", 120),
    SPACE_SHOOTER("spaceshooter", "Space Shooter", 180),
    CUSTOM("custom", "Custom Game", 240);

    private final String id;
    private final String displayName;
    private final int defaultTimeoutSeconds;

    GameType(String id, String displayName, int defaultTimeoutSeconds) {
        this.id = id;
        this.displayName = displayName;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    /**
     * Convert string ID to GameType enum.
     * @param id String identifier (e.g., "tictactoe")
     * @return GameType enum or TICTACTOE as default
     */
    public static GameType fromId(String id) {
        if (id == null) return TICTACTOE;
        for (GameType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return TICTACTOE; // Default to Tic-Tac-Toe if unknown
    }
}
