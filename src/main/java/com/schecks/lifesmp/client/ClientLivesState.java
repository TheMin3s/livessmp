package com.schecks.lifesmp.client;

/**
 * Client-only holder for the local player's lives, as last told by the server.
 * Written from the network handler, read from the HUD render path — fields are
 * volatile so the value is always visible across those threads.
 */
public final class ClientLivesState {
    private static volatile int lives = -1;   // -1 = no data received yet
    private static volatile int maxLives = 0;

    private ClientLivesState() {}

    public static void update(int newLives, int newMax) {
        lives = newLives;
        maxLives = newMax;
    }

    /** Forgets the cached lives — so the HUD vanishes on disconnect / vanilla servers. */
    public static void reset() {
        lives = -1;
        maxLives = 0;
    }

    public static boolean hasData() {
        return lives >= 0;
    }

    public static int lives() {
        return lives;
    }

    public static int maxLives() {
        return maxLives;
    }
}
