package com.ultikits.plugins.login.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.annotations.config.Pattern;
import com.ultikits.ultitools.annotations.config.Size;
import com.ultikits.ultitools.entities.Language;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Writes a configuration's built-in text into the operator's file in the server's language.
 * <p>
 * A text setting holds its built-in text until the operator edits it. "Built-in" is an exact,
 * whole-value match against a tracked set: every default an earlier version shipped, plus the text
 * this module's jar ships for the setting in every language ({@link #LANGUAGES}). A value in that
 * set is replaced with the text in the server's current language; any other value, including a
 * built-in text changed by one character, is the operator's and is never touched. A blank value is
 * never replaced.
 * <p>
 * The text written comes from the same place as the tracked set: this jar's own catalogue for the
 * language the framework loads ({@link #jarLanguage}), never the module's {@code i18n}, which reads the
 * operator's extracted language file first. Otherwise an edit of that file would be written into the
 * config file, would not be in the tracked set, and would stop following {@code language} -- a value
 * the module wrote itself would become unrecognisable (orchestrator ruling O3, 2026-09-25). So these
 * settings are customised in the config file, not in the language file.
 * <p>
 * The jar's texts are read from this module's own jar (its {@link CodeSource}), not through the
 * class loader and not from the language files on disk: every internal module shares one class
 * loader, so a resource lookup could return another module's {@code lang/en.yml}, and the disk copy
 * is the operator's to edit. Files are flattened the way the framework's {@code Language} reads them:
 * JSON as a flat map with every primitive value read as its text (Gson's {@code Map<String, String>}),
 * YAML with nested keys joined by {@code '.'} and string values only.
 * <p>
 * A current text that would break the field's own {@code @NotEmpty}, {@code @Size} or {@code @Pattern}
 * constraint (for example an operator's over-long title in the language file on disk) is never
 * written: the framework validates the file on the next load and would refuse the module. The value is
 * kept instead, and the module keeps showing it, so file and behaviour still agree.
 * <p>
 * The caller rewrites its fields with the returned values and saves the file once when anything
 * changed. It must run after the module's language is loaded ({@code registerSelf()} and
 * {@code onReload()}), never from a configuration change listener, which the framework fires before
 * it reloads the language.
 */
public final class ConfigTextDefaults {

    /** The languages every module jar ships a catalogue for. */
    static final List<String> LANGUAGES = Collections.unmodifiableList(Arrays.asList("en", "zh"));

    /** Catalogue file extensions in the order the framework tries them; the first present wins. */
    private static final List<String> EXTENSIONS = Collections.unmodifiableList(Arrays.asList(".json", ".yml", ".yaml"));

    private static final Gson GSON = new Gson();

    private ConfigTextDefaults() {
    }

    /**
     * The value to store for a single-string setting.
     *
     * @param value   the value now in the file (after the framework loaded it)
     * @param current the text in the server's current language, or {@code null} when unknown
     * @param tracked every text that counts as built-in ({@link #tracked})
     * @return {@code current} when {@code value} is built-in text other than {@code current};
     *         otherwise {@code value} itself
     */
    public static String materialize(String value, String current, Collection<String> tracked) {
        if (isBlank(value) || isBlank(current) || value.equals(current) || !tracked.contains(value)) {
            return value;
        }
        return current;
    }

    /**
     * The value to store for a list setting, compared as a whole list.
     *
     * @param value   the list now in the file
     * @param current the list in the server's current language, or {@code null} when unknown
     * @param tracked every list that counts as built-in ({@link #trackedLines})
     * @return a copy of {@code current} when {@code value} is a built-in list other than
     *         {@code current}; otherwise {@code value} itself
     */
    public static List<String> materializeLines(List<String> value, List<String> current,
                                                Collection<List<String>> tracked) {
        if (value == null || value.isEmpty() || current == null || current.isEmpty()
                || value.equals(current) || !tracked.contains(value)) {
            return value;
        }
        return new ArrayList<>(current);
    }

    /**
     * {@link #materialize(String, String, Collection)} for {@code entity}'s field {@code field}: a
     * replacement that would break the field's constraints ({@link #satisfiesConstraints}) is not made.
     */
    public static String materialize(Class<?> entity, String field, String value, String current,
                                     Collection<String> tracked) {
        String result = materialize(value, current, tracked);
        return result == value || satisfiesConstraints(entity, field, result) ? result : value;
    }

    /**
     * {@link #materializeLines(List, List, Collection)} for {@code entity}'s field {@code field}: a
     * replacement that would break the field's constraints ({@link #satisfiesConstraints}) is not made.
     */
    public static List<String> materializeLines(Class<?> entity, String field, List<String> value, List<String> current,
                                                Collection<List<String>> tracked) {
        List<String> result = materializeLines(value, current, tracked);
        return result == value || satisfiesConstraints(entity, field, result) ? result : value;
    }

    /**
     * Whether {@code candidate} satisfies the {@code @NotEmpty}, {@code @Size} and {@code @Pattern}
     * constraints on {@code entity}'s declared field {@code field}, checked as the framework's
     * {@code AbstractConfigEntity#validateFields} checks them: not blank; string length or collection
     * size within {@code [min, max]}; a string matching the whole regex.
     *
     * @throws IllegalArgumentException when {@code entity} declares no field of that name
     */
    public static boolean satisfiesConstraints(Class<?> entity, String field, Object candidate) {
        Field f;
        try {
            f = entity.getDeclaredField(field);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(entity.getName() + " has no field " + field, e);
        }
        if (f.getAnnotation(NotEmpty.class) != null && (candidate == null || candidate.toString().trim().isEmpty())) {
            return false;
        }
        Size size = f.getAnnotation(Size.class);
        if (size != null && candidate != null) {
            int length = candidate instanceof Collection ? ((Collection<?>) candidate).size()
                    : candidate instanceof String ? ((String) candidate).length() : -1;
            if (length >= 0 && (length < size.min() || length > size.max())) {
                return false;
            }
        }
        Pattern pattern = f.getAnnotation(Pattern.class);
        return pattern == null || !(candidate instanceof String) || ((String) candidate).matches(pattern.regex());
    }

    /**
     * The text in the server's current language for {@code key}, with {@code prefix} in front, or
     * {@code null} when the language has no text for it (the framework answers a missing key with
     * the key itself; that is never written into a file).
     */
    public static String currentText(Function<String, String> text, String prefix, String key) {
        String value = text.apply(key);
        if (value == null || value.equals(key) || isBlank(value)) {
            return null;
        }
        return prefix + value;
    }

    /** {@link #currentText} for a list setting whose catalogue entry holds its lines separated by "\n". */
    public static List<String> currentLines(Function<String, String> text, String key) {
        String value = currentText(text, "", key);
        return value == null ? null : lines(value);
    }

    /** Splits a catalogue value into lines on "\n", keeping empty lines, including trailing ones. */
    public static List<String> lines(String text) {
        return new ArrayList<>(Arrays.asList(text.split("\n", -1)));
    }

    /**
     * The tracked set of a single-string setting: {@code shipped} plus {@code prefix} + the jar's
     * text for {@code key} in every language the jar ships.
     */
    public static Set<String> tracked(Map<String, Map<String, String>> jar, String prefix, String key,
                                      String... shipped) {
        Set<String> set = new LinkedHashSet<>(Arrays.asList(shipped));
        for (Map<String, String> catalogue : jar.values()) {
            String text = catalogue.get(key);
            if (text != null) {
                set.add(prefix + text);
            }
        }
        return set;
    }

    /** The tracked set of a list setting: every shipped list plus the jar's lines for {@code key}. */
    @SafeVarargs
    public static Set<List<String>> trackedLines(Map<String, Map<String, String>> jar, String key,
                                                 List<String>... shipped) {
        Set<List<String>> set = new LinkedHashSet<>();
        for (List<String> list : shipped) {
            set.add(new ArrayList<>(list));
        }
        for (Map<String, String> catalogue : jar.values()) {
            String text = catalogue.get(key);
            if (text != null) {
                set.add(lines(text));
            }
        }
        return set;
    }

    /**
     * This jar's catalogue, as a framework {@link Language}, for the language the framework loads when
     * the server's {@code language} is {@code code}: {@code code} itself when the jar ships it, else
     * {@code en}, else the first language shipped (as {@code UltiToolsPlugin#resolveLanguageCode} falls
     * back). Pass its {@code getLocalizedText} to a configuration's {@code materializeText}: a missing
     * key answers itself, which {@link #currentText} treats as "no text".
     *
     * @param anchor a class in the module's jar
     * @param code   the server's {@code language}, as {@code UltiToolsPlugin#getLanguageCode()} returns it
     * @return the jar's catalogue for that language
     */
    public static Language jarLanguage(Class<?> anchor, String code) {
        return jarLanguage(jarCatalogues(anchor), code);
    }

    /** {@link #jarLanguage(Class, String)} over catalogues already read with {@link #jarCatalogues}. */
    public static Language jarLanguage(Map<String, Map<String, String>> jar, String code) {
        Map<String, String> chosen = null;
        if (code != null && jar.containsKey(code) && !jar.get(code).isEmpty()) {
            chosen = jar.get(code);
        } else if (jar.containsKey("en") && !jar.get("en").isEmpty()) {
            chosen = jar.get("en");
        } else {
            for (Map<String, String> catalogue : jar.values()) {
                if (!catalogue.isEmpty()) {
                    chosen = catalogue;
                    break;
                }
            }
        }
        return new Language(chosen == null ? Collections.<String, String>emptyMap() : new LinkedHashMap<>(chosen));
    }

    /**
     * The catalogues shipped in the jar (or class directory) that holds {@code anchor}, one per
     * language in {@link #LANGUAGES}, keyed by language code; a language whose file is absent or
     * unreadable maps to an empty catalogue.
     */
    public static Map<String, Map<String, String>> jarCatalogues(Class<?> anchor) {
        CodeSource source = anchor.getProtectionDomain().getCodeSource();
        return jarCatalogues(source == null ? null : source.getLocation());
    }

    /** {@link #jarCatalogues(Class)} for an explicit jar or class-directory location. */
    static Map<String, Map<String, String>> jarCatalogues(URL location) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        File file = location == null ? null : toFile(location);
        for (String code : LANGUAGES) {
            result.put(code, file == null ? Collections.<String, String>emptyMap() : catalogue(file, code));
        }
        return result;
    }

    private static Map<String, String> catalogue(File location, String code) {
        for (String extension : EXTENSIONS) {
            String entry = "lang/" + code + extension;
            try {
                String text = read(location, entry);
                if (text != null) {
                    return parse(text, extension);
                }
            } catch (IOException | InvalidConfigurationException | JsonParseException | IllegalStateException e) {
                return Collections.emptyMap();
            }
        }
        return Collections.emptyMap();
    }

    private static String read(File location, String entry) throws IOException {
        if (location.isDirectory()) {
            File file = new File(location, entry.replace('/', File.separatorChar));
            return file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : null;
        }
        if (!location.isFile()) {
            return null;
        }
        try (JarFile jar = new JarFile(location)) {
            JarEntry found = jar.getJarEntry(entry);
            if (found == null) {
                return null;
            }
            try (InputStream in = jar.getInputStream(found)) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int length;
                while ((length = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, length);
                }
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }

    private static Map<String, String> parse(String text, String extension) throws InvalidConfigurationException {
        Map<String, String> result = new LinkedHashMap<>();
        if (".json".equals(extension)) {
            JsonObject object = GSON.fromJson(text, JsonObject.class);
            if (object != null) {
                for (Map.Entry<String, JsonElement> e : object.entrySet()) {
                    JsonElement v = e.getValue();
                    if (v != null && v.isJsonPrimitive()) {
                        result.put(e.getKey(), v.getAsString());
                    }
                }
            }
            return result;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                result.put(key, yaml.getString(key));
            }
        }
        return result;
    }

    private static File toFile(URL location) {
        try {
            return new File(location.toURI());
        } catch (URISyntaxException | IllegalArgumentException e) {
            return new File(location.getPath());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
