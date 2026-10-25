package com.ultikits.plugins.login;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The release metadata the framework reads from the shipped {@code plugin.yml}, pinned against the
 * repository's own records (UltiKits/UltiTools-Reborn#474, #544).
 * <ul>
 *   <li>{@code api-version} is the lowest UltiTools level this build runs on. This module is built
 *       against UltiTools-API 6.3.1, so it must declare {@code 631}: an older framework then refuses it
 *       at load with a clear warning instead of failing part-way through start-up. The framework
 *       compares {@code api-version} with its own level, which writes its version's numbers together
 *       ({@code 6.3.1} is {@code 631}), so a patch release can raise the floor and the patch now
 *       counts.</li>
 *   <li>{@code identify-string} is the key the framework's update check and {@code /upm update} use to
 *       find this module in the UltiCloud catalogue; a module without it is skipped by both. It must
 *       equal {@code identifyString} in {@code ultikits.json}, the record the catalogue entry is
 *       published from.</li>
 *   <li>The README's UltiTools-API badge, and every "X.Y.Z or later" framework claim in its prose,
 *       must name the same floor, compared the way the framework encodes it: the three numbers
 *       written together, so {@code 6.3.10} ({@code 6310}) cannot pass for {@code 631}.</li>
 * </ul>
 * Files outside the test classpath are found from the project directory Surefire reports
 * ({@code basedir}), not from whatever directory the runner started in.
 */
@DisplayName("Release metadata: api-version 631, identify-string and the README's framework floor")
class PluginMetadataTest {

    /** The UltiTools level this module needs: 6.3.1, encoded the way {@code api-version} encodes it. */
    private static final int REQUIRED_API_VERSION = 631;

    /** The README's UltiTools-API badge, e.g. {@code UltiTools--API-6.3.0%2B-blue}. */
    private static final Pattern BADGE = Pattern.compile("UltiTools--API-(\\d+\\.\\d+\\.\\d+)(?:%2B|\\+)?-");

    /**
     * A minimum-version claim in prose: {@code 6.3.0+}, {@code 6.3.0 或更高版本}, {@code 6.3.0 or later}.
     * Only major version 6 is read, which in these READMEs is always the framework (module versions are
     * 1.x/2.x; Paper and Java are written with two components).
     */
    private static final Pattern MINIMUM_CLAIM =
            Pattern.compile("\\b(6\\.\\d+\\.\\d+)(?:\\+|%2B|\\s*或更高|\\s+or (?:later|newer|higher))");

    private static YamlConfiguration shippedPluginYml() throws IOException {
        InputStream in = PluginMetadataTest.class.getClassLoader().getResourceAsStream("plugin.yml");
        assertThat(in).as("shipped plugin.yml on the test classpath").isNotNull();
        try {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } finally {
            in.close();
        }
    }

    /** A file in the project directory ({@code basedir}, set by Surefire), else the working directory. */
    private static Path projectFile(String name) {
        String basedir = System.getProperty("basedir");
        if (basedir != null) {
            Path candidate = Paths.get(basedir, name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return Paths.get(name);
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * Whether {@code major.minor.patch} is exactly the framework level {@code apiVersion}: the
     * version's three numbers written together, the encoding {@code UltiTools.getPluginVersion()}
     * uses ({@code 6.3.1} is {@code 631}).
     */
    static boolean namesLevel(String version, int apiVersion) {
        String[] parts = version.split("\\.");
        return parts.length == 3 && (parts[0] + parts[1] + parts[2]).equals(String.valueOf(apiVersion));
    }

    @Test
    @DisplayName("plugin.yml declares api-version 631")
    void declaresApiVersion631() throws Exception {
        YamlConfiguration yml = shippedPluginYml();
        // Control: this is the module's own descriptor, not some other plugin.yml on the classpath.
        assertThat(yml.getString("main")).as("control: main class").isEqualTo(UltiLogin.class.getName());

        assertThat(yml.getInt("api-version", -1)).as("api-version").isEqualTo(REQUIRED_API_VERSION);
    }

    @Test
    @DisplayName("plugin.yml's identify-string equals ultikits.json's identifyString")
    void identifyStringMatchesUltikitsJson() throws Exception {
        JsonObject json = JsonParser.parseString(read(projectFile("ultikits.json"))).getAsJsonObject();
        String expected = json.get("identifyString").getAsString();
        // Control: the catalogue record really carries a value, so equality below is not vacuous.
        assertThat(expected).as("control: ultikits.json identifyString").isNotBlank();

        assertThat(shippedPluginYml().getString("identify-string"))
                .as("plugin.yml identify-string")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("the README's badge and every framework minimum in its prose name UltiTools-API 6.3.1")
    void readmeNamesTheDeclaredFloor() throws Exception {
        String text = read(projectFile("README.md"));
        Matcher badge = BADGE.matcher(text);
        // Control: the badge is really there and parsed, so the comparison below is not vacuous.
        assertThat(badge.find()).as("README carries the UltiTools-API badge").isTrue();
        assertThat(namesLevel(badge.group(1), REQUIRED_API_VERSION))
                .as("badge %s names UltiTools-API 6.3.1", badge.group(1))
                .isTrue();

        List<String> stale = new ArrayList<>();
        Matcher claim = MINIMUM_CLAIM.matcher(text);
        while (claim.find()) {
            if (!namesLevel(claim.group(1), REQUIRED_API_VERSION)) {
                stale.add(claim.group());
            }
        }
        assertThat(stale).as("framework minimums in the README that are not 6.3.1").isEmpty();
    }

    @Test
    @DisplayName("control: 6.3.1 names 631; 6.3.0, 6.3.10, 6.4.1 and 7.3.1 do not")
    void levelComparison() {
        assertThat(namesLevel("6.3.1", 631)).isTrue();
        assertThat(namesLevel("6.3.0", 631)).isFalse();
        assertThat(namesLevel("6.3.10", 631)).isFalse();
        assertThat(namesLevel("6.4.1", 631)).isFalse();
        assertThat(namesLevel("7.3.1", 631)).isFalse();
    }

    @Test
    @DisplayName("control: the prose pattern finds 6.2.0+, 6.2.0 或更高版本 and 6.2.0 or later")
    void minimumClaimPatternFindsEachForm() {
        for (String sample : new String[] {"UltiTools-API 6.2.0+ 框架", "6.2.0 或更高版本", "6.2.0 or later"}) {
            Matcher m = MINIMUM_CLAIM.matcher(sample);
            assertThat(m.find()).as(sample).isTrue();
            assertThat(m.group(1)).isEqualTo("6.2.0");
        }
    }
}
