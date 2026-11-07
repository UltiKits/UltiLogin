package com.ultikits.plugins.login.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conditional framework requirement for server API compatibility, independent of Bukkit. */
public final class RuntimeCompatibility {
    private static final Pattern VERSION = Pattern.compile(
            "^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-[A-Za-z0-9][A-Za-z0-9.+-]*)?$");

    private RuntimeCompatibility() {
    }

    public enum Result { ALLOW, REFUSE, UNKNOWN }

    /** SNAPSHOT and Bukkit build suffixes do not change the numeric version. */
    public static Result check(String minecraft, String framework) {
        int[] server = parse(minecraft);
        int[] core = parse(framework);
        if (server == null || core == null) {
            return Result.UNKNOWN;
        }
        return below(server, 1, 21, 0) && below(core, 6, 4, 0) ? Result.REFUSE : Result.ALLOW;
    }

    private static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        Matcher match = VERSION.matcher(version.trim());
        if (!match.matches()) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                    match.group(3) == null ? 0 : Integer.parseInt(match.group(3))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean below(int[] version, int major, int minor, int patch) {
        int[] minimum = {major, minor, patch};
        for (int i = 0; i < minimum.length; i++) {
            if (version[i] != minimum[i]) {
                return version[i] < minimum[i];
            }
        }
        return false;
    }
}
