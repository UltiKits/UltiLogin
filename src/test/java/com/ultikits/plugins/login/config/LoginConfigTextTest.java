package com.ultikits.plugins.login.config;

import com.ultikits.plugins.login.UltiLogin;
import com.ultikits.plugins.login.commands.LoginAdminCommand;
import com.ultikits.plugins.login.commands.LoginCommand;
import com.ultikits.plugins.login.i18n.CatalogueText;
import com.ultikits.plugins.login.i18n.LoginSeams;
import com.ultikits.plugins.login.listener.LoginProtectionListener;
import com.ultikits.plugins.login.service.LoginService;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.interfaces.ConfigChangeListener;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code config/login.yml} holds every GUI title and message in the server's language, and the module
 * sends exactly what the file holds (maintainer decision 2026-09-25; UltiKits/UltiLogin#20).
 * A value that is still built-in text — the colour code plus any language's text from this jar, or the
 * default an earlier version shipped — follows {@code language} at enable and on reload, in both
 * directions; anything else is the operator's and is kept byte for byte. Every case runs the framework's
 * real {@code AbstractConfigEntity#init} on a temporary folder, the module's real {@code registerSelf()}
 * and {@code onReload()}, and answers {@code i18n} from the module's real catalogues.
 */
@DisplayName("login.yml holds the titles and messages in the server's language (UltiKits/UltiLogin#20)")
class LoginConfigTextTest {

    /** One text setting: its field, its path in login.yml, its catalogue key, its colour, its shipped default. */
    private static final class Setting {
        final String field;
        final String path;
        final String key;
        final String prefix;
        final String shipped;

        Setting(String field, String path, String key, String prefix, String shipped) {
            this.field = field;
            this.path = path;
            this.key = key;
            this.prefix = prefix;
            this.shipped = shipped;
        }

        String text(String code) {
            return prefix + CatalogueText.text(code, key);
        }

        String getter() {
            return "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
        }
    }

    /** The 24 settings, with the one default each shipped in every earlier version (census §4). */
    private static final List<Setting> SETTINGS = Arrays.asList(
            new Setting("guiLoginTitle", "gui-mode.title-login", "gui_enter_password", "&6", "&6请输入密码"),
            new Setting("guiRegisterTitle", "gui-mode.title-register", "gui_set_password", "&6", "&6请设置密码"),
            new Setting("guiConfirmTitle", "gui-mode.title-confirm", "gui_confirm_password", "&6", "&6请再次输入密码"),
            new Setting("registerPrompt", "messages.register-prompt", "register_prompt", "&e", "&e请使用 /register <密码> <确认密码> 注册账号"),
            new Setting("registerPromptGui", "messages.register-prompt-gui", "register_prompt_gui", "&e", "&e请在弹出的界面中设置密码"),
            new Setting("loginPrompt", "messages.login-prompt", "login_prompt", "&e", "&e请使用 /login <密码> 登录"),
            new Setting("loginPromptGui", "messages.login-prompt-gui", "login_prompt_gui", "&e", "&e请在弹出的界面中输入密码"),
            new Setting("registerSuccess", "messages.register-success", "register_success", "&a", "&a注册成功！欢迎加入服务器！"),
            new Setting("loginSuccess", "messages.login-success", "login_success", "&a", "&a登录成功！欢迎回来！"),
            new Setting("alreadyLogged", "messages.already-logged", "already_logged", "&e", "&e你已经登录了！"),
            new Setting("notRegistered", "messages.not-registered", "not_registered", "&c", "&c你还没有注册！请先注册。"),
            new Setting("alreadyRegistered", "messages.already-registered", "already_registered", "&c", "&c你已经注册过了！请直接登录。"),
            new Setting("passwordMismatch", "messages.password-mismatch", "password_mismatch", "&c", "&c两次输入的密码不一致！"),
            new Setting("passwordTooShort", "messages.password-too-short", "password_too_short", "&c", "&c密码太短！至少需要 {MIN} 个字符。"),
            new Setting("passwordTooLong", "messages.password-too-long", "password_too_long", "&c", "&c密码太长！最多 {MAX} 个字符。"),
            new Setting("timeoutKick", "messages.timeout-kick", "timeout_kick", "&c", "&c登录超时！请重新连接。"),
            new Setting("accountLocked", "messages.account-locked", "account_locked", "&c", "&c登录失败次数过多！请在 {TIME} 秒后重试。"),
            new Setting("attemptsRemaining", "messages.attempts-remaining", "attempts_remaining", "&c", "&c密码错误！剩余尝试次数: {COUNT}"),
            new Setting("guiPasswordInvalid", "messages.gui-password-invalid", "gui_password_invalid", "&c", "&c密码必须是 {LENGTH} 位数字！"),
            new Setting("adminPasswordReset", "messages.admin.password-reset", "admin_password_reset", "&a", "&a已重置玩家 {PLAYER} 的密码为: {PASSWORD}"),
            new Setting("adminForceLogin", "messages.admin.force-login", "admin_force_login", "&a", "&a已强制登录玩家 {PLAYER}"),
            new Setting("adminUnregister", "messages.admin.unregister", "admin_unregister", "&a", "&a已删除玩家 {PLAYER} 的账号"),
            new Setting("adminPlayerNotFound", "messages.admin.player-not-found", "admin_player_not_found", "&c", "&c找不到玩家 {PLAYER}"),
            new Setting("adminAccountNotFound", "messages.admin.account-not-found", "admin_account_not_found", "&c", "&c玩家 {PLAYER} 尚未注册"));

    /** The fields that carried {@code @NotEmpty} at origin/master: the 24 above plus three that never changed. */
    private static final Set<String> NOT_EMPTY_AT_MASTER = new TreeSet<>();

    static {
        for (Setting s : SETTINGS) {
            NOT_EMPTY_AT_MASTER.add(s.field);
        }
        NOT_EMPTY_AT_MASTER.addAll(Arrays.asList("lockoutType", "spawnWorld", "allowedCommands"));
    }

    private static final String[] LANGUAGES = {"en", "zh"};

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    @TempDir
    Path tempDir;

    private final String[] language = {"en"};

    private final PluginLogger logger = mock(PluginLogger.class);

    /** Catalogue texts an operator changed in the extracted language file on disk, answered by i18n first. */
    private final Map<String, String> diskOverrides = new LinkedHashMap<>();

    /** The configuration the module double returns from {@code getConfig(LoginConfig.class)}. */
    private LoginConfig current;

    private UltiLogin plugin;

    @BeforeEach
    void setUp() {
        plugin = moduleDouble();
    }

    @AfterEach
    void tearDown() {
        current = null;
    }

    @Test
    @DisplayName("the catalogues give each setting English text under en and exactly its shipped default under zh")
    void catalogueTexts() {
        for (Setting s : SETTINGS) {
            assertThat(s.text("zh")).as(s.field).isEqualTo(s.shipped);
            assertThat(s.text("en")).as(s.field).startsWith(s.prefix).doesNotMatch(".*" + CJK.pattern() + ".*");
        }
    }

    @Test
    @DisplayName("fresh start under en: login.yml holds every setting's English text, and each getter returns the file's value")
    void freshStartEnglish() throws Exception {
        language[0] = "en";
        LoginConfig config = spy(load());

        start(config);

        YamlConfiguration disk = onDisk();
        for (Setting s : SETTINGS) {
            assertThat(disk.getString(s.path)).as(s.path).isEqualTo(s.text("en"));
            assertThat(get(config, s)).as(s.field).isEqualTo(disk.getString(s.path));
        }
        verify(config, times(1)).save();
    }

    @Test
    @DisplayName("fresh start under zh: login.yml holds every setting's Chinese text, which is its shipped default, and the module writes nothing")
    void freshStartChinese() throws Exception {
        language[0] = "zh";
        LoginConfig config = spy(load());
        byte[] afterFramework = bytes();

        start(config);

        YamlConfiguration disk = onDisk();
        for (Setting s : SETTINGS) {
            assertThat(disk.getString(s.path)).as(s.path).isEqualTo(s.shipped);
            assertThat(get(config, s)).as(s.field).isEqualTo(s.shipped);
        }
        verify(config, never()).save();
        assertThat(bytes()).isEqualTo(afterFramework);
    }

    @Test
    @DisplayName("every built-in text in the file (shipped default, jar en text, jar zh text) is replaced with the current language's text and saved, under en and zh")
    void everyTrackedValueFollowsTheLanguage() throws Exception {
        for (String code : LANGUAGES) {
            for (String member : new String[] {"shipped", "en", "zh"}) {
                language[0] = code;
                Map<String, String> values = new LinkedHashMap<>();
                for (Setting s : SETTINGS) {
                    values.put(s.path, "shipped".equals(member) ? s.shipped : s.text(member));
                }
                write(values);
                LoginConfig config = spy(load());

                start(config);

                YamlConfiguration disk = onDisk();
                for (Setting s : SETTINGS) {
                    String what = "language " + code + ", file held the " + member + " text of " + s.path;
                    assertThat(disk.getString(s.path)).as(what).isEqualTo(s.text(code));
                    assertThat(get(config, s)).as(what).isEqualTo(s.text(code));
                }
                boolean alreadyCurrent = member.equals(code) || ("shipped".equals(member) && "zh".equals(code));
                verify(config, times(alreadyCurrent ? 0 : 1)).save();
            }
        }
    }

    @Test
    @DisplayName("an upgraded file holding the shipped defaults reads exactly the English text under en (pinned, not read from the catalogue)")
    void upgradedFileReadsExactEnglish() throws Exception {
        language[0] = "en";
        Map<String, String> values = new LinkedHashMap<>();
        for (Setting s : SETTINGS) {
            values.put(s.path, s.shipped);
        }
        write(values);
        LoginConfig config = spy(load());

        start(config);

        YamlConfiguration disk = onDisk();
        assertThat(disk.getString("gui-mode.title-login")).isEqualTo("&6Enter Password");
        assertThat(disk.getString("messages.login-prompt")).isEqualTo("&ePlease use /login <password> to login");
        assertThat(disk.getString("messages.login-success")).isEqualTo("&aLogin successful! Welcome back!");
        assertThat(disk.getString("messages.admin.player-not-found")).isEqualTo("&cPlayer {PLAYER} not found");
        assertThat(config.getLoginSuccess()).isEqualTo("&aLogin successful! Welcome back!");
        verify(config, times(1)).save();
    }

    @Test
    @DisplayName("a customised value, or built-in text changed by one character, is kept byte for byte under both languages and the file is not rewritten")
    void customisedValuesAreKept() throws Exception {
        for (String code : LANGUAGES) {
            for (String variant : new String[] {"shipped!", "en!", "own"}) {
                language[0] = code;
                Map<String, String> values = new LinkedHashMap<>();
                for (Setting s : SETTINGS) {
                    String v;
                    if ("shipped!".equals(variant)) {
                        v = s.shipped + "!";
                    } else if ("en!".equals(variant)) {
                        v = s.text("en") + " ";
                    } else {
                        v = "&dOperator text for " + s.field;
                    }
                    values.put(s.path, v);
                }
                write(values);
                LoginConfig config = spy(load());
                byte[] before = bytes();

                start(config);

                assertThat(bytes()).as(code + " " + variant).isEqualTo(before);
                for (Setting s : SETTINGS) {
                    assertThat(get(config, s)).as(code + " " + variant + " " + s.field).isEqualTo(values.get(s.path));
                }
                verify(config, never()).save();
            }
        }
    }

    @Test
    @DisplayName("a second enable with the same language writes nothing")
    void secondEnableWritesNothing() throws Exception {
        for (String code : LANGUAGES) {
            language[0] = code;
            Map<String, String> values = new LinkedHashMap<>();
            for (Setting s : SETTINGS) {
                values.put(s.path, s.shipped);
            }
            write(values);
            start(load());
            byte[] afterFirst = bytes();

            LoginConfig second = spy(load());
            start(second);

            assertThat(bytes()).as(code).isEqualTo(afterFirst);
            verify(second, never()).save();
        }
    }

    @Test
    @DisplayName("onReload() after a language switch rewrites every setting in the new language, in both directions")
    void reloadFollowsALanguageSwitchBothWays() throws Exception {
        for (String[] direction : new String[][] {{"en", "zh"}, {"zh", "en"}}) {
            language[0] = direction[0];
            Files.deleteIfExists(file().toPath());
            LoginConfig config = load();
            start(config);

            language[0] = direction[1];
            config.init(plugin);
            reload();

            YamlConfiguration disk = onDisk();
            for (Setting s : SETTINGS) {
                String what = direction[0] + " -> " + direction[1] + ": " + s.path;
                assertThat(disk.getString(s.path)).as(what).isEqualTo(s.text(direction[1]));
                assertThat(get(config, s)).as(what).isEqualTo(s.text(direction[1]));
            }
        }
    }

    @Test
    @DisplayName("no configuration change listener rewrites the text (the framework fires them before it reloads the language)")
    void changeListenersDoNotMaterialize() throws Exception {
        language[0] = "en";
        LoginConfig config = load();
        start(config);
        byte[] before = bytes();

        language[0] = "zh";
        // This module registers no change listener, and the framework registers none for it, so nothing
        // can write the file before the framework rebuilds the language; pinned so that adding one is seen.
        assertThat(config.getChangeListeners()).isEmpty();
        for (ConfigChangeListener listener : new ArrayList<>(config.getChangeListeners())) {
            listener.onConfigReload(config);
        }

        assertThat(bytes()).isEqualTo(before);
        assertThat(config.getLoginSuccess()).isEqualTo(SETTINGS.get(8).text("en"));
    }

    @Test
    @DisplayName("an operator-edited language file on disk does not widen what counts as built-in text")
    void diskCatalogueDoesNotWidenTheTrackedSet() throws Exception {
        Path lang = Files.createDirectories(tempDir.resolve("lang"));
        StringBuilder json = new StringBuilder("{");
        for (Setting s : SETTINGS) {
            json.append(json.length() > 1 ? "," : "").append('"').append(s.key).append("\":\"Edited ").append(s.key).append('"');
        }
        json.append('}');
        for (String code : LANGUAGES) {
            Files.write(lang.resolve(code + ".json"), json.toString().getBytes(StandardCharsets.UTF_8));
        }
        for (String code : LANGUAGES) {
            language[0] = code;
            Map<String, String> values = new LinkedHashMap<>();
            for (Setting s : SETTINGS) {
                values.put(s.path, s.prefix + "Edited " + s.key);
            }
            write(values);
            LoginConfig config = spy(load());

            start(config);

            for (Setting s : SETTINGS) {
                assertThat(onDisk().getString(s.path)).as(code + " " + s.path).isEqualTo(values.get(s.path));
            }
            verify(config, never()).save();
        }
    }

    @Test
    @DisplayName("an install upgraded with an earlier extracted zh language file (the words, no colour codes) keeps every shipped default unchanged under zh")
    void earlierExtractedCatalogueKeepsTheColour() throws Exception {
        // The previous release's lang/zh.json held these keys with the same words and no colour
        // codes, and an upgraded install reads that file first; the colour is the module's.
        final Map<String, String> previous = new LinkedHashMap<>();
        for (Setting s : SETTINGS) {
            previous.put(s.key, s.shipped.substring(s.prefix.length()));
        }
        final com.ultikits.ultitools.entities.Language earlier = new com.ultikits.ultitools.entities.Language(previous);
        language[0] = "zh";
        Map<String, String> values = new LinkedHashMap<>();
        for (Setting s : SETTINGS) {
            values.put(s.path, s.shipped);
        }
        write(values);
        UltiLogin upgraded = Mockito.mock(UltiLogin.class, invocation -> "i18n".equals(invocation.getMethod().getName())
                ? earlier.getLocalizedText(invocation.<String>getArgument(invocation.getArguments().length - 1))
                : moduleAnswer(invocation));
        LoginConfig config = new LoginConfig();
        config.init(upgraded);
        LoginConfig spied = spy(config);
        current = spied;
        byte[] before = bytes();

        upgraded.registerSelf();

        assertThat(bytes()).isEqualTo(before);
        verify(spied, never()).save();
        for (Setting s : SETTINGS) {
            assertThat(get(spied, s)).as(s.field).isEqualTo(s.shipped);
        }
    }

    @Test
    @DisplayName("an operator's edit of the extracted language file is not written into login.yml, so each value keeps following a language switch")
    void diskCatalogueEditDoesNotReachTheFile() throws Exception {
        for (Setting s : SETTINGS) {
            diskOverrides.put(s.key, "Edited " + s.key);
        }
        language[0] = "en";
        Map<String, String> values = new LinkedHashMap<>();
        for (Setting s : SETTINGS) {
            values.put(s.path, s.shipped);
        }
        write(values);
        LoginConfig config = load();
        start(config);

        for (Setting s : SETTINGS) {
            assertThat(onDisk().getString(s.path)).as("en, " + s.path + ": the jar's text, not the disk edit").isEqualTo(s.text("en"));
        }

        language[0] = "zh";
        config.init(plugin);
        reload();

        for (Setting s : SETTINGS) {
            assertThat(onDisk().getString(s.path)).as("after a switch to zh, " + s.path + " follows").isEqualTo(s.text("zh"));
        }
    }

    @Test
    @DisplayName("a file that cannot be saved is reported in the server's language, and the module still uses the new text")
    void saveFailureIsReported() throws Exception {
        language[0] = "en";
        LoginConfig config = spy(load());
        doThrow(new IOException("read-only")).when(config).save();

        assertThat(start(config)).isTrue();

        String expected = CatalogueText.text("en", "log_config_default_save_failed").replace("{FILE}", LoginConfig.CONFIG_FILE);
        verify(logger).warn(any(IOException.class), eq(expected));
        assertThat(config.getLoginSuccess()).isEqualTo(SETTINGS.get(8).text("en"));
    }

    @Test
    @DisplayName("/login replies are rendered from the file's text")
    void loginRepliesUseTheFile() throws Exception {
        for (String code : LANGUAGES) {
            language[0] = code;
            Files.deleteIfExists(file().toPath());
            LoginConfig config = load();
            start(config);
            YamlConfiguration disk = onDisk();
            LoginService service = mock(LoginService.class);
            LoginSeams.speak(service, code);
            when(service.getConfig()).thenReturn(config);
            Player player = mock(Player.class);
            UUID id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id);

            when(service.isLoggedIn(id)).thenReturn(true);
            new LoginCommand(service).login(player, "secret");
            when(service.isLoggedIn(id)).thenReturn(false);
            when(service.isRegistered(id)).thenReturn(false);
            new LoginCommand(service).login(player, "secret");

            verify(player).sendMessage(ChatColor.translateAlternateColorCodes('&', disk.getString("messages.already-logged")));
            verify(player).sendMessage(ChatColor.translateAlternateColorCodes('&', disk.getString("messages.not-registered")));
        }
    }

    @Test
    @DisplayName("an admin message is rendered from the file's text")
    void adminMessageUsesTheFile() throws Exception {
        language[0] = "en";
        LoginConfig config = load();
        start(config);
        LoginService service = mock(LoginService.class);
        LoginSeams.speak(service, "en");
        when(service.getConfig()).thenReturn(config);
        CommandSender sender = mock(CommandSender.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer("Nobody")).thenReturn(null);
            new LoginAdminCommand(service).forceLogin(sender, "Nobody");
        }

        String fileText = onDisk().getString("messages.admin.player-not-found");
        assertThat(fileText).doesNotMatch(".*" + CJK.pattern() + ".*");
        verify(sender).sendMessage(ChatColor.translateAlternateColorCodes('&', fileText.replace("{PLAYER}", "Nobody")));
    }

    @Test
    @DisplayName("the keypad GUI is still recognised by the three written titles, in both languages")
    void credentialGuiRecognisesTheWrittenTitles() throws Exception {
        for (String code : LANGUAGES) {
            language[0] = code;
            Files.deleteIfExists(file().toPath());
            LoginConfig config = load();
            start(config);
            YamlConfiguration disk = onDisk();
            LoginService service = mock(LoginService.class);
            LoginSeams.speak(service, code);
            when(service.getConfig()).thenReturn(config);
            Player player = mock(Player.class);
            UUID id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id);
            when(service.isLoggedIn(id)).thenReturn(false);
            LoginProtectionListener listener;
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
                listener = new LoginProtectionListener(plugin, service);
            }

            for (String path : new String[] {"gui-mode.title-login", "gui-mode.title-register", "gui-mode.title-confirm"}) {
                InventoryClickEvent click = mock(InventoryClickEvent.class);
                InventoryView view = mock(InventoryView.class);
                when(click.getWhoClicked()).thenReturn(player);
                when(click.getView()).thenReturn(view);
                when(view.getTitle()).thenReturn(ChatColor.translateAlternateColorCodes('&', disk.getString(path)));

                listener.onInventoryClick(click);

                verify(click, never()).setCancelled(true);
            }
            InventoryClickEvent other = mock(InventoryClickEvent.class);
            InventoryView chest = mock(InventoryView.class);
            when(other.getWhoClicked()).thenReturn(player);
            when(other.getView()).thenReturn(chest);
            when(chest.getTitle()).thenReturn("Chest");
            listener.onInventoryClick(other);
            verify(other).setCancelled(true);
        }
    }

    @Test
    @DisplayName("@NotEmpty is on exactly the fields that carried it at origin/master, and each text field's Java default is its shipped default")
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    void validationAndJavaDefaults() throws Exception {
        Set<String> notEmpty = new TreeSet<>();
        for (Field f : LoginConfig.class.getDeclaredFields()) {
            if (f.isAnnotationPresent(ConfigEntry.class) && f.isAnnotationPresent(NotEmpty.class)) {
                notEmpty.add(f.getName());
            }
        }
        assertThat(notEmpty).containsExactlyElementsOf(NOT_EMPTY_AT_MASTER);

        LoginConfig fresh = new LoginConfig();
        for (Setting s : SETTINGS) {
            Field f = LoginConfig.class.getDeclaredField(s.field);
            f.setAccessible(true);
            assertThat(f.get(fresh)).as(s.field).isEqualTo(s.shipped);
            assertThat(f.getAnnotation(ConfigEntry.class).path()).as(s.field).isEqualTo(s.path);
        }
    }

    // ---- harness ----

    private static String get(LoginConfig config, Setting s) throws Exception {
        return (String) LoginConfig.class.getMethod(s.getter()).invoke(config);
    }

    private File file() {
        return new File(tempDir.toFile(), LoginConfig.CONFIG_FILE);
    }

    private byte[] bytes() throws IOException {
        return Files.readAllBytes(file().toPath());
    }

    private YamlConfiguration onDisk() {
        return YamlConfiguration.loadConfiguration(file());
    }

    /** Writes a login.yml holding {@code values} (path to value), as an earlier version or an operator left it. */
    private void write(Map<String, String> values) throws IOException {
        Files.createDirectories(file().getParentFile().toPath());
        YamlConfiguration persisted = new YamlConfiguration();
        for (Map.Entry<String, String> e : values.entrySet()) {
            persisted.set(e.getKey(), e.getValue());
        }
        persisted.save(file());
    }

    /** The framework's own load: {@code init} fills missing keys with the Java defaults, saves, validates. */
    private LoginConfig load() throws IOException {
        Files.createDirectories(file().getParentFile().toPath());
        LoginConfig config = new LoginConfig();
        config.init(plugin);
        return config;
    }

    /** The module's enable path: {@code UltiLogin#registerSelf()} with {@code config} as the module's configuration. */
    private boolean start(LoginConfig config) {
        current = config;
        return plugin.registerSelf();
    }

    /** The module's {@code onReload()} (protected), as the framework calls it after rebuilding the language. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void reload() throws Exception {
        java.lang.reflect.Method onReload = UltiLogin.class.getDeclaredMethod("onReload");
        onReload.setAccessible(true);
        onReload.invoke(plugin);
    }

    /**
     * A module double whose {@code registerSelf()} and {@code onReload()} are the real ones, whose
     * configuration folder is the temporary directory, whose {@code i18n} answers from the module's real
     * catalogue for the language in {@link #language} (read at call time), and whose
     * {@code getConfig(LoginConfig.class)} is {@link #current}.
     */
    private UltiLogin moduleDouble() {
        return Mockito.mock(UltiLogin.class, this::moduleAnswer);
    }

    private Object moduleAnswer(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
        final Map<String, org.mockito.stubbing.Answer<String>> answers = new LinkedHashMap<>();
        for (String code : LANGUAGES) {
            answers.put(code, CatalogueText.answer(code));
        }
        {
            String name = invocation.getMethod().getName();
            switch (name) {
                case "registerSelf":
                case "onReload":
                    return invocation.callRealMethod();
                case "getConfigFolder":
                    return tempDir.toString();
                case "getConfigFile":
                    return new File(tempDir.toFile(), invocation.<String>getArgument(0));
                case "operatorConfigFile":
                    return file();
                case "i18n": {
                    String key = invocation.getArgument(invocation.getArguments().length - 1);
                    return diskOverrides.containsKey(key) ? diskOverrides.get(key) : answers.get(language[0]).answer(invocation);
                }
                case "getLanguageCode":
                    return language[0];
                case "getLogger":
                    return logger;
                case "getConfig":
                    return current;
                default:
                    return Answers.RETURNS_DEFAULTS.answer(invocation);
            }
        }
    }
}
