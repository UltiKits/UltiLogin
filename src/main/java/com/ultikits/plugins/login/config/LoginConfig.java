package com.ultikits.plugins.login.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
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

    @NotEmpty
    @ConfigEntry(path = "gui-mode.title-login", comment = "GUI登录界面标题")
    private String guiLoginTitle = SHIPPED_GUI_LOGIN_TITLE;

    @NotEmpty
    @ConfigEntry(path = "gui-mode.title-register", comment = "GUI注册界面标题")
    private String guiRegisterTitle = SHIPPED_GUI_REGISTER_TITLE;

    @NotEmpty
    @ConfigEntry(path = "gui-mode.title-confirm", comment = "GUI确认密码界面标题")
    private String guiConfirmTitle = SHIPPED_GUI_CONFIRM_TITLE;
    
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

    @NotEmpty
    @ConfigEntry(path = "messages.register-prompt", comment = "注册提示（命令模式）")
    private String registerPrompt = SHIPPED_REGISTER_PROMPT;

    @NotEmpty
    @ConfigEntry(path = "messages.register-prompt-gui", comment = "注册提示（GUI模式）")
    private String registerPromptGui = SHIPPED_REGISTER_PROMPT_GUI;

    @NotEmpty
    @ConfigEntry(path = "messages.login-prompt", comment = "登录提示（命令模式）")
    private String loginPrompt = SHIPPED_LOGIN_PROMPT;

    @NotEmpty
    @ConfigEntry(path = "messages.login-prompt-gui", comment = "登录提示（GUI模式）")
    private String loginPromptGui = SHIPPED_LOGIN_PROMPT_GUI;

    @NotEmpty
    @ConfigEntry(path = "messages.register-success", comment = "注册成功")
    private String registerSuccess = SHIPPED_REGISTER_SUCCESS;

    @NotEmpty
    @ConfigEntry(path = "messages.login-success", comment = "登录成功")
    private String loginSuccess = SHIPPED_LOGIN_SUCCESS;

    @NotEmpty
    @ConfigEntry(path = "messages.already-logged", comment = "已经登录")
    private String alreadyLogged = SHIPPED_ALREADY_LOGGED;

    @NotEmpty
    @ConfigEntry(path = "messages.not-registered", comment = "未注册")
    private String notRegistered = SHIPPED_NOT_REGISTERED;

    @NotEmpty
    @ConfigEntry(path = "messages.already-registered", comment = "已注册")
    private String alreadyRegistered = SHIPPED_ALREADY_REGISTERED;

    @NotEmpty
    @ConfigEntry(path = "messages.password-mismatch", comment = "密码不匹配")
    private String passwordMismatch = SHIPPED_PASSWORD_MISMATCH;

    @NotEmpty
    @ConfigEntry(path = "messages.password-too-short", comment = "密码太短")
    private String passwordTooShort = SHIPPED_PASSWORD_TOO_SHORT;

    @NotEmpty
    @ConfigEntry(path = "messages.password-too-long", comment = "密码太长")
    private String passwordTooLong = SHIPPED_PASSWORD_TOO_LONG;

    @NotEmpty
    @ConfigEntry(path = "messages.timeout-kick", comment = "超时踢出")
    private String timeoutKick = SHIPPED_TIMEOUT_KICK;

    @NotEmpty
    @ConfigEntry(path = "messages.account-locked", comment = "账户被锁定")
    private String accountLocked = SHIPPED_ACCOUNT_LOCKED;

    @NotEmpty
    @ConfigEntry(path = "messages.attempts-remaining", comment = "剩余尝试次数")
    private String attemptsRemaining = SHIPPED_ATTEMPTS_REMAINING;

    @NotEmpty
    @ConfigEntry(path = "messages.gui-password-invalid", comment = "GUI密码无效")
    private String guiPasswordInvalid = SHIPPED_GUI_PASSWORD_INVALID;

    // ==================== 管理员消息 ====================

    @NotEmpty
    @ConfigEntry(path = "messages.admin.password-reset", comment = "管理员重置密码成功")
    private String adminPasswordReset = SHIPPED_ADMIN_PASSWORD_RESET;

    @NotEmpty
    @ConfigEntry(path = "messages.admin.force-login", comment = "管理员强制登录")
    private String adminForceLogin = SHIPPED_ADMIN_FORCE_LOGIN;

    @NotEmpty
    @ConfigEntry(path = "messages.admin.unregister", comment = "管理员删除账号")
    private String adminUnregister = SHIPPED_ADMIN_UNREGISTER;

    @NotEmpty
    @ConfigEntry(path = "messages.admin.player-not-found", comment = "玩家不存在")
    private String adminPlayerNotFound = SHIPPED_ADMIN_PLAYER_NOT_FOUND;

    @NotEmpty
    @ConfigEntry(path = "messages.admin.account-not-found", comment = "账号不存在")
    private String adminAccountNotFound = SHIPPED_ADMIN_ACCOUNT_NOT_FOUND;
    
    // ==================== Text defaults an earlier version shipped ====================
    // Every GUI title and message below shipped one fixed Chinese default in every earlier version.
    // Each is still the setting's Java default, which the framework writes for a missing key, and one
    // of the values materializeText() recognises as built-in text in an operator's file; that method
    // then writes the setting's colour code plus the language file's text in the server's language
    // (maintainer decision 2026-09-25, UltiKits/UltiLogin#20). The colour stays the module's because a
    // language file extracted by an earlier version holds the same keys with the same words and no
    // colour codes, and an upgraded install reads that file first.

    /** The default every earlier version shipped for {@code gui-mode.title-login}; the Java default, compared byte for byte. */
    static final String SHIPPED_GUI_LOGIN_TITLE = "&6请输入密码";

    /** The default every earlier version shipped for {@code gui-mode.title-register}; the Java default, compared byte for byte. */
    static final String SHIPPED_GUI_REGISTER_TITLE = "&6请设置密码";

    /** The default every earlier version shipped for {@code gui-mode.title-confirm}; the Java default, compared byte for byte. */
    static final String SHIPPED_GUI_CONFIRM_TITLE = "&6请再次输入密码";

    /** The default every earlier version shipped for {@code messages.register-prompt}; the Java default, compared byte for byte. */
    static final String SHIPPED_REGISTER_PROMPT = "&e请使用 /register <密码> <确认密码> 注册账号";

    /** The default every earlier version shipped for {@code messages.register-prompt-gui}; the Java default, compared byte for byte. */
    static final String SHIPPED_REGISTER_PROMPT_GUI = "&e请在弹出的界面中设置密码";

    /** The default every earlier version shipped for {@code messages.login-prompt}; the Java default, compared byte for byte. */
    static final String SHIPPED_LOGIN_PROMPT = "&e请使用 /login <密码> 登录";

    /** The default every earlier version shipped for {@code messages.login-prompt-gui}; the Java default, compared byte for byte. */
    static final String SHIPPED_LOGIN_PROMPT_GUI = "&e请在弹出的界面中输入密码";

    /** The default every earlier version shipped for {@code messages.register-success}; the Java default, compared byte for byte. */
    static final String SHIPPED_REGISTER_SUCCESS = "&a注册成功！欢迎加入服务器！";

    /** The default every earlier version shipped for {@code messages.login-success}; the Java default, compared byte for byte. */
    static final String SHIPPED_LOGIN_SUCCESS = "&a登录成功！欢迎回来！";

    /** The default every earlier version shipped for {@code messages.already-logged}; the Java default, compared byte for byte. */
    static final String SHIPPED_ALREADY_LOGGED = "&e你已经登录了！";

    /** The default every earlier version shipped for {@code messages.not-registered}; the Java default, compared byte for byte. */
    static final String SHIPPED_NOT_REGISTERED = "&c你还没有注册！请先注册。";

    /** The default every earlier version shipped for {@code messages.already-registered}; the Java default, compared byte for byte. */
    static final String SHIPPED_ALREADY_REGISTERED = "&c你已经注册过了！请直接登录。";

    /** The default every earlier version shipped for {@code messages.password-mismatch}; the Java default, compared byte for byte. */
    static final String SHIPPED_PASSWORD_MISMATCH = "&c两次输入的密码不一致！";

    /** The default every earlier version shipped for {@code messages.password-too-short}; the Java default, compared byte for byte. */
    static final String SHIPPED_PASSWORD_TOO_SHORT = "&c密码太短！至少需要 {MIN} 个字符。";

    /** The default every earlier version shipped for {@code messages.password-too-long}; the Java default, compared byte for byte. */
    static final String SHIPPED_PASSWORD_TOO_LONG = "&c密码太长！最多 {MAX} 个字符。";

    /** The default every earlier version shipped for {@code messages.timeout-kick}; the Java default, compared byte for byte. */
    static final String SHIPPED_TIMEOUT_KICK = "&c登录超时！请重新连接。";

    /** The default every earlier version shipped for {@code messages.account-locked}; the Java default, compared byte for byte. */
    static final String SHIPPED_ACCOUNT_LOCKED = "&c登录失败次数过多！请在 {TIME} 秒后重试。";

    /** The default every earlier version shipped for {@code messages.attempts-remaining}; the Java default, compared byte for byte. */
    static final String SHIPPED_ATTEMPTS_REMAINING = "&c密码错误！剩余尝试次数: {COUNT}";

    /** The default every earlier version shipped for {@code messages.gui-password-invalid}; the Java default, compared byte for byte. */
    static final String SHIPPED_GUI_PASSWORD_INVALID = "&c密码必须是 {LENGTH} 位数字！";

    /** The default every earlier version shipped for {@code messages.admin.password-reset}; the Java default, compared byte for byte. */
    static final String SHIPPED_ADMIN_PASSWORD_RESET = "&a已重置玩家 {PLAYER} 的密码为: {PASSWORD}";

    /** The default every earlier version shipped for {@code messages.admin.force-login}; the Java default, compared byte for byte. */
    static final String SHIPPED_ADMIN_FORCE_LOGIN = "&a已强制登录玩家 {PLAYER}";

    /** The default every earlier version shipped for {@code messages.admin.unregister}; the Java default, compared byte for byte. */
    static final String SHIPPED_ADMIN_UNREGISTER = "&a已删除玩家 {PLAYER} 的账号";

    /** The default every earlier version shipped for {@code messages.admin.player-not-found}; the Java default, compared byte for byte. */
    static final String SHIPPED_ADMIN_PLAYER_NOT_FOUND = "&c找不到玩家 {PLAYER}";

    /** The default every earlier version shipped for {@code messages.admin.account-not-found}; the Java default, compared byte for byte. */
    static final String SHIPPED_ADMIN_ACCOUNT_NOT_FOUND = "&c玩家 {PLAYER} 尚未注册";

    public LoginConfig() {
        super(CONFIG_FILE);
    }

    /**
     * Writes every GUI title and message in the server's language (maintainer decision 2026-09-25,
     * UltiKits/UltiLogin#20): each setting whose value is still built-in text -- the default an earlier
     * version shipped, or its colour code plus this jar's text for it in any language -- and differs
     * from the current text is replaced with its colour code plus {@code text}'s current text, when that
     * text fits the setting's own limits. Any other value is the operator's and is kept. Idempotent. Must run after the module's language is loaded
     * ({@code registerSelf()} and {@code onReload()}), never from a change listener; the caller saves
     * the file when this returns {@code true}.
     *
     * @param text the module's {@code i18n}: catalogue key to text in the server's language
     * @return whether any value was rewritten
     */
    public boolean materializeText(Function<String, String> text) {
        Map<String, Map<String, String>> jar = ConfigTextDefaults.jarCatalogues(LoginConfig.class);
        boolean[] changed = {false};
        guiLoginTitle = follow("guiLoginTitle", guiLoginTitle, text, jar, "&6", "gui_enter_password", SHIPPED_GUI_LOGIN_TITLE, changed);
        guiRegisterTitle = follow("guiRegisterTitle", guiRegisterTitle, text, jar, "&6", "gui_set_password", SHIPPED_GUI_REGISTER_TITLE, changed);
        guiConfirmTitle = follow("guiConfirmTitle", guiConfirmTitle, text, jar, "&6", "gui_confirm_password", SHIPPED_GUI_CONFIRM_TITLE, changed);
        registerPrompt = follow("registerPrompt", registerPrompt, text, jar, "&e", "register_prompt", SHIPPED_REGISTER_PROMPT, changed);
        registerPromptGui = follow("registerPromptGui", registerPromptGui, text, jar, "&e", "register_prompt_gui", SHIPPED_REGISTER_PROMPT_GUI, changed);
        loginPrompt = follow("loginPrompt", loginPrompt, text, jar, "&e", "login_prompt", SHIPPED_LOGIN_PROMPT, changed);
        loginPromptGui = follow("loginPromptGui", loginPromptGui, text, jar, "&e", "login_prompt_gui", SHIPPED_LOGIN_PROMPT_GUI, changed);
        registerSuccess = follow("registerSuccess", registerSuccess, text, jar, "&a", "register_success", SHIPPED_REGISTER_SUCCESS, changed);
        loginSuccess = follow("loginSuccess", loginSuccess, text, jar, "&a", "login_success", SHIPPED_LOGIN_SUCCESS, changed);
        alreadyLogged = follow("alreadyLogged", alreadyLogged, text, jar, "&e", "already_logged", SHIPPED_ALREADY_LOGGED, changed);
        notRegistered = follow("notRegistered", notRegistered, text, jar, "&c", "not_registered", SHIPPED_NOT_REGISTERED, changed);
        alreadyRegistered = follow("alreadyRegistered", alreadyRegistered, text, jar, "&c", "already_registered", SHIPPED_ALREADY_REGISTERED, changed);
        passwordMismatch = follow("passwordMismatch", passwordMismatch, text, jar, "&c", "password_mismatch", SHIPPED_PASSWORD_MISMATCH, changed);
        passwordTooShort = follow("passwordTooShort", passwordTooShort, text, jar, "&c", "password_too_short", SHIPPED_PASSWORD_TOO_SHORT, changed);
        passwordTooLong = follow("passwordTooLong", passwordTooLong, text, jar, "&c", "password_too_long", SHIPPED_PASSWORD_TOO_LONG, changed);
        timeoutKick = follow("timeoutKick", timeoutKick, text, jar, "&c", "timeout_kick", SHIPPED_TIMEOUT_KICK, changed);
        accountLocked = follow("accountLocked", accountLocked, text, jar, "&c", "account_locked", SHIPPED_ACCOUNT_LOCKED, changed);
        attemptsRemaining = follow("attemptsRemaining", attemptsRemaining, text, jar, "&c", "attempts_remaining", SHIPPED_ATTEMPTS_REMAINING, changed);
        guiPasswordInvalid = follow("guiPasswordInvalid", guiPasswordInvalid, text, jar, "&c", "gui_password_invalid", SHIPPED_GUI_PASSWORD_INVALID, changed);
        adminPasswordReset = follow("adminPasswordReset", adminPasswordReset, text, jar, "&a", "admin_password_reset", SHIPPED_ADMIN_PASSWORD_RESET, changed);
        adminForceLogin = follow("adminForceLogin", adminForceLogin, text, jar, "&a", "admin_force_login", SHIPPED_ADMIN_FORCE_LOGIN, changed);
        adminUnregister = follow("adminUnregister", adminUnregister, text, jar, "&a", "admin_unregister", SHIPPED_ADMIN_UNREGISTER, changed);
        adminPlayerNotFound = follow("adminPlayerNotFound", adminPlayerNotFound, text, jar, "&c", "admin_player_not_found", SHIPPED_ADMIN_PLAYER_NOT_FOUND, changed);
        adminAccountNotFound = follow("adminAccountNotFound", adminAccountNotFound, text, jar, "&c", "admin_account_not_found", SHIPPED_ADMIN_ACCOUNT_NOT_FOUND, changed);
        return changed[0];
    }

    /**
     * {@code value}, or {@code prefix} plus {@code text}'s current text for {@code key} when
     * {@code value} is still built-in text other than that and the new text fits {@code field}'s
     * constraints; sets {@code changed[0]} when it replaces.
     */
    private static String follow(String field, String value, Function<String, String> text,
                                 Map<String, Map<String, String>> jar, String prefix, String key, String shipped,
                                 boolean[] changed) {
        String result = ConfigTextDefaults.materialize(LoginConfig.class, field, value,
                ConfigTextDefaults.currentText(text, prefix, key), ConfigTextDefaults.tracked(jar, prefix, key, shipped));
        if (!Objects.equals(result, value)) {
            changed[0] = true;
        }
        return result;
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
