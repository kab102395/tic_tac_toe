package com.stanstate.ttt;

/**
 * Data transfer object for game results.
 * Holds the outcome of a completed game including winner, scores, and result type.
 */
public class GameResult {
    private String matchId;
    private GameType gameType;
    private String player1;
    private String player2;
    private String result;        // "X", "O", "draw", "forfeit:X", etc.
    private int player1Score;     // Raw score from the game
    private int player2Score;     // Raw score from the game
    private long durationSeconds; // Game duration in seconds

    public GameResult(String matchId, GameType gameType, String player1, String player2,
                      String result, int player1Score, int player2Score, long durationSeconds) {
        this.matchId = matchId;
        this.gameType = gameType;
        this.player1 = player1;
        this.player2 = player2;
        this.result = result;
        this.player1Score = player1Score;
        this.player2Score = player2Score;
        this.durationSeconds = durationSeconds;
    }

    public String getMatchId() {
        return matchId;
    }

    public GameType getGameType() {
        return gameType;
    }

    public String getPlayer1() {
        return player1;
    }

    public String getPlayer2() {
        return player2;
    }

    public String getResult() {
        return result;
    }

    public int getPlayer1Score() {
        return player1Score;
    }

    public int getPlayer2Score() {
        return player2Score;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * Determine the winner based on the result string.
     * @return "player1", "player2", "draw", or "none" if still ongoing
     */
    public String getWinner() {
        if (result == null) return "none";
        if (result.contains("1")) return "player1";
        if (result.contains("2")) return "player2";
        if (result.contains("draw")) return "draw";
        if (result.contains("forfeit")) {
            // For TTT: "forfeit:X" means X forfeited, so O (player2) wins
            if (result.contains("X")) return "player2";
            if (result.contains("O")) return "player1";
        }
        return "none";
    }

    @Override
    public String toString() {
        return "GameResult{" +
                "matchId='" + matchId + '\'' +
                ", gameType=" + gameType +
                ", result='" + result + '\'' +
                ", player1Score=" + player1Score +
                ", player2Score=" + player2Score +
                ", durationSeconds=" + durationSeconds +
                '}';
    }
}
