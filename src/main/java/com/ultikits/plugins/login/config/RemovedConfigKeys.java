package com.ultikits.plugins.login.config;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reports configuration keys this module no longer reads but which are still sitting in the
 * operator's own {@code config/login.yml}.
 * <p>
 * Deleting a key from {@link LoginConfig} stops the framework writing it into a fresh file, but it
 * does nothing to the files already on disk: the framework only ever writes a declared default for
 * a key that is <em>missing</em>, so an existing install keeps the key, keeps whatever value the
 * operator gave it, and gets no indication that the value means nothing. This class is that
 * indication -- one warning per leftover key, naming the module, the file and the key, and saying
 * where the setting went.
 *
 * @author wisdomme
 * @version 1.0.0
 */
public final class RemovedConfigKeys {

    /**
     * Every key removed from {@code config/login.yml}, mapped to what an operator should be told
     * about it. Insertion order is the order the warnings are emitted in.
     */
    private static final Map<String, String> REMOVED;

    static {
        Map<String, String> removed = new LinkedHashMap<String, String>();
        removed.put("messages.wrong-password",
                "Its value was never shown to players. The reply to a wrong password now comes "
                        + "from the 'wrong_password' entry of this module's language file "
                        + "(lang/<language>.json, in the same module folder as config/login.yml), "
                        + "so it follows the server's language setting; edit it there "
                        + "(UltiKits/UltiLogin#23).");
        REMOVED = Collections.unmodifiableMap(removed);
    }

    private RemovedConfigKeys() {
        // Utility class
    }

    /**
     * The keys this class knows about, in the order it reports them.
     *
     * @return an unmodifiable map of removed key path to the guidance printed for it
     */
    public static Map<String, String> removedKeys() {
        return REMOVED;
    }

    /**
     * Emit one warning per removed key that is still present in the operator's configuration file.
     * <p>
     * Silent when the file is absent or unreadable -- there is then nothing to report and nothing
     * to be sure of. A parse failure is deliberately not reported here: the framework's own config
     * loading already fails loudly on an unparseable file, and a second message from this check
     * would only add noise to it.
     *
     * @param configFile the operator's {@code config/login.yml}; may be {@code null}
     * @param warn       where to send each warning, normally the module logger's warn method
     */
    public static void warnAboutLeftovers(File configFile, Consumer<String> warn) {
        if (configFile == null || !configFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            return;
        }
        for (Map.Entry<String, String> entry : REMOVED.entrySet()) {
            if (yaml.contains(entry.getKey())) {
                // No "[UltiLogin]" prefix: the module logger adds that itself, and the module is
                // still named in the sentence for any consumer that does not.
                warn.accept(configFile.getPath() + " still contains '"
                        + entry.getKey() + "', which this version of UltiLogin no longer reads. "
                        + entry.getValue()
                        + " Delete the key from the file to silence this warning.");
            }
        }
    }
}
