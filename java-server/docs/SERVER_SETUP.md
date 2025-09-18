# Server Setup Guide - TicTacToe Multiplayer System

## Overview

This guide provides step-by-step instructions for setting up, building, and running the TicTacToe multiplayer server.

## Prerequisites

1. **Java Development Kit (JDK):** Ensure Java 11+ is installed.
2. **Gradle:** Verify Gradle is installed or use the provided Gradle wrapper.
3. **SQLite:** Ensure SQLite is installed for database operations.

## Setup Instructions

### 1. **Clone the Repository**

```bash
git clone https://github.com/kab102395/TicTacToe.git
cd TicTacToe/java-server
```

### 2. **Build the Project**

Use the Gradle wrapper to build the project:

```bash
./gradlew build
```

### 3. **Run the Server**

Execute the server using the following command:

```bash
java -cp "build/libs/*" com.stanstate.ttt.Main
```

### 4. **Verify the Server**

Check the server health endpoint:

```bash
curl http://127.0.0.1:8081/health
```

Expected response:

```json
{"status":"ok","message":"Server is running"}
```

## Notes

- Database files are located in the `database/` folder.
- Logs and build artifacts are stored in the `build/` folder.
- Modify `settings.gradle` for custom configurations.

## Troubleshooting

1. **Build Failures:**
   - Ensure Gradle and JDK versions are compatible.
   - Check for missing dependencies in `build.gradle`.

2. **Server Startup Errors:**
   - Verify the database files are accessible.
   - Check for port conflicts on 8080 and 8081.

3. **API Failures:**
   - Use the provided test scripts in the `tests/` folder to debug issues.