package com.ultikits.plugins.login.config;

import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.annotations.config.Pattern;
import com.ultikits.ultitools.annotations.config.Size;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The config text materializer: which values count as built-in text, how a list is compared, and
 * where the jar's own texts are read from. Module-independent; every module carries this file
 * unchanged except the package line.
 */
@DisplayName("ConfigTextDefaults")
class ConfigTextDefaultsTest {

    private static final Set<String> TRACKED = new java.util.LinkedHashSet<>(Arrays.asList("old shipped", "en text", "zh text"));

    @Nested
    @DisplayName("a single value")
    class SingleValue {

        @Test
        @DisplayName("a tracked value other than the current text is replaced")
        void trackedValueIsReplaced() {
            assertThat(ConfigTextDefaults.materialize("old shipped", "en text", TRACKED)).isEqualTo("en text");
            assertThat(ConfigTextDefaults.materialize("zh text", "en text", TRACKED)).isEqualTo("en text");
            assertThat(ConfigTextDefaults.materialize("en text", "zh text", TRACKED)).isEqualTo("zh text");
        }

        @Test
        @DisplayName("a value equal to the current text is returned as the same instance (no change)")
        void currentValueIsLeft() {
            String value = new String("en text".toCharArray());
            assertThat(ConfigTextDefaults.materialize(value, "en text", TRACKED)).isSameAs(value);
        }

        @Test
        @DisplayName("a value outside the tracked set is kept, even one character away from a tracked text")
        void customisedValueIsKept() {
            assertThat(ConfigTextDefaults.materialize("old shippe", "en text", TRACKED)).isEqualTo("old shippe");
            assertThat(ConfigTextDefaults.materialize("old shipped ", "en text", TRACKED)).isEqualTo("old shipped ");
            assertThat(ConfigTextDefaults.materialize("Operator text", "en text", TRACKED)).isEqualTo("Operator text");
        }

        @Test
        @DisplayName("a null or blank value is never replaced, and nothing is written when the current text is unknown")
        void blankIsNeverReplaced() {
            Set<String> withBlank = new java.util.LinkedHashSet<>(TRACKED);
            withBlank.add("");
            withBlank.add("  ");
            assertThat(ConfigTextDefaults.materialize(null, "en text", withBlank)).isNull();
            assertThat(ConfigTextDefaults.materialize("", "en text", withBlank)).isEmpty();
            assertThat(ConfigTextDefaults.materialize("  ", "en text", withBlank)).isEqualTo("  ");
            assertThat(ConfigTextDefaults.materialize("old shipped", null, TRACKED)).isEqualTo("old shipped");
            assertThat(ConfigTextDefaults.materialize("old shipped", " ", TRACKED)).isEqualTo("old shipped");
        }

        @Test
        @DisplayName("the current text is the catalogue text with the prefix, or null when the key is missing")
        void currentText() {
            Map<String, String> catalogue = new HashMap<>();
            catalogue.put("k", "Hello");
            catalogue.put("blank", " ");
            Function<String, String> text = key -> catalogue.containsKey(key) ? catalogue.get(key) : key;

            assertThat(ConfigTextDefaults.currentText(text, "&e", "k")).isEqualTo("&eHello");
            assertThat(ConfigTextDefaults.currentText(text, "", "k")).isEqualTo("Hello");
            assertThat(ConfigTextDefaults.currentText(text, "&e", "missing")).as("framework answers a missing key with the key").isNull();
            assertThat(ConfigTextDefaults.currentText(text, "&e", "blank")).isNull();
            assertThat(ConfigTextDefaults.currentText(key -> null, "", "k")).isNull();
        }
    }

    @Nested
    @DisplayName("a list value")
    class ListValue {

        private final List<String> shipped = Arrays.asList("&7Hello", "", "&eOld");
        private final List<String> en = Arrays.asList("&7Hello", "", "&eNew");
        private final List<String> zh = Arrays.asList("&7Ni hao", "", "&eXin");

        private Set<List<String>> tracked() {
            Set<List<String>> set = new java.util.LinkedHashSet<>();
            set.add(shipped);
            set.add(en);
            set.add(zh);
            return set;
        }

        @Test
        @DisplayName("a tracked list is replaced as a whole, by a copy")
        void trackedListIsReplaced() {
            List<String> result = ConfigTextDefaults.materializeLines(new ArrayList<>(shipped), zh, tracked());
            assertThat(result).containsExactlyElementsOf(zh).isNotSameAs(zh);
            assertThat(ConfigTextDefaults.materializeLines(new ArrayList<>(en), zh, tracked())).containsExactlyElementsOf(zh);
        }

        @Test
        @DisplayName("a list with one edited, added or removed line is kept as it is")
        void editedListIsKept() {
            List<String> edited = new ArrayList<>(shipped);
            edited.set(2, "&eOld!");
            assertThat(ConfigTextDefaults.materializeLines(edited, zh, tracked())).isSameAs(edited);
            List<String> longer = new ArrayList<>(shipped);
            longer.add("");
            assertThat(ConfigTextDefaults.materializeLines(longer, zh, tracked())).isSameAs(longer);
            List<String> shorter = new ArrayList<>(shipped.subList(0, 2));
            assertThat(ConfigTextDefaults.materializeLines(shorter, zh, tracked())).isSameAs(shorter);
        }

        @Test
        @DisplayName("the current list, an empty list and a null list are left, and an unknown current list writes nothing")
        void currentEmptyAndNullAreLeft() {
            List<String> current = new ArrayList<>(zh);
            assertThat(ConfigTextDefaults.materializeLines(current, zh, tracked())).isSameAs(current);
            List<String> empty = new ArrayList<>();
            Set<List<String>> withEmpty = tracked();
            withEmpty.add(Collections.<String>emptyList());
            assertThat(ConfigTextDefaults.materializeLines(empty, zh, withEmpty)).isSameAs(empty);
            assertThat(ConfigTextDefaults.materializeLines(null, zh, tracked())).isNull();
            List<String> old = new ArrayList<>(shipped);
            assertThat(ConfigTextDefaults.materializeLines(old, null, tracked())).isSameAs(old);
        }

        @Test
        @DisplayName("a catalogue value splits on \\n and keeps empty lines, including a trailing one")
        void linesKeepEmptyLines() {
            assertThat(ConfigTextDefaults.lines("a\n\nb\n")).containsExactly("a", "", "b", "");
            assertThat(ConfigTextDefaults.lines("single")).containsExactly("single");
            Function<String, String> text = key -> "k".equals(key) ? "x\n\ny" : key;
            assertThat(ConfigTextDefaults.currentLines(text, "k")).containsExactly("x", "", "y");
            assertThat(ConfigTextDefaults.currentLines(text, "missing")).isNull();
        }
    }

    /** A configuration entity shape with the framework's validation annotations. */
    @SuppressWarnings("unused")
    static final class Constrained {
        @NotEmpty
        @Size(min = 1, max = 5)
        private String title;
        @Size(max = 2)
        private List<String> lines;
        @Pattern(regex = "[a-z]+")
        private String word;
        private String free;
    }

    @Nested
    @DisplayName("the field's validation constraints")
    class Constraints {

        @Test
        @DisplayName("@NotEmpty, @Size (string length, list size) and @Pattern are checked the way the framework's validateFields checks them")
        void satisfiesTheFrameworkConstraints() {
            assertThat(ConfigTextDefaults.satisfiesConstraints(Constrained.class, "title", "abcde")).isTrue();
            assertThat(ConfigTextDefaults.satisfiesConstraints(Constrained.class, "title", "abcdef")).isFalse();
            assertThat(ConfigTextDefaults.satisfiesConstraints(Constrained.class, "title", " ")).isFalse();
            assertThat(ConfigTextDefaults.satisfiesConstraints(Constrained.class, "lines", Arrays.asList("a", "b"))).isTrue();
            assertThat(ConfigTextDefaults.satisfiesConstraints(Constrained.class, "lines", Arrays.asList("a", "b", "c"))).isFalse();
            assertThat(ConfigTextDefaults.satisfiesConstraints(Constrained.class, "word", "abc")).isTrue();
            assertThat(ConfigTextDefaults.satisfiesConstraints(Constrained.class, "word", "Abc")).isFalse();
            assertThat(ConfigTextDefaults.satisfiesConstraints(Constrained.class, "free", "anything at all, any length")).isTrue();
            assertThatThrownBy(() -> ConfigTextDefaults.satisfiesConstraints(Constrained.class, "missing", "x"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a current text that would break the field's constraints is not written; the value is kept")
        void aTextBreakingTheConstraintsIsNotWritten() {
            Set<String> tracked = new java.util.LinkedHashSet<>(Arrays.asList("old", "ok"));
            assertThat(ConfigTextDefaults.materialize(Constrained.class, "title", "old", "ok", tracked)).isEqualTo("ok");
            assertThat(ConfigTextDefaults.materialize(Constrained.class, "title", "old", "too long", tracked)).isEqualTo("old");

            Set<List<String>> lists = new java.util.LinkedHashSet<>();
            lists.add(Arrays.asList("x"));
            List<String> value = new ArrayList<>(Arrays.asList("x"));
            assertThat(ConfigTextDefaults.materializeLines(Constrained.class, "lines", value, Arrays.asList("y", "z"), lists))
                    .containsExactly("y", "z");
            assertThat(ConfigTextDefaults.materializeLines(Constrained.class, "lines", value, Arrays.asList("y", "z", "w"), lists))
                    .isSameAs(value);
        }
    }

    @Nested
    @DisplayName("the jar's own texts")
    class JarTexts {

        @TempDir
        Path temp;

        @Test
        @DisplayName("YAML is flattened with '.', only string values are kept; JSON is read flat, every primitive as its text, like the framework's Gson map")
        void yamlAndJsonAreFlattenedLikeTheFramework() throws Exception {
            File jar = jar("m.jar",
                    "lang/en.yml", "top: \"T\"\ngroup:\n  inner: \"I\"\n  deeper:\n    leaf: \"L\"\nnumber: 5\nlist:\n  - a\n",
                    "lang/zh.json", "{\"flat.key\": \"F\", \"n\": 3, \"o\": {\"x\": \"y\"}, \"b\": true}");

            Map<String, Map<String, String>> jar2 = ConfigTextDefaults.jarCatalogues(jar.toURI().toURL());

            assertThat(jar2.keySet()).containsExactly("en", "zh");
            assertThat(jar2.get("en")).containsOnlyKeys("top", "group.inner", "group.deeper.leaf")
                    .containsEntry("group.deeper.leaf", "L");
            assertThat(jar2.get("zh")).containsOnlyKeys("flat.key", "n", "b").containsEntry("flat.key", "F")
                    .containsEntry("n", "3").containsEntry("b", "true");
        }

        @Test
        @DisplayName("the first extension present wins, in the framework's order .json, .yml, .yaml")
        void extensionOrder() throws Exception {
            File jar = jar("m.jar",
                    "lang/en.yml", "k: \"from yml\"\n",
                    "lang/en.json", "{\"k\": \"from json\"}",
                    "lang/zh.yaml", "k: \"from yaml\"\n");

            Map<String, Map<String, String>> c = ConfigTextDefaults.jarCatalogues(jar.toURI().toURL());

            assertThat(c.get("en")).containsEntry("k", "from json");
            assertThat(c.get("zh")).containsEntry("k", "from yaml");
        }

        @Test
        @DisplayName("a class directory is read the same way as a jar")
        void classDirectory() throws Exception {
            Path dir = Files.createDirectories(temp.resolve("classes/lang"));
            Files.write(dir.resolve("en.yml"), "k: \"dir text\"\n".getBytes(StandardCharsets.UTF_8));

            Map<String, Map<String, String>> c = ConfigTextDefaults.jarCatalogues(temp.resolve("classes").toUri().toURL());

            assertThat(c.get("en")).containsEntry("k", "dir text");
            assertThat(c.get("zh")).isEmpty();
        }

        @Test
        @DisplayName("an absent location, an absent file, and an unparseable file each give an empty catalogue")
        void absentAndBroken() throws Exception {
            File jar = jar("broken.jar", "lang/en.yml", "k: [unclosed\n", "lang/zh.json", "{not json");
            Map<String, Map<String, String>> broken = ConfigTextDefaults.jarCatalogues(jar.toURI().toURL());
            assertThat(broken.get("en")).isEmpty();
            assertThat(broken.get("zh")).isEmpty();

            Map<String, Map<String, String>> none = ConfigTextDefaults.jarCatalogues((java.net.URL) null);
            assertThat(none.get("en")).isEmpty();
            assertThat(none.get("zh")).isEmpty();

            Map<String, Map<String, String>> missing = ConfigTextDefaults.jarCatalogues(temp.resolve("nowhere.jar").toUri().toURL());
            assertThat(missing.get("en")).isEmpty();
        }

        @Test
        @DisplayName("the tracked set is the shipped defaults plus the prefix and each language's jar text")
        void trackedSets() throws Exception {
            File jar = jar("m.jar", "lang/en.yml", "k: \"Hi\"\nl: \"a\\n\\nb\"\n", "lang/zh.yml", "k: \"Ni hao\"\nl: \"c\\nd\"\n");
            Map<String, Map<String, String>> c = ConfigTextDefaults.jarCatalogues(jar.toURI().toURL());

            assertThat(ConfigTextDefaults.tracked(c, "&e", "k", "old")).containsExactly("old", "&eHi", "&eNi hao");
            assertThat(ConfigTextDefaults.tracked(c, "", "absent", "old")).containsExactly("old");
            assertThat(ConfigTextDefaults.trackedLines(c, "l", Arrays.asList("x", "y")))
                    .containsExactly(Arrays.asList("x", "y"), Arrays.asList("a", "", "b"), Arrays.asList("c", "d"));
        }

        @Test
        @DisplayName("jarCatalogues(Class) reads the jar or class directory that holds the class, in both languages")
        void readsTheAnchorsOwnCodeSource() {
            Map<String, Map<String, String>> c = ConfigTextDefaults.jarCatalogues(ConfigTextDefaults.class);

            assertThat(c.keySet()).containsExactly("en", "zh");
            assertThat(c.get("en")).as("this module's own lang/en.*").isNotEmpty();
            assertThat(c.get("zh")).as("this module's own lang/zh.*").isNotEmpty();
        }

        private File jar(String name, String... entries) throws IOException {
            File file = temp.resolve(name).toFile();
            try (OutputStream out = Files.newOutputStream(file.toPath());
                 JarOutputStream jar = new JarOutputStream(out)) {
                for (int i = 0; i < entries.length; i += 2) {
                    jar.putNextEntry(new JarEntry(entries[i]));
                    jar.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                    jar.closeEntry();
                }
            }
            return file;
        }
    }
}
