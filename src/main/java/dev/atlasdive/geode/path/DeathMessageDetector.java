package dev.atlasdive.geode.path;

import java.util.regex.Pattern;

/** Matches the variable player-name portion of the Pit death recap line. */
public final class DeathMessageDetector {
    private static final Pattern RECAP=Pattern.compile("(?i)DEATH!\\s+by\\s+.+\\s+VIEW\\s+RECAP");
    private static final Pattern FORMATTING=Pattern.compile("(?i)\\u00a7[0-9A-FK-OR]");
    private DeathMessageDetector() {}

    public static boolean matches(String message){
        if(message==null)return false;
        String plain=FORMATTING.matcher(message).replaceAll("").trim();
        return RECAP.matcher(plain).find();
    }
}
