package com.stanstate.ttt;

import com.google.gson.JsonObject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Placeholder implementation of Duck Hunt game room.
 * Players shoot targets (0-50 targets hit).
 * First to hit all targets wins.
 */
public class DuckHuntRoom extends Room {
    private ClientSession p1, p2;
    private int p1Hits = 0, p2Hits = 0;
    private static final int TARGET_HITS = 50;
    private ScheduledFuture<?> timer;

    public DuckHuntRoom(String id, ScheduledExecutorService s) {
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
            if (isP1) {
                p1Hits++;
            } else {
                p2Hits++;
            }

            String res = result();
            System.out.println("DUCK HUNT SHOT: Player " + (isP1 ? "1" : "2") + " hit a target. P1: " + p1Hits + ", P2: " + p2Hits);

            if (!"ongoing".equals(res)) {
                cancelTimer();
                broadcastOver(res);
            } else {
                scheduleTimer();
                broadcastState(res);
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

    private String result() {
        if (p1Hits >= TARGET_HITS) return "P1";
        if (p2Hits >= TARGET_HITS) return "P2";
        return "ongoing";
    }

    private void scheduleTimer() {
        cancelTimer();
        timer = sched.schedule(() -> {
            String winner = (p1Hits > p2Hits) ? "P1" : (p2Hits > p1Hits) ? "P2" : "draw";
            broadcastOver("timeout:" + winner);
        }, 120, TimeUnit.SECONDS);
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
        st.addProperty("p1_hits", p1Hits);
        st.addProperty("p2_hits", p2Hits);
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
