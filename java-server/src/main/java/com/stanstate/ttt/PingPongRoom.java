package com.stanstate.ttt;

import com.google.gson.JsonObject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Placeholder implementation of Ping Pong game room.
 * Players hit a rally (0-1000 rally count).
 * Longest rally wins.
 */
public class PingPongRoom extends Room {
    private ClientSession p1, p2;
    private int rallyCount = 0;
    private String currentPlayer = "P1"; // Who's hitting the ball
    private int p1BestRally = 0, p2BestRally = 0;
    private ScheduledFuture<?> timer;

    public PingPongRoom(String id, ScheduledExecutorService s) {
        super(id, s);
    }

    public void addPlayer(ClientSession s, int seat) {
        s.matchId = id;
        s.seat = seat;
        if (seat == 1) p1 = s;
        else p2 = s;
    }

    @Override
    public void start() {
        var m1 = new JsonObject();
        m1.addProperty("t", "match");
        m1.addProperty("match", id);
        m1.addProperty("seat", 1);
        p1.send(m1);

        var m2 = new JsonObject();
        m2.addProperty("t", "match");
        m2.addProperty("match", id);
        m2.addProperty("seat", 2);
        p2.send(m2);

        broadcastState("ongoing");
        scheduleTimer();
    }

    @Override
    public void onMove(ClientSession s, int cell) {
        sched.execute(() -> {
            boolean isP1 = (s == p1);
            String hitting = isP1 ? "P1" : "P2";

            if (hitting.equals(currentPlayer)) {
                rallyCount++;
                currentPlayer = isP1 ? "P2" : "P1";

                if (rallyCount >= 1000) {
                    if (isP1) p1BestRally = rallyCount;
                    else p2BestRally = rallyCount;

                    String winner = isP1 ? "P1" : "P2";
                    cancelTimer();
                    broadcastOver(winner);
                } else {
                    scheduleTimer();
                    broadcastState("ongoing");
                }
            }
        });
    }

    @Override
    public void onLeave(ClientSession s) {
        sched.execute(() -> {
            cancelTimer();
            String winner = (s == p1) ? "P2" : "P1";
            broadcastOver("forfeit:" + winner);
        });
    }

    private void scheduleTimer() {
        cancelTimer();
        timer = sched.schedule(() -> {
            if (p1BestRally > p2BestRally) {
                broadcastOver("timeout:P1");
            } else if (p2BestRally > p1BestRally) {
                broadcastOver("timeout:P2");
            } else {
                broadcastOver("timeout:draw");
            }
        }, 180, TimeUnit.SECONDS);
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel(false);
        }
    }

    private void broadcastState(String res) {
        var st = new JsonObject();
        st.addProperty("t", "state");
        st.addProperty("match", id);
        st.addProperty("rally_count", rallyCount);
        st.addProperty("current_player", currentPlayer);
        st.addProperty("p1_best", p1BestRally);
        st.addProperty("p2_best", p2BestRally);
        st.addProperty("result", res);
        p1.send(st);
        p2.send(st);
    }

    private void broadcastOver(String res) {
        var over = new JsonObject();
        over.addProperty("t", "over");
        over.addProperty("match", id);
        over.addProperty("result", res);
        p1.send(over);
        p2.send(over);
    }
}
