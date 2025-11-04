package com.stanstate.ttt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import spark.Spark;
import java.util.UUID;

public class RestApiServer {
    private final GameService gameService;
    private final Gson gson;
    private final int port;
    
    public RestApiServer(int port) {
        this.port = port;
        
        // Get the WebSocket notifier, fallback to new instance if needed
        WebSocketNotifier notifier = Server.getNotifier();
        if (notifier == null) {
            System.out.println("Warning: Server.getNotifier() returned null, creating new WebSocketNotifier");
            notifier = new WebSocketNotifier();
        }
        
        this.gameService = new GameService(DatabaseManager.getInstance(), notifier);
        this.gson = new Gson();
        System.out.println("RestApiServer initialized with port " + port);
    }
    
    public void start() {
        Spark.port(port);
        setupRoutes();
        System.out.println("HTTP API server started on port " + port);
    }
    
    private void setupRoutes() {
        // Enable CORS for all routes
        Spark.before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        // Health check endpoint
        Spark.get("/health", (request, response) -> {
            response.type("application/json");
            JsonObject healthResponse = new JsonObject();
            healthResponse.addProperty("status", "ok");
            healthResponse.addProperty("message", "Server is running");
            return gson.toJson(healthResponse);
        });
        
        // Handle preflight requests
        Spark.options("/*", (request, response) -> {
            return "OK";
        });
        
        // Join game endpoint
        Spark.post("/api/join", (request, response) -> {
            response.type("application/json");
            
            try {
                System.out.println("=== JOIN REQUEST ===");
                System.out.println("Request body: " + request.body());
                
                JsonObject requestBody = gson.fromJson(request.body(), JsonObject.class);
                String sessionId = requestBody.has("sessionId") ? 
                    requestBody.get("sessionId").getAsString() : 
                    UUID.randomUUID().toString();
                String playerName = requestBody.has("name") ? 
                    requestBody.get("name").getAsString() : 
                    "Player-" + sessionId.substring(0, 8);
                
                System.out.println("Session ID: " + sessionId + ", Player: " + playerName);
                
                String matchId = gameService.joinGame(sessionId, playerName).get();
                System.out.println("Join result - Match ID: " + matchId);
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("sessionId", sessionId);
                responseJson.addProperty("matchId", matchId);
                
                return gson.toJson(responseJson);
            } catch (Exception e) {
                System.out.println("JOIN ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // NEW: Get available matches endpoint
        Spark.get("/api/matches", (request, response) -> {
            response.type("application/json");
            
            try {
                System.out.println("=== GET MATCHES REQUEST ===");
                
                // Get available matches from GameService
                JsonObject matchesResponse = gameService.getAvailableMatches().get();
                System.out.println("Available matches response: " + matchesResponse);
                
                return gson.toJson(matchesResponse);
            } catch (Exception e) {
                System.out.println("GET MATCHES ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // NEW: Create match endpoint
        Spark.post("/api/create-match", (request, response) -> {
            response.type("application/json");
            
            try {
                System.out.println("=== CREATE MATCH REQUEST ===");
                System.out.println("Request body: " + request.body());
                
                JsonObject requestBody = gson.fromJson(request.body(), JsonObject.class);
                String sessionId = requestBody.get("sessionId").getAsString();
                String playerName = requestBody.get("playerName").getAsString();
                String matchName = requestBody.has("matchName") ? requestBody.get("matchName").getAsString() : playerName + "'s Game";
                
                System.out.println("Create match - Session: " + sessionId + ", Player: " + playerName + ", Match: " + matchName);
                
                String matchId = gameService.createMatch(sessionId, playerName, matchName).get();
                System.out.println("Created match: " + matchId);
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", true);
                responseJson.addProperty("matchId", matchId);
                responseJson.addProperty("message", "Match created successfully");
                
                return gson.toJson(responseJson);
            } catch (Exception e) {
                System.out.println("CREATE MATCH ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // NEW: Join specific match endpoint
        Spark.post("/api/join-match", (request, response) -> {
            response.type("application/json");
            
            try {
                System.out.println("=== JOIN SPECIFIC MATCH REQUEST ===");
                System.out.println("Request body: " + request.body());
                
                JsonObject requestBody = gson.fromJson(request.body(), JsonObject.class);
                String sessionId = requestBody.get("sessionId").getAsString();
                String playerName = requestBody.get("playerName").getAsString();
                String matchId = requestBody.get("matchId").getAsString();
                
                System.out.println("Join specific match - Session: " + sessionId + ", Player: " + playerName + ", Match: " + matchId);
                
                boolean success = gameService.joinSpecificMatch(sessionId, playerName, matchId).get();
                System.out.println("Join specific match result: " + success);
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", success);
                if (success) {
                    responseJson.addProperty("matchId", matchId);
                    responseJson.addProperty("message", "Joined match successfully");
                } else {
                    responseJson.addProperty("error", "Failed to join match - may be full or not exist");
                }
                
                return gson.toJson(responseJson);
            } catch (Exception e) {
                System.out.println("JOIN SPECIFIC MATCH ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // Make move endpoint
        Spark.post("/api/move", (request, response) -> {
            response.type("application/json");
            
            try {
                System.out.println("=== MOVE REQUEST ===");
                System.out.println("Request body: " + request.body());
                
                JsonObject requestBody = gson.fromJson(request.body(), JsonObject.class);
                String sessionId = requestBody.get("sessionId").getAsString();
                String matchId = requestBody.get("matchId").getAsString();
                int cell = requestBody.get("cell").getAsInt();
                
                System.out.println("Move request - Session: " + sessionId + ", Match: " + matchId + ", Cell: " + cell);
                
                boolean success = gameService.makeMove(sessionId, matchId, cell).get();
                System.out.println("Move result: " + success);
                
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("success", success);
                
                return gson.toJson(responseJson);
            } catch (Exception e) {
                System.out.println("MOVE ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // NEW: Game state polling endpoint - replaces WebSocket notifications
        Spark.get("/api/game-state/:sessionId", (request, response) -> {
            response.type("application/json");
            
            try {
                String sessionId = request.params(":sessionId");
                System.out.println("=== GAME STATE POLL ===");
                System.out.println("Session: " + sessionId);
                
                // Get current game state for this session
                JsonObject gameState = gameService.getGameStateForSession(sessionId).get();
                System.out.println("Game state response: " + gameState);
                
                return gson.toJson(gameState);
            } catch (Exception e) {
                System.out.println("GAME STATE POLL ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });
        
        // NEW: Player statistics endpoint
        Spark.get("/api/stats/:playerName", (request, response) -> {
            response.type("application/json");
            
            try {
                String playerName = request.params(":playerName");
                System.out.println("=== PLAYER STATS REQUEST ===");
                System.out.println("Player: " + playerName);
                
                JsonObject stats = gameService.getPlayerStats(playerName).get();
                System.out.println("Stats response: " + stats);
                
                return gson.toJson(stats);
            } catch (Exception e) {
                System.out.println("PLAYER STATS ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });

        // NEW: Update player statistics endpoint
        Spark.post("/api/stats/update", (request, response) -> {
            response.type("application/json");
            
            try {
                String body = request.body();
                System.out.println("=== UPDATE PLAYER STATS REQUEST ===");
                System.out.println("Request body: " + body);
                
                JsonObject requestData = gson.fromJson(body, JsonObject.class);
                String playerName = requestData.get("playerName").getAsString();
                String result = requestData.get("result").getAsString(); // "win", "loss", or "draw"
                String gameType = requestData.has("gameType") ? requestData.get("gameType").getAsString() : "tictactoe";
                
                System.out.println("Player: " + playerName + ", Result: " + result + ", GameType: " + gameType);
                
                JsonObject stats = gameService.updatePlayerStats(playerName, result, gameType).get();
                System.out.println("Update response: " + stats);
                
                return gson.toJson(stats);
            } catch (Exception e) {
                System.out.println("UPDATE STATS ERROR: " + e.getMessage());
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", e.getMessage());
                response.status(500);
                return gson.toJson(errorResponse);
            }
        });

        // Health check
        Spark.get("/api/health", (req, res) -> {
            res.type("application/json");
            JsonObject health = new JsonObject();
            health.addProperty("status", "OK");
            health.addProperty("timestamp", System.currentTimeMillis());
            return gson.toJson(health);
        });
        
        System.out.println("REST API server started on http://localhost:" + port);
    }
    
    public void stop() {
        Spark.stop();
    }
}