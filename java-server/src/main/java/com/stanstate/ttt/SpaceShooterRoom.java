package com.stanstate.ttt;

import com.google.gson.JsonObject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Placeholder implementation of Space Shooter game room.
 * Players destroy enemies (0-200 enemies destroyed).
 * First to destroy all enemies wins.
 */
public class SpaceShooterRoom extends Room {
    private ClientSession p1, p2;
    private int p1Destroyed = 0, p2Destroyed = 0;
    private static final int TARGET_DESTROYED = 200;
    private ScheduledFuture<?> timer;

    public SpaceShooterRoom(String id, ScheduledExecutorService s) {
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
                p1Destroyed++;
            } else {
                p2Destroyed++;
            }

            String res = result();
            System.out.println("SPACE SHOOTER: Player " + (isP1 ? "1" : "2") + " destroyed an enemy. P1: " + p1Destroyed + ", P2: " + p2Destroyed);

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
        if (p1Destroyed >= TARGET_DESTROYED) return "P1";
        if (p2Destroyed >= TARGET_DESTROYED) return "P2";
        return "ongoing";
    }

    private void scheduleTimer() {
        cancelTimer();
        timer = sched.schedule(() -> {
            String winner = (p1Destroyed > p2Destroyed) ? "P1" : (p2Destroyed > p1Destroyed) ? "P2" : "draw";
            broadcastOver("timeout:" + winner);
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
        st.addProperty("p1_destroyed", p1Destroyed);
        st.addProperty("p2_destroyed", p2Destroyed);
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
