package com.ultikits.plugins.login.config;

import java.util.Arrays;
import java.util.List;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.annotations.config.Range;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for UltiLogin.
 *
 * @author wisdomme
 * @version 1.1.0
 */
@Getter
@Setter
@ConfigEntity(LoginConfig.CONFIG_FILE)
public class LoginConfig extends AbstractConfigEntity {

    /**
     * This entity's file, relative to the module's folder. The one place the path is written: the
     * annotation above, the constructor and the removed-key check in {@code UltiLogin} all read it
     * from here, so they cannot drift apart (UltiKits/UltiLogin#23).
     */
    public static final String CONFIG_FILE = "config/login.yml";
    
    // ==================== 基础设置 ====================

    @Range(min = 10, max = 600)
    @ConfigEntry(path = "login-timeout", comment = "登录超时时间（秒），超时将被踢出")
    private int loginTimeout = 60;

    @ConfigEntry(path = "session-enabled", comment = "启用会话功能（同一IP短期内无需重新登录）")
    private boolean sessionEnabled = true;

    @Range(min = 1, max = 1440)
    @ConfigEntry(path = "session-timeout", comment = "会话过期时间（分钟）")
    private int sessionTimeout = 30;

    @Range(min = 0, max = 100)
    @ConfigEntry(path = "max-register-per-ip", comment = "同一IP最大注册账户数（0为不限制）")
    private int maxRegisterPerIp = 3;
    
    // ==================== GUI 模式设置 ====================

    @ConfigEntry(path = "gui-mode.enabled", comment = "启用GUI登录模式（数字键盘界面）")
    private boolean guiModeEnabled = false;

    @Range(min = 1, max = 9)
    @ConfigEntry(path = "gui-mode.password-length", comment = "GUI模式密码位数（1-9数字，推荐4-6位）")
    private int guiPasswordLength = 4;

    @ConfigEntry(path = "gui-mode.title-login", comment = "GUI登录界面标题（留空：使用语言文件中的文本）")
    private String guiLoginTitle = "";

    @ConfigEntry(path = "gui-mode.title-register", comment = "GUI注册界面标题（留空：使用语言文件中的文本）")
    private String guiRegisterTitle = "";

    @ConfigEntry(path = "gui-mode.title-confirm", comment = "GUI确认密码界面标题（留空：使用语言文件中的文本）")
    private String guiConfirmTitle = "";
    
    // ==================== 命令模式密码设置 ====================

    @Range(min = 4, max = 32)
    @ConfigEntry(path = "password.min-length", comment = "命令模式密码最小长度")
    private int minPasswordLength = 6;

    @Range(min = 6, max = 128)
    @ConfigEntry(path = "password.max-length", comment = "命令模式密码最大长度")
    private int maxPasswordLength = 32;
    
    // ==================== 登录安全保护 ====================

    @Range(min = 0, max = 20)
    @ConfigEntry(path = "security.max-login-attempts", comment = "最大登录失败次数（0为不限制）")
    private int maxLoginAttempts = 5;

    @Range(min = 60, max = 86400)
    @ConfigEntry(path = "security.lockout-duration", comment = "登录失败封禁时长（秒）")
    private int lockoutDuration = 900;

    @NotEmpty
    @ConfigEntry(path = "security.lockout-type", comment = "封禁类型：IP / UUID / BOTH")
    private String lockoutType = "IP";
    
    // ==================== 位置设置 ====================

    @ConfigEntry(path = "spawn-location.enabled", comment = "未登录时传送到指定位置")
    private boolean spawnLocationEnabled = false;

    @NotEmpty
    @ConfigEntry(path = "spawn-location.world", comment = "出生点世界")
    private String spawnWorld = "world";

    @ConfigEntry(path = "spawn-location.x", comment = "出生点X")
    private double spawnX = 0;

    @Range(min = -64, max = 320)
    @ConfigEntry(path = "spawn-location.y", comment = "出生点Y")
    private double spawnY = 64;

    @ConfigEntry(path = "spawn-location.z", comment = "出生点Z")
    private double spawnZ = 0;
    
    // ==================== 其他设置 ====================

    @NotEmpty
    @ConfigEntry(path = "allowed-commands", comment = "未登录时允许执行的命令")
    private List<String> allowedCommands = Arrays.asList("login", "l", "register", "reg", "panel", "regs", "recover");

    @ConfigEntry(path = "blind-effect", comment = "未登录时给予失明效果")
    private boolean blindEffect = true;

    // ==================== UltiCloud 集成 ====================

    @ConfigEntry(path = "ulticloud.enabled", comment = "Enable UltiCloud integration for web panel login")
    private boolean ulticloudEnabled = false;

    // ==================== 消息配置 ====================

    @ConfigEntry(path = "messages.register-prompt", comment = "注册提示（命令模式）（留空：使用语言文件中的文本）")
    private String registerPrompt = "";

    @ConfigEntry(path = "messages.register-prompt-gui", comment = "注册提示（GUI模式）（留空：使用语言文件中的文本）")
    private String registerPromptGui = "";

    @ConfigEntry(path = "messages.login-prompt", comment = "登录提示（命令模式）（留空：使用语言文件中的文本）")
    private String loginPrompt = "";

    @ConfigEntry(path = "messages.login-prompt-gui", comment = "登录提示（GUI模式）（留空：使用语言文件中的文本）")
    private String loginPromptGui = "";

    @ConfigEntry(path = "messages.register-success", comment = "注册成功（留空：使用语言文件中的文本）")
    private String registerSuccess = "";

    @ConfigEntry(path = "messages.login-success", comment = "登录成功（留空：使用语言文件中的文本）")
    private String loginSuccess = "";

    @ConfigEntry(path = "messages.already-logged", comment = "已经登录（留空：使用语言文件中的文本）")
    private String alreadyLogged = "";

    @ConfigEntry(path = "messages.not-registered", comment = "未注册（留空：使用语言文件中的文本）")
    private String notRegistered = "";

    @ConfigEntry(path = "messages.already-registered", comment = "已注册（留空：使用语言文件中的文本）")
    private String alreadyRegistered = "";

    @ConfigEntry(path = "messages.password-mismatch", comment = "密码不匹配（留空：使用语言文件中的文本）")
    private String passwordMismatch = "";

    @ConfigEntry(path = "messages.password-too-short", comment = "密码太短（留空：使用语言文件中的文本）")
    private String passwordTooShort = "";

    @ConfigEntry(path = "messages.password-too-long", comment = "密码太长（留空：使用语言文件中的文本）")
    private String passwordTooLong = "";

    @ConfigEntry(path = "messages.timeout-kick", comment = "超时踢出（留空：使用语言文件中的文本）")
    private String timeoutKick = "";

    @ConfigEntry(path = "messages.account-locked", comment = "账户被锁定（留空：使用语言文件中的文本）")
    private String accountLocked = "";

    @ConfigEntry(path = "messages.attempts-remaining", comment = "剩余尝试次数（留空：使用语言文件中的文本）")
    private String attemptsRemaining = "";

    @ConfigEntry(path = "messages.gui-password-invalid", comment = "GUI密码无效（留空：使用语言文件中的文本）")
    private String guiPasswordInvalid = "";

    // ==================== 管理员消息 ====================

    @ConfigEntry(path = "messages.admin.password-reset", comment = "管理员重置密码成功（留空：使用语言文件中的文本）")
    private String adminPasswordReset = "";

    @ConfigEntry(path = "messages.admin.force-login", comment = "管理员强制登录（留空：使用语言文件中的文本）")
    private String adminForceLogin = "";

    @ConfigEntry(path = "messages.admin.unregister", comment = "管理员删除账号（留空：使用语言文件中的文本）")
    private String adminUnregister = "";

    @ConfigEntry(path = "messages.admin.player-not-found", comment = "玩家不存在（留空：使用语言文件中的文本）")
    private String adminPlayerNotFound = "";

    @ConfigEntry(path = "messages.admin.account-not-found", comment = "账号不存在（留空：使用语言文件中的文本）")
    private String adminAccountNotFound = "";
    
    // ==================== Text defaults an earlier version shipped ====================
    // Every GUI title and message below used to ship a fixed Chinese default. Each now defaults to
    // blank and reads the language file's text, in the server's language, while it stays blank
    // (maintainer ruling 2026-09-24 (d), UltiKits/UltiLogin#20). The colour stays the module's: each
    // getter opens with the colour code its shipped default had, because a language file extracted by
    // an earlier version holds the same keys with the same words and no colour codes, and an upgraded
    // install reads that file first. These are the old values, one per
    // setting across this module's history, kept only so migrateLegacyDefaults() can recognise them
    // in an upgraded operator's file.

    /** The default every earlier version shipped for {@code gui-mode.title-login}; compared, never shown. */
    static final String SHIPPED_GUI_LOGIN_TITLE = "&6请输入密码";

    /** The default every earlier version shipped for {@code gui-mode.title-register}; compared, never shown. */
    static final String SHIPPED_GUI_REGISTER_TITLE = "&6请设置密码";

    /** The default every earlier version shipped for {@code gui-mode.title-confirm}; compared, never shown. */
    static final String SHIPPED_GUI_CONFIRM_TITLE = "&6请再次输入密码";

    /** The default every earlier version shipped for {@code messages.register-prompt}; compared, never shown. */
    static final String SHIPPED_REGISTER_PROMPT = "&e请使用 /register <密码> <确认密码> 注册账号";

    /** The default every earlier version shipped for {@code messages.register-prompt-gui}; compared, never shown. */
    static final String SHIPPED_REGISTER_PROMPT_GUI = "&e请在弹出的界面中设置密码";

    /** The default every earlier version shipped for {@code messages.login-prompt}; compared, never shown. */
    static final String SHIPPED_LOGIN_PROMPT = "&e请使用 /login <密码> 登录";

    /** The default every earlier version shipped for {@code messages.login-prompt-gui}; compared, never shown. */
    static final String SHIPPED_LOGIN_PROMPT_GUI = "&e请在弹出的界面中输入密码";

    /** The default every earlier version shipped for {@code messages.register-success}; compared, never shown. */
    static final String SHIPPED_REGISTER_SUCCESS = "&a注册成功！欢迎加入服务器！";

    /** The default every earlier version shipped for {@code messages.login-success}; compared, never shown. */
    static final String SHIPPED_LOGIN_SUCCESS = "&a登录成功！欢迎回来！";

    /** The default every earlier version shipped for {@code messages.already-logged}; compared, never shown. */
    static final String SHIPPED_ALREADY_LOGGED = "&e你已经登录了！";

    /** The default every earlier version shipped for {@code messages.not-registered}; compared, never shown. */
    static final String SHIPPED_NOT_REGISTERED = "&c你还没有注册！请先注册。";

    /** The default every earlier version shipped for {@code messages.already-registered}; compared, never shown. */
    static final String SHIPPED_ALREADY_REGISTERED = "&c你已经注册过了！请直接登录。";

    /** The default every earlier version shipped for {@code messages.password-mismatch}; compared, never shown. */
    static final String SHIPPED_PASSWORD_MISMATCH = "&c两次输入的密码不一致！";

    /** The default every earlier version shipped for {@code messages.password-too-short}; compared, never shown. */
    static final String SHIPPED_PASSWORD_TOO_SHORT = "&c密码太短！至少需要 {MIN} 个字符。";

    /** The default every earlier version shipped for {@code messages.password-too-long}; compared, never shown. */
    static final String SHIPPED_PASSWORD_TOO_LONG = "&c密码太长！最多 {MAX} 个字符。";

    /** The default every earlier version shipped for {@code messages.timeout-kick}; compared, never shown. */
    static final String SHIPPED_TIMEOUT_KICK = "&c登录超时！请重新连接。";

    /** The default every earlier version shipped for {@code messages.account-locked}; compared, never shown. */
    static final String SHIPPED_ACCOUNT_LOCKED = "&c登录失败次数过多！请在 {TIME} 秒后重试。";

    /** The default every earlier version shipped for {@code messages.attempts-remaining}; compared, never shown. */
    static final String SHIPPED_ATTEMPTS_REMAINING = "&c密码错误！剩余尝试次数: {COUNT}";

    /** The default every earlier version shipped for {@code messages.gui-password-invalid}; compared, never shown. */
    static final String SHIPPED_GUI_PASSWORD_INVALID = "&c密码必须是 {LENGTH} 位数字！";

    /** The default every earlier version shipped for {@code messages.admin.password-reset}; compared, never shown. */
    static final String SHIPPED_ADMIN_PASSWORD_RESET = "&a已重置玩家 {PLAYER} 的密码为: {PASSWORD}";

    /** The default every earlier version shipped for {@code messages.admin.force-login}; compared, never shown. */
    static final String SHIPPED_ADMIN_FORCE_LOGIN = "&a已强制登录玩家 {PLAYER}";

    /** The default every earlier version shipped for {@code messages.admin.unregister}; compared, never shown. */
    static final String SHIPPED_ADMIN_UNREGISTER = "&a已删除玩家 {PLAYER} 的账号";

    /** The default every earlier version shipped for {@code messages.admin.player-not-found}; compared, never shown. */
    static final String SHIPPED_ADMIN_PLAYER_NOT_FOUND = "&c找不到玩家 {PLAYER}";

    /** The default every earlier version shipped for {@code messages.admin.account-not-found}; compared, never shown. */
    static final String SHIPPED_ADMIN_ACCOUNT_NOT_FOUND = "&c玩家 {PLAYER} 尚未注册";

    public LoginConfig() {
        super(CONFIG_FILE);
    }

    /**
     * {@code configured}, or {@code languageText} when {@code configured} is null, empty or only
     * whitespace.
     *
     * @param configured   the value in {@code config/login.yml}
     * @param languageText the language file's text for the same setting
     * @return the text to show
     */
    static String configuredOr(String configured, String languageText) {
        return configured == null || configured.trim().isEmpty() ? languageText : configured;
    }

    /**
     * The language file's text for {@code key}, in the server's language, read through the plugin
     * this configuration was bound to at load. Before that binding there is no language to read, so
     * the key itself is returned, as the framework renders a missing key.
     *
     * @param key the language-file key
     * @return the text for the key
     */
    private String i18n(String key) {
        UltiToolsPlugin plugin = getUltiToolsPlugin();
        return plugin == null ? key : plugin.i18n(key);
    }

    /**
     * {@code gui-mode.title-login}, or the language file's {@code gui_enter_password} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getGuiLoginTitle() {
        return configuredOr(guiLoginTitle, "&6" + i18n("gui_enter_password"));
    }

    /**
     * {@code gui-mode.title-register}, or the language file's {@code gui_set_password} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getGuiRegisterTitle() {
        return configuredOr(guiRegisterTitle, "&6" + i18n("gui_set_password"));
    }

    /**
     * {@code gui-mode.title-confirm}, or the language file's {@code gui_confirm_password} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getGuiConfirmTitle() {
        return configuredOr(guiConfirmTitle, "&6" + i18n("gui_confirm_password"));
    }

    /**
     * {@code messages.register-prompt}, or the language file's {@code register_prompt} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getRegisterPrompt() {
        return configuredOr(registerPrompt, "&e" + i18n("register_prompt"));
    }

    /**
     * {@code messages.register-prompt-gui}, or the language file's {@code register_prompt_gui} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getRegisterPromptGui() {
        return configuredOr(registerPromptGui, "&e" + i18n("register_prompt_gui"));
    }

    /**
     * {@code messages.login-prompt}, or the language file's {@code login_prompt} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getLoginPrompt() {
        return configuredOr(loginPrompt, "&e" + i18n("login_prompt"));
    }

    /**
     * {@code messages.login-prompt-gui}, or the language file's {@code login_prompt_gui} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getLoginPromptGui() {
        return configuredOr(loginPromptGui, "&e" + i18n("login_prompt_gui"));
    }

    /**
     * {@code messages.register-success}, or the language file's {@code register_success} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getRegisterSuccess() {
        return configuredOr(registerSuccess, "&a" + i18n("register_success"));
    }

    /**
     * {@code messages.login-success}, or the language file's {@code login_success} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getLoginSuccess() {
        return configuredOr(loginSuccess, "&a" + i18n("login_success"));
    }

    /**
     * {@code messages.already-logged}, or the language file's {@code already_logged} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getAlreadyLogged() {
        return configuredOr(alreadyLogged, "&e" + i18n("already_logged"));
    }

    /**
     * {@code messages.not-registered}, or the language file's {@code not_registered} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getNotRegistered() {
        return configuredOr(notRegistered, "&c" + i18n("not_registered"));
    }

    /**
     * {@code messages.already-registered}, or the language file's {@code already_registered} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getAlreadyRegistered() {
        return configuredOr(alreadyRegistered, "&c" + i18n("already_registered"));
    }

    /**
     * {@code messages.password-mismatch}, or the language file's {@code password_mismatch} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getPasswordMismatch() {
        return configuredOr(passwordMismatch, "&c" + i18n("password_mismatch"));
    }

    /**
     * {@code messages.password-too-short}, or the language file's {@code password_too_short} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getPasswordTooShort() {
        return configuredOr(passwordTooShort, "&c" + i18n("password_too_short"));
    }

    /**
     * {@code messages.password-too-long}, or the language file's {@code password_too_long} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getPasswordTooLong() {
        return configuredOr(passwordTooLong, "&c" + i18n("password_too_long"));
    }

    /**
     * {@code messages.timeout-kick}, or the language file's {@code timeout_kick} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getTimeoutKick() {
        return configuredOr(timeoutKick, "&c" + i18n("timeout_kick"));
    }

    /**
     * {@code messages.account-locked}, or the language file's {@code account_locked} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getAccountLocked() {
        return configuredOr(accountLocked, "&c" + i18n("account_locked"));
    }

    /**
     * {@code messages.attempts-remaining}, or the language file's {@code attempts_remaining} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getAttemptsRemaining() {
        return configuredOr(attemptsRemaining, "&c" + i18n("attempts_remaining"));
    }

    /**
     * {@code messages.gui-password-invalid}, or the language file's {@code gui_password_invalid} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getGuiPasswordInvalid() {
        return configuredOr(guiPasswordInvalid, "&c" + i18n("gui_password_invalid"));
    }

    /**
     * {@code messages.admin.password-reset}, or the language file's {@code admin_password_reset} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getAdminPasswordReset() {
        return configuredOr(adminPasswordReset, "&a" + i18n("admin_password_reset"));
    }

    /**
     * {@code messages.admin.force-login}, or the language file's {@code admin_force_login} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getAdminForceLogin() {
        return configuredOr(adminForceLogin, "&a" + i18n("admin_force_login"));
    }

    /**
     * {@code messages.admin.unregister}, or the language file's {@code admin_unregister} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getAdminUnregister() {
        return configuredOr(adminUnregister, "&a" + i18n("admin_unregister"));
    }

    /**
     * {@code messages.admin.player-not-found}, or the language file's {@code admin_player_not_found} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getAdminPlayerNotFound() {
        return configuredOr(adminPlayerNotFound, "&c" + i18n("admin_player_not_found"));
    }

    /**
     * {@code messages.admin.account-not-found}, or the language file's {@code admin_account_not_found} text, in the colour its shipped default opened with, when left blank.
     *
     * @return the text to show
     */
    public String getAdminAccountNotFound() {
        return configuredOr(adminAccountNotFound, "&c" + i18n("admin_account_not_found"));
    }

    /**
     * Rewrites every GUI title and message that still holds the default an earlier version shipped
     * to blank, so the language file's text takes over; any other value is the operator's and is
     * kept. Idempotent: a blank value matches no shipped default. The caller saves the file when
     * this returns true (maintainer ruling 2026-09-24 (d)).
     *
     * @return whether any value was rewritten
     */
    public boolean migrateLegacyDefaults() {
        boolean changed = false;
        if (SHIPPED_GUI_LOGIN_TITLE.equals(guiLoginTitle)) {
            guiLoginTitle = "";
            changed = true;
        }
        if (SHIPPED_GUI_REGISTER_TITLE.equals(guiRegisterTitle)) {
            guiRegisterTitle = "";
            changed = true;
        }
        if (SHIPPED_GUI_CONFIRM_TITLE.equals(guiConfirmTitle)) {
            guiConfirmTitle = "";
            changed = true;
        }
        if (SHIPPED_REGISTER_PROMPT.equals(registerPrompt)) {
            registerPrompt = "";
            changed = true;
        }
        if (SHIPPED_REGISTER_PROMPT_GUI.equals(registerPromptGui)) {
            registerPromptGui = "";
            changed = true;
        }
        if (SHIPPED_LOGIN_PROMPT.equals(loginPrompt)) {
            loginPrompt = "";
            changed = true;
        }
        if (SHIPPED_LOGIN_PROMPT_GUI.equals(loginPromptGui)) {
            loginPromptGui = "";
            changed = true;
        }
        if (SHIPPED_REGISTER_SUCCESS.equals(registerSuccess)) {
            registerSuccess = "";
            changed = true;
        }
        if (SHIPPED_LOGIN_SUCCESS.equals(loginSuccess)) {
            loginSuccess = "";
            changed = true;
        }
        if (SHIPPED_ALREADY_LOGGED.equals(alreadyLogged)) {
            alreadyLogged = "";
            changed = true;
        }
        if (SHIPPED_NOT_REGISTERED.equals(notRegistered)) {
            notRegistered = "";
            changed = true;
        }
        if (SHIPPED_ALREADY_REGISTERED.equals(alreadyRegistered)) {
            alreadyRegistered = "";
            changed = true;
        }
        if (SHIPPED_PASSWORD_MISMATCH.equals(passwordMismatch)) {
            passwordMismatch = "";
            changed = true;
        }
        if (SHIPPED_PASSWORD_TOO_SHORT.equals(passwordTooShort)) {
            passwordTooShort = "";
            changed = true;
        }
        if (SHIPPED_PASSWORD_TOO_LONG.equals(passwordTooLong)) {
            passwordTooLong = "";
            changed = true;
        }
        if (SHIPPED_TIMEOUT_KICK.equals(timeoutKick)) {
            timeoutKick = "";
            changed = true;
        }
        if (SHIPPED_ACCOUNT_LOCKED.equals(accountLocked)) {
            accountLocked = "";
            changed = true;
        }
        if (SHIPPED_ATTEMPTS_REMAINING.equals(attemptsRemaining)) {
            attemptsRemaining = "";
            changed = true;
        }
        if (SHIPPED_GUI_PASSWORD_INVALID.equals(guiPasswordInvalid)) {
            guiPasswordInvalid = "";
            changed = true;
        }
        if (SHIPPED_ADMIN_PASSWORD_RESET.equals(adminPasswordReset)) {
            adminPasswordReset = "";
            changed = true;
        }
        if (SHIPPED_ADMIN_FORCE_LOGIN.equals(adminForceLogin)) {
            adminForceLogin = "";
            changed = true;
        }
        if (SHIPPED_ADMIN_UNREGISTER.equals(adminUnregister)) {
            adminUnregister = "";
            changed = true;
        }
        if (SHIPPED_ADMIN_PLAYER_NOT_FOUND.equals(adminPlayerNotFound)) {
            adminPlayerNotFound = "";
            changed = true;
        }
        if (SHIPPED_ADMIN_ACCOUNT_NOT_FOUND.equals(adminAccountNotFound)) {
            adminAccountNotFound = "";
            changed = true;
        }
        return changed;
    }
    
    /**
     * Get the effective password min length based on mode.
     * GUI mode uses guiPasswordLength, command mode uses minPasswordLength.
     */
    public int getEffectiveMinPasswordLength() {
        return guiModeEnabled ? guiPasswordLength : minPasswordLength;
    }
    
    /**
     * Get the effective password max length based on mode.
     * GUI mode uses guiPasswordLength, command mode uses maxPasswordLength.
     */
    public int getEffectiveMaxPasswordLength() {
        return guiModeEnabled ? guiPasswordLength : maxPasswordLength;
    }
}
