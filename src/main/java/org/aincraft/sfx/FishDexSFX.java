package org.aincraft.sfx;

import org.bukkit.entity.Player;

/** Central place for FishDex UI sounds. */
public final class FishDexSFX {
    private FishDexSFX() {}

    // Resource-pack sound keys (define these in assets/longhardfish/sounds.json)
    public static final String OPEN   = "longhardfish:dex.open";
    public static final String CLOSE  = "longhardfish:dex.close";
    public static final String NEXT    = "longhardfish:dex.next";
    public static final String PREVIOUS    = "longhardfish:dex.previous";
    public static final String SELECT = "longhardfish:dex.select";

    public static void playOpen(Player p)   { play(p, OPEN); }
    public static void playClose(Player p)  { play(p, CLOSE); }
    public static void playNext(Player p)    { play(p, NEXT); }
    public static void playPrevious(Player p)    { play(p, PREVIOUS); }
    public static void playSelect(Player p) { play(p, SELECT); }

    private static void play(Player p, String key) {
        if (p == null || key == null || key.isBlank()) return;
        p.playSound(p.getLocation(), key, 1.0f, 1.0f);
    }
}

