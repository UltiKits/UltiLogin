package com.ultikits.plugins.login.config;

import java.util.Arrays;
import java.util.List;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.Range;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for email binding and password recovery.
 * <p>
 * 邮箱绑定和密码找回配置。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Getter
@Setter
@ConfigEntity("config/email.yml")
public class EmailConfig extends AbstractConfigEntity {

    // ==================== 验证码设置 ====================

    @Range(min = 4, max = 8)
    @ConfigEntry(path = "verification.code-length", comment = "{login_config_comment_email_verification_code_length}")
    private int codeLength = 6;

    @Range(min = 60, max = 1800)
    @ConfigEntry(path = "verification.code-expiry-seconds", comment = "{login_config_comment_email_verification_code_expiry_seconds}")
    private int codeExpirySeconds = 300;

    @Range(min = 1, max = 10)
    @ConfigEntry(path = "verification.max-attempts", comment = "{login_config_comment_email_verification_max_attempts}")
    private int maxAttempts = 3;

    @Range(min = 30, max = 600)
    @ConfigEntry(path = "verification.cooldown-seconds", comment = "{login_config_comment_email_verification_cooldown_seconds}")
    private int cooldownSeconds = 60;

    // ==================== 邮箱域名黑名单 ====================

    @ConfigEntry(path = "domain-blacklist", comment = "{login_config_comment_email_domain_blacklist}")
    private List<String> domainBlacklist = Arrays.asList(
            "10minutemail.com",
            "tempmail.com",
            "guerrillamail.com",
            "mailinator.com",
            "throwaway.email"
    );

    // ==================== 账号限制 ====================

    @Range(min = 1, max = 10)
    @ConfigEntry(path = "max-accounts-per-email", comment = "{login_config_comment_email_max_accounts_per_email}")
    private int maxAccountsPerEmail = 1;

    // ==================== 绑定奖励 ====================

    @ConfigEntry(path = "reward.enabled", comment = "{login_config_comment_email_reward_enabled}")
    private boolean rewardEnabled = false;

    @ConfigEntry(path = "reward.commands", comment = "{login_config_comment_email_reward_commands}")
    private List<String> rewardCommands = Arrays.asList("givemoney %player% 500");

    public EmailConfig() {
        super("config/email.yml");
    }
}
