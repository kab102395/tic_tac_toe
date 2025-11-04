package com.stanstate.ttt;

import java.sql.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:database/ttt_game.db";
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private static DatabaseManager instance;
    private final ScheduledExecutorService cleanupScheduler;
    private ConnectionPool connectionPool;
    
    private DatabaseManager() {
        // Private constructor for singleton
        this.cleanupScheduler = Executors.newScheduledThreadPool(1);
        try {
            this.connectionPool = ConnectionPool.getInstance();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize connection pool", e);
        }
        startCleanupTask();
    }
    
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }
    
    public void initializeDatabase() {
        try (Connection conn = connectionPool.getConnection()) {
            // Check if database exists and get version
            int currentVersion = getDatabaseVersion(conn);
            System.out.println("Current database version: " + currentVersion);
            
            if (currentVersion == 0) {
                // Create fresh database with new schema
                createFreshDatabase(conn);
            } else {
                // Migrate existing database
                migrateDatabase(conn, currentVersion);
            }
            
            System.out.println("Database initialized successfully with enhanced schema");
        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private int getDatabaseVersion(Connection conn) {
        try {
            // Create version table if it doesn't exist
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY
                )
            """);
            
            PreparedStatement stmt = conn.prepareStatement("SELECT version FROM schema_version ORDER BY version DESC LIMIT 1");
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("version");
            }
            return 0;
        } catch (SQLException e) {
            return 0;
        }
    }
    
    private void createFreshDatabase(Connection conn) throws SQLException {
        System.out.println("Creating fresh database with enhanced schema...");
        
        // Enhanced player sessions table with connection tracking
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS player_sessions (
                session_id TEXT PRIMARY KEY,
                player_name TEXT NOT NULL,
                connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                websocket_id TEXT,
                connection_status TEXT DEFAULT 'connected',
                retry_count INTEGER DEFAULT 0
            )
        """);
        
        // Enhanced game matches table with state tracking
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS game_matches (
                match_id TEXT PRIMARY KEY,
                player1_session TEXT,
                player2_session TEXT,
                board TEXT DEFAULT '.........',
                current_turn TEXT DEFAULT 'X',
                status TEXT DEFAULT 'waiting',
                result TEXT DEFAULT 'ongoing',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_move_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                state_version INTEGER DEFAULT 1,
                FOREIGN KEY (player1_session) REFERENCES player_sessions(session_id),
                FOREIGN KEY (player2_session) REFERENCES player_sessions(session_id)
            )
        """);
        
        // Enhanced game moves table with validation
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS game_moves (
                move_id INTEGER PRIMARY KEY AUTOINCREMENT,
                match_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                cell_position INTEGER NOT NULL,
                mark TEXT NOT NULL,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                state_version INTEGER DEFAULT 1,
                validated BOOLEAN DEFAULT FALSE,
                FOREIGN KEY (match_id) REFERENCES game_matches(match_id),
                FOREIGN KEY (session_id) REFERENCES player_sessions(session_id)
            )
        """);
        
        // Enhanced pending notifications with retry logic
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS pending_notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                notification_type TEXT NOT NULL,
                data TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                attempts INTEGER DEFAULT 0,
                max_attempts INTEGER DEFAULT 3,
                next_retry TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                delivered BOOLEAN DEFAULT FALSE,
                FOREIGN KEY (session_id) REFERENCES player_sessions(session_id)
            )
        """);
        
        // Connection health monitoring table
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS connection_health (
                session_id TEXT PRIMARY KEY,
                last_ping TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_pong TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                ping_count INTEGER DEFAULT 0,
                missed_pings INTEGER DEFAULT 0,
                connection_quality REAL DEFAULT 1.0,
                FOREIGN KEY (session_id) REFERENCES player_sessions(session_id)
            )
        """);
        
        // Lobby persistence table
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS lobby_state (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                waiting_player_id TEXT,
                waiting_player_name TEXT,
                waiting_since TIMESTAMP,
                FOREIGN KEY (waiting_player_id) REFERENCES player_sessions(session_id)
            )
        """);
        
        // Overall player statistics table for aggregate tracking
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS player_stats (
                player_name TEXT PRIMARY KEY,
                total_games INTEGER DEFAULT 0,
                wins INTEGER DEFAULT 0,
                losses INTEGER DEFAULT 0,
                draws INTEGER DEFAULT 0,
                win_rate REAL DEFAULT 0.0,
                last_game TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        // Per-game-type statistics table for tracking separate stats
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS game_stats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_name TEXT NOT NULL,
                game_type TEXT NOT NULL,
                games_played INTEGER DEFAULT 0,
                wins INTEGER DEFAULT 0,
                losses INTEGER DEFAULT 0,
                draws INTEGER DEFAULT 0,
                win_rate REAL DEFAULT 0.0,
                last_played TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(player_name, game_type),
                FOREIGN KEY (player_name) REFERENCES player_stats(player_name)
            )
        """);
        
        // Initialize lobby state
        conn.createStatement().execute("""
            INSERT OR IGNORE INTO lobby_state (id, waiting_player_id, waiting_player_name, waiting_since) 
            VALUES (1, NULL, NULL, NULL)
        """);
        
        // Set database version
        conn.createStatement().execute("INSERT INTO schema_version (version) VALUES (3)");
        System.out.println("Fresh database created with version 3");
    }
    
    private void migrateDatabase(Connection conn, int currentVersion) throws SQLException {
        System.out.println("Migrating database from version " + currentVersion + " to version 3...");
        
        if (currentVersion < 2) {
            // Add missing columns to existing tables
            try {
                conn.createStatement().execute("ALTER TABLE player_sessions ADD COLUMN connection_status TEXT DEFAULT 'connected'");
                System.out.println("Added connection_status to player_sessions");
            } catch (SQLException e) {
                // Column might already exist
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE player_sessions ADD COLUMN retry_count INTEGER DEFAULT 0");
                System.out.println("Added retry_count to player_sessions");
            } catch (SQLException e) {
                // Column might already exist
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE pending_notifications ADD COLUMN attempts INTEGER DEFAULT 0");
                System.out.println("Added attempts to pending_notifications");
            } catch (SQLException e) {
                // Column might already exist
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE pending_notifications ADD COLUMN delivered BOOLEAN DEFAULT FALSE");
                System.out.println("Added delivered to pending_notifications");
            } catch (SQLException e) {
                // Column might already exist
            }
            
            try {
                conn.createStatement().execute("ALTER TABLE game_matches ADD COLUMN result TEXT DEFAULT 'ongoing'");
                System.out.println("Added result to game_matches");
            } catch (SQLException e) {
                // Column might already exist
            }
            
            // Create new tables if they don't exist
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS connection_health (
                    session_id TEXT PRIMARY KEY,
                    last_ping TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_pong TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    ping_count INTEGER DEFAULT 0,
                    missed_pings INTEGER DEFAULT 0,
                    connection_quality REAL DEFAULT 1.0
                )
            """);
            
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS lobby_state (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    waiting_player_id TEXT,
                    waiting_player_name TEXT,
                    waiting_since TIMESTAMP
                )
            """);
            
            conn.createStatement().execute("""
                INSERT OR IGNORE INTO lobby_state (id, waiting_player_id, waiting_player_name, waiting_since) 
                VALUES (1, NULL, NULL, NULL)
            """);
        }
        
        if (currentVersion < 3) {
            // Add player statistics table for version 3
            System.out.println("Adding player statistics table for version 3...");
            
            try {
                conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        player_name TEXT PRIMARY KEY,
                        total_games INTEGER DEFAULT 0,
                        wins INTEGER DEFAULT 0,
                        losses INTEGER DEFAULT 0,
                        draws INTEGER DEFAULT 0,
                        win_rate REAL DEFAULT 0.0,
                        last_game TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);
                System.out.println("Player statistics table created successfully");
            } catch (SQLException e) {
                System.err.println("Failed to create player_stats table: " + e.getMessage());
            }
            
            try {
                conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS game_stats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_name TEXT NOT NULL,
                        game_type TEXT NOT NULL,
                        total_games INTEGER DEFAULT 0,
                        wins INTEGER DEFAULT 0,
                        losses INTEGER DEFAULT 0,
                        draws INTEGER DEFAULT 0,
                        win_rate REAL DEFAULT 0.0,
                        last_game TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(player_name, game_type),
                        FOREIGN KEY (player_name) REFERENCES player_stats(player_name)
                    )
                """);
                System.out.println("Game statistics table created successfully");
            } catch (SQLException e) {
                System.err.println("Failed to create game_stats table: " + e.getMessage());
            }
        }
        
        // Update version to 3
        try {
            conn.createStatement().execute("INSERT INTO schema_version (version) VALUES (3)");
            System.out.println("Database migrated to version 3");
        } catch (SQLException e) {
            System.err.println("Failed to update schema version: " + e.getMessage());
        }
    }
    
    public Connection getConnection() throws SQLException {
        return connectionPool.getConnection();
    }
    
    public ReadWriteLock getLock() {
        return lock;
    }
    
    private void startCleanupTask() {
        // Run cleanup every 30 seconds
        cleanupScheduler.scheduleAtFixedRate(this::cleanupDeadConnections, 30, 30, TimeUnit.SECONDS);
    }
    
    public void cleanupDeadConnections() {
        try (Connection conn = getConnection()) {
            // Mark connections as dead if no heartbeat for 2 minutes
            PreparedStatement updateStale = conn.prepareStatement(
                "UPDATE player_sessions SET connection_status = 'disconnected' " +
                "WHERE connection_status = 'connected' AND last_heartbeat < datetime('now', '-2 minutes')"
            );
            int staleCount = updateStale.executeUpdate();
            
            // Clean up old pending notifications (older than 10 minutes)
            PreparedStatement cleanNotifications = conn.prepareStatement(
                "DELETE FROM pending_notifications WHERE created_at < datetime('now', '-10 minutes')"
            );
            int cleanedNotifications = cleanNotifications.executeUpdate();
            
            // Reset lobby if waiting player is disconnected
            PreparedStatement resetLobby = conn.prepareStatement(
                "UPDATE lobby_state SET waiting_player_id = NULL, waiting_player_name = NULL, waiting_since = NULL " +
                "WHERE waiting_player_id IN (SELECT session_id FROM player_sessions WHERE connection_status = 'disconnected')"
            );
            resetLobby.executeUpdate();
            
            if (staleCount > 0 || cleanedNotifications > 0) {
                System.out.println("Cleanup: marked " + staleCount + " connections as stale, cleaned " + cleanedNotifications + " old notifications");
            }
        } catch (SQLException e) {
            System.err.println("Cleanup task failed: " + e.getMessage());
        }
    }
    
    public void updateHeartbeat(String sessionId) {
        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_sessions SET last_heartbeat = CURRENT_TIMESTAMP, connection_status = 'connected' WHERE session_id = ?"
            );
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to update heartbeat for " + sessionId + ": " + e.getMessage());
        }
    }
    
    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
        }
    }
}