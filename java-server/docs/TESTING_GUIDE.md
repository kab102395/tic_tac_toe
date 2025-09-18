# Testing Guide - TicTacToe Multiplayer System

## Overview
This guide provides instructions for running tests, interpreting results, and troubleshooting issues in the TicTacToe multiplayer system.

## Test Categories

### 1. **Turn Logic Tests**
- **Script:** `test_turn_logic.ps1`
- **Purpose:** Validates the correctness of turn-based gameplay.
- **Execution:**
  ```powershell
  .\tests\test_turn_logic.ps1
  ```
- **Expected Output:**
  - Proper turn sequence.
  - No skipped or repeated turns.

### 2. **WebSocket Debug Tests**
- **Script:** `test_websocket_debug.ps1`
- **Purpose:** Debugs WebSocket connections and message flow.
- **Execution:**
  ```powershell
  .\tests\test_websocket_debug.ps1
  ```
- **Expected Output:**
  - Successful connection establishment.
  - Correct message delivery.

### 3. **WebSocket Flow Tests**
- **Script:** `test_websocket_flow.ps1`
- **Purpose:** Validates the flow of WebSocket messages under various scenarios.
- **Execution:**
  ```powershell
  .\tests\test_websocket_flow.ps1
  ```
- **Expected Output:**
  - Messages delivered in correct order.
  - No dropped or delayed messages.

## Troubleshooting

### Common Issues
1. **Database Connection Errors:**
   - Ensure the database files are in the `database/` folder.
   - Verify the server is running.

2. **WebSocket Failures:**
   - Check server logs for errors.
   - Ensure the correct port (8080) is open.

3. **Script Execution Errors:**
   - Verify PowerShell execution policy allows running scripts.
   - Use `Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy Unrestricted` if needed.

## Notes
- All test scripts are located in the `tests/` folder.
- Ensure the server is running before executing tests.
- Modify scripts as needed for custom scenarios.