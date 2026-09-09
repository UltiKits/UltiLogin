# UltiLogin — UAT Checklist

This document is the executable companion to `FEATURES.md`: one row per feature stating the
steps to exercise it and the observable truth that proves it works. It is an internal reference
for real-machine verification, not user-facing documentation.

> Batches are dispatched at 60 rows or fewer, and a batch never spans two repositories. There are
> exactly two legitimate exits to `human-uat-pending`: a row needing the pixel layer while the
> real-client harness is not ready, and a row needing personal credentials. Every other row must
> reach `pass`, `fail`, or `blocked`.

## Conventions

- **Columns:** `ID`, `Preconditions`, `Steps`, `Expected`, `Layer`, `Covers`.
- **ID:** cites its `FEATURES.md` ID verbatim. A negative case suffixes the checklist ID only,
  as `.neg-<slug>` — a negative case still tests the same feature, so the base ID is unchanged.
- **Layer**, copied verbatim from Laojun's own `ultitools-real-client-uat` skill so no
  translation step exists at dispatch time: `protocol`, `java-client`, `os-input`, `pixel`,
  `server`, `human`.
- **UltiCloud-panel-session rows (D-27b):** a row whose Steps can only be exercised through the
  maintainer's own authenticated UltiCloud panel session carries the fixed Preconditions phrase
  `maintainer-authenticated UltiCloud panel session (personal credentials)` (appended to, not
  replacing, the row's own preconditions) and Layer `human`. This module's `/panel` command is
  such a row: it hands the player a real UltiCloud magic-link that only completes against the
  maintainer's own account.
- **SMTP-credential rows (this module's own addition to the D-27b pattern):** a row whose Steps
  can only be exercised by actually sending an email through the maintainer's own configured SMTP
  account (`email.smtp.username`/`email.smtp.password` in the framework's `config.yml`, personal
  mail credentials) and reading the resulting code from a real inbox carries the fixed
  Preconditions phrase `maintainer-configured SMTP account (personal credentials)` and Layer
  `human`. Every row under `## Email Binding` and `## Password Recovery` that causes a real
  verification email to be sent is such a row, following the same "mark it human, do not
  fabricate an automated assertion" principle the framework's checklist established for UltiCloud
  panel sessions. A negative row that only needs a *pending* request to already exist (not the
  real received code) still carries this phrase and Layer, because creating that pending state
  itself required a real send — see `ultilogin.email.verify.neg-wrong-code` for the one row where
  this distinction matters.
- **Expected** must name an observable truth — an exact chat line, a log line, a database row,
  an inventory slot — and never the words "it works".
- **Covers** back-references a Phase 9 GUI-excluded class name; left blank when no such class
  applies.
- A row whose Preconditions name a prior row must appear after that row in file order — asserted
  mechanically: for every row, every checklist ID cited in its Preconditions cell must have a
  strictly smaller line number in this file than the row citing it (sweep class 8, D-27a).
- **Config-per-file rule (D-06):** one checklist row per `@ConfigEntity`-annotated class, never
  one row per key. The row's ID is suffixed `-yml` (`ultilogin.config.login-yml`,
  `ultilogin.config.email-yml`), aggregating every per-key `ultilogin.config.<file-stem>.*` row
  for that file rather than citing a single one of them.
- **Two message sources, not one:** most player-facing text in `## Register`, `## Login`,
  `## Change Password`, and three of `## Admin`'s five success confirmations is driven by
  `LoginConfig`'s own `messages.*` fields — customizable per-server, but shipping with their own
  Chinese defaults **independent of the `language` setting**. Every Expected quoting one of these
  values quotes the shipped `LoginConfig` default directly and does NOT carry a `language: en`
  precondition — `language: en` would change nothing here (a distinct fact from the framework's
  own `config.yml`, where every localized string genuinely is `language`-driven). Rows under
  `## Email Binding`, `## Password Recovery`, and `## Panel` DO route through `plugin.i18n(...)`
  and DO carry `language: en` as a precondition, quoting `lang/en.json`'s text. A third group —
  every `handleHelp` override, `LoginAdminCommand#showInfo`, and the handful of inline messages
  named in `## Register`/`## Login`/`## Admin` below — is hardcoded Simplified Chinese with
  **neither** mechanism behind it; those Expected cells quote the actual Chinese text and note
  `UltiKits/UltiLogin#20`, the filed defect.
- This module ships `login-timeout: 60`, `security.max-login-attempts: 5`,
  `security.lockout-duration: 900`, `max-register-per-ip: 3`, `password.min-length: 6`,
  `password.max-length: 32`, `gui-mode.enabled: false` (command-mode password flow) as its
  defaults; every row below assumes these unless its own Preconditions say otherwise.

## Register

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultilogin.register.create | A fresh player UUID that has never registered on this server; `gui-mode.enabled: false` (shipped default) | Run `/register abcdefgh abcdefgh`, then immediately run `/login abcdefgh` | `messages.register-success`'s shipped (Simplified Chinese, not reproduced per D-02 -- see `LoginConfig.java` `registerSuccess`) text appears; the immediate `/login` afterward shows `messages.already-logged`'s shipped text, proving the register call auto-logged the account in | server | |
| ultilogin.register.create.neg-already-registered | The same player as `ultilogin.register.create`, already registered | Run `/register newpass1 newpass1` | `messages.already-registered`'s shipped (Simplified Chinese, not reproduced per D-02) text | server | |
| ultilogin.register.create.neg-ip-limit | `max-register-per-ip: 1` (NOT the shipped default 3); one account already registered from this client's IP | Register a second, fresh player from the same client/IP | A hardcoded Simplified Chinese refusal line (`LoginService.java:367`, not reproduced per D-02), red — unaffected by `language: en`; no matching lang key exists at all (`UltiKits/UltiLogin#20`) | server | |
| ultilogin.register.create.neg-mismatch | A fresh, unregistered player | Run `/register password1 password2` (two different values) | `messages.password-mismatch`'s shipped (Simplified Chinese, not reproduced per D-02) text | server | |
| ultilogin.register.create.neg-too-short | A fresh, unregistered player; `gui-mode.enabled: false` | Run `/register ab ab` (2 characters, below `password.min-length: 6`) | `messages.password-too-short`'s shipped (Simplified Chinese, not reproduced per D-02) text, with `{MIN}` substituted to `6` | server | |
| ultilogin.register.help | none | Run bare `/register` and, separately, literal `/register help` | Both print the identical hardcoded Simplified Chinese two-line block (`RegisterCommand.java:75,78-79`, not reproduced per D-02), unaffected by `language: en`; `lang/en.json`'s `help_register` key is declared but never read (`UltiKits/UltiLogin#20`) | server | |

## Login

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultilogin.login.authenticate | `session-enabled: false` (isolates this row from session auto-login); a registered player, currently not logged in, whose password is known | Run `/login <the correct password>` | `messages.login-success`'s shipped (Simplified Chinese, not reproduced per D-02) text | server | |
| ultilogin.login.authenticate.neg-already-logged | A currently logged-in player | Run `/login anything` | `messages.already-logged`'s shipped (Simplified Chinese, not reproduced per D-02) text | server | |
| ultilogin.login.authenticate.neg-not-registered | A player who has never registered | Run `/login anything` | `messages.not-registered`'s shipped (Simplified Chinese, not reproduced per D-02) text | server | |
| ultilogin.login.authenticate.neg-wrong-password | A registered, not-logged-in player, with zero prior failed attempts this lockout window; `security.max-login-attempts: 5` (shipped default) | Run `/login wrongpassword` | `messages.attempts-remaining`'s shipped (Simplified Chinese, not reproduced per D-02) text, with `{COUNT}` substituted to `4` | server | |
| ultilogin.login.authenticate.neg-lockout | Same player/IP as `ultilogin.login.authenticate.neg-wrong-password`, immediately after that row's one wrong attempt; `security.max-login-attempts: 5` (shipped default) — run 3 MORE wrong `/login` attempts first, for 4 total, before this row's own 5th | Run `/login wrongpassword` a 5th consecutive time | `messages.account-locked`'s shipped (Simplified Chinese, not reproduced per D-02) text, with `{TIME}` substituted to `900`; a subsequent `/login <the correct password>` attempt during the lockout window shows the SAME account-locked message rather than succeeding | server | |
| ultilogin.login.help | none | Run bare `/login` and, separately, literal `/login help` | Both print the identical hardcoded Simplified Chinese line (`LoginCommand.java:67`, not reproduced per D-02), unaffected by `language: en`; `lang/en.json`'s `help_login` key is declared but never read (`UltiKits/UltiLogin#20`) | server | |

## Change Password

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultilogin.changepassword.update | A logged-in registered player with a known current password | Run `/changepassword <old> newpass1 newpass1`, then immediately attempt any blocked action (e.g. move a block) | A hardcoded Simplified Chinese success line (`ChangePasswordCommand.java:62`, not reproduced per D-02), green; the player is IMMEDIATELY forced back to the unauthenticated state and re-prompted (F-L1: `invalidateSession` revokes the CALLER's own session too, since the caller is online) — the following move attempt is cancelled exactly as `ultilogin.protection.movement-block` describes, and a subsequent `/login newpass1` (the NEW password) succeeds | server | |
| ultilogin.changepassword.update.neg-mismatch | A logged-in player | Run `/changepassword <old> newpass1 newpass2` (two different new values) | A hardcoded Simplified Chinese mismatch line (`ChangePasswordCommand.java:56`, not reproduced per D-02), red; the player remains logged in (only a successful change revokes) | server | |
| ultilogin.changepassword.update.neg-not-logged-in | A player who is not currently logged in | Run `/changepassword a b b` | A hardcoded Simplified Chinese line (`ChangePasswordCommand.java:43`, not reproduced per D-02), red — a DIFFERENT literal than `EmailBindCommand`'s i18n-routed `please_login_first` ("Please login first!") for the same underlying condition, despite `lang/en.json` declaring that exact key | server | |
| ultilogin.changepassword.update.neg-wrong-old | A logged-in player | Run `/changepassword wrongold newpass1 newpass1` | A hardcoded Simplified Chinese line (`ChangePasswordCommand.java:64`, not reproduced per D-02), red; the player remains logged in | server | |
| ultilogin.changepassword.help | none | Run bare `/changepassword` and, separately, literal `/changepassword help` | Both print the identical hardcoded Simplified Chinese two-line block (`ChangePasswordCommand.java:76,78-80`, not reproduced per D-02), unaffected by `language: en`; `lang/en.json`'s `help_change_password` key is declared but never read (`UltiKits/UltiLogin#20`) | server | |

## Email Binding

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultilogin.email.bind | `language: en`; `email.enable: true` and the framework's SMTP block correctly configured; maintainer-configured SMTP account (personal credentials); a logged-in player with no verified, bound email; a real, maintainer-readable email address | Run `/regs <a real email address>` | `email_bind_prompt`: "A verification code has been sent to {EMAIL}. Use /regs <code> to complete binding." (yellow, `{EMAIL}` substituted) | human | |
| ultilogin.email.bind.neg-domain-blocked | `language: en`; `email.enable: true` with a valid (not necessarily reachable) SMTP configuration present — `requestEmailBind` refuses with `email_not_enabled` before checking the address at all if the framework's email service is absent/disabled, and this row does not itself trigger a send; logged in; no verified email bound yet | Run `/regs test@mailinator.com` (a domain on the shipped `domain-blacklist`) | `email_domain_blocked`: "This email domain is not allowed! Please use a mainstream email provider." (red) | server | |
| ultilogin.email.bind.neg-invalid-format | `language: en`; `email.enable: true` with a valid (not necessarily reachable) SMTP configuration present, for the same reason as `ultilogin.email.bind.neg-domain-blocked` above; logged in | Run `/regs abc@` (contains `@`, so it takes the bind branch rather than the code-verify branch, but fails the email regex) | `email_invalid_format`: "Invalid email format!" (red) | server | |
| ultilogin.email.bind.neg-not-logged-in | `language: en`; not logged in | Run `/regs test@example.com` | `please_login_first`: "Please login first!" (red) | server | |
| ultilogin.email.verify | `language: en`; the pending bind from `ultilogin.email.bind`; maintainer-configured SMTP account (personal credentials) able to read the real received code | Run `/regs <the real received code>` | `email_bind_success`: "Email bound successfully!" (green) | human | |
| ultilogin.email.verify.neg-no-pending | `language: en`; logged in; no pending bind or recovery request for this player | Run `/regs 123456` | `email_no_pending`: "No pending verification request found." (red) | server | |
| ultilogin.email.verify.neg-wrong-code | `language: en`; the pending bind from `ultilogin.email.bind` still open (a real send already happened to create it, though this row does not need to read the received code itself — only a WRONG one); `verification.max-attempts: 3` (shipped default), zero prior wrong attempts on this pending request | Run `/regs 000000` (not the real code) | `email_code_invalid` with `{COUNT}` substituted: "Invalid verification code! Remaining attempts: 2" (red) | human | |
| ultilogin.email.bind.neg-already-bound | `language: en`; the same player as `ultilogin.email.verify` (already has a verified, bound email from that row) | Run `/regs another@example.com` | `email_already_bound` with `{EMAIL}` substituted to the ALREADY-bound address: "You have already bound an email: {EMAIL}" (yellow) | server | |
| ultilogin.email.help | `language: en` | Run bare `/regs` and, separately, literal `/regs help` | Both print `help_email_bind` ("/regs <email> - Bind email address") then `help_email_verify` ("/regs <code> - Verify email") — both via `plugin.i18n(...)`, respecting `language` | server | |

## Password Recovery

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultilogin.recover.request | `language: en`; not logged in; the registered account from `ultilogin.email.verify` (has a verified, bound email); maintainer-configured SMTP account (personal credentials) | Run `/recover` | `recover_email_sent`: "A verification code has been sent to your bound email. Use /recover <code> <new_password> <confirm> to reset." (yellow) | human | |
| ultilogin.recover.request.neg-already-logged | `language: en`; logged in | Run `/recover` | `recover_already_logged`: "You are already logged in. Use /changepassword to change your password." (yellow) | server | |
| ultilogin.recover.request.neg-no-email | `language: en`; `email.enable: true` with a valid (not necessarily reachable) SMTP configuration present — `EmailVerificationService#requestPasswordRecovery` refuses with `email_not_enabled` before checking registration or email-binding state at all; registered, not logged in, no verified bound email | Run `/recover` | `recover_no_email`: "No email bound to your account. Please contact an admin to reset your password." (red) | server | |
| ultilogin.recover.request.neg-not-registered | `language: en`; `email.enable: true` with a valid (not necessarily reachable) SMTP configuration present, for the same reason as `ultilogin.recover.request.neg-no-email` above; a player who has never registered | Run `/recover` | `recover_not_registered`: "You are not registered. Cannot recover password." (red) | server | |
| ultilogin.recover.reset | `language: en`; the pending recovery from `ultilogin.recover.request`; maintainer-configured SMTP account (personal credentials) able to read the real received code | Run `/recover <the real received code> newpass1 newpass1` | `recover_success`: "Password reset successful! You have been logged in." (green); the player is now logged in with NO intervening credential prompt (round 7, `EmailVerificationService#resetPasswordAfterRecovery` → `LoginService#resetPasswordForRecovery` suppresses it) — an immediate blocked action (e.g. moving) is NOT cancelled | human | |
| ultilogin.recover.reset.neg-wrong-code | `language: en`; the pending recovery from `ultilogin.recover.request` still open (a real send already happened to create it; this row does not need the real received code, only a WRONG one) | Run `/recover 000000 newpass1 newpass1` | Literal, unresolved `Invalid verification code! Remaining attempts: {COUNT}` — `RecoverVerifyResult` carries no substitution mechanism, so the `{COUNT}` placeholder is never replaced (`UltiKits/UltiLogin#21`); the password is NOT changed | human | |
| ultilogin.recover.help | `language: en` | Run literal `/recover help` (bare `/recover` reaches `requestRecovery` instead, see `ultilogin.recover.request` above) | `help_recover` ("/recover - Request password recovery code") then `help_recover_reset` ("/recover <code> <new_password> <confirm> - Reset password") — both via `plugin.i18n(...)`, respecting `language` | server | |

## Admin

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultilogin.admin.force-login | An online, registered, not-logged-in target player; an OP or `ultilogin.admin`-holding sender | Run `/logadmin forcelogin <target>` | `messages.admin.force-login`'s shipped (Simplified Chinese, not reproduced per D-02) text, with `{PLAYER}` substituted, to the admin, green; a second hardcoded Simplified Chinese line (`LoginAdminCommand.java:136`, not reproduced per D-02) to the target, green; the target is now logged in | server | |
| ultilogin.admin.force-login.neg-already-logged | The target is already logged in | Run `/logadmin forcelogin <target>` | A hardcoded Simplified Chinese line (`LoginAdminCommand.java:129`, not reproduced per D-02), yellow — unaffected by `language: en`; `lang/en.json`'s `admin_player_already_logged` key is declared but never read (`UltiKits/UltiLogin#20`) | server | |
| ultilogin.admin.force-login.neg-not-found | The target is offline | Run `/logadmin forcelogin <target>` | `messages.admin.player-not-found`'s shipped (Simplified Chinese, not reproduced per D-02) text, with `{PLAYER}` substituted | server | |
| ultilogin.admin.help | none | Run bare `/logadmin` and, separately, literal `/logadmin help` | Both print the identical five-line hardcoded Simplified Chinese block (`LoginAdminCommand.java:207-211`, not reproduced per D-02), unaffected by `language: en`; `lang/en.json`'s five `help_admin_*` keys are declared but never read (`UltiKits/UltiLogin#20`) | server | |
| ultilogin.admin.info | A registered target player with a bound, verified email | Run `/logadmin info <target>` | Eight hardcoded Simplified Chinese lines (`LoginAdminCommand.java:181-197`, not reproduced per D-02: player name, UUID, register/last IP, login count, email + verified state, register/last-login timestamps) — unaffected by `language: en`; `lang/en.json`'s 11 `info_*` keys are declared but never read (`UltiKits/UltiLogin#20`) | server | |
| ultilogin.admin.info.neg-not-found | The target has never registered | Run `/logadmin info <target>` | `messages.admin.account-not-found`'s shipped (Simplified Chinese, not reproduced per D-02) text, with `{PLAYER}` substituted | server | |
| ultilogin.admin.reset-password | A registered target; `<newpassword>` valid under command-mode length rules | Run `/logadmin reset <target> <newpassword>` | `messages.admin.password-reset`'s shipped (Simplified Chinese, not reproduced per D-02) text, green, with `{PLAYER}`/`{PASSWORD}` substituted; if the target is online, they are force-revoked and re-prompted exactly as `ultilogin.changepassword.update` describes | server | |
| ultilogin.admin.reset-password.neg-not-found | The target has never registered | Run `/logadmin reset <target> anypassword` | `messages.admin.account-not-found`'s shipped (Simplified Chinese, not reproduced per D-02) text | server | |
| ultilogin.admin.reset-random | A registered target; `gui-mode.enabled: false` (shipped default) | Run `/logadmin reset <target>` | `messages.admin.password-reset`'s shipped (Simplified Chinese, not reproduced per D-02) text, green, with a freshly-generated 8-character alphanumeric password reported in `{PASSWORD}`; that reported password successfully logs the target in afterward | server | |
| ultilogin.admin.unregister | A registered target | Run `/logadmin unregister <target>` | `messages.admin.unregister`'s shipped (Simplified Chinese, not reproduced per D-02) text, green, with `{PLAYER}` substituted; a subsequent `/logadmin info <target>` shows `messages.admin.account-not-found`'s shipped text | server | |
| ultilogin.admin.unregister.neg-not-found | The target has never registered | Run `/logadmin unregister <target>` | `messages.admin.account-not-found`'s shipped (Simplified Chinese, not reproduced per D-02) text | server | |

## Panel

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultilogin.panel.open | `language: en`; `ulticloud.enabled: true`; maintainer-authenticated UltiCloud panel session (personal credentials) | Run `/panel`, then click the returned link and complete the UltiCloud login in a browser | `panel_generating` ("Generating panel link...", gray) then a clickable `panel_link_sent` line ("Click here to open the panel: {URL}", green, underlined URL); once the poll confirms completion, `panel_auth_success_owner` or `panel_auth_success_player` (depending on the authenticated account's role) and the player is now logged in | human | |
| ultilogin.panel.open.neg-disabled | `language: en`; `ulticloud.enabled: false` (shipped default) | Run `/panel` | `panel_not_enabled`: "UltiCloud integration is not enabled on this server." (red) | server | |
| ultilogin.panel.help | none | Run literal `/panel help` (bare `/panel` reaches `openPanel` instead, see `ultilogin.panel.open` above) | "Usage: /panel - Open UltiCloud web panel" (yellow) — the one help line in this module hardcoded in English rather than routed through `plugin.i18n(...)` or a lang key at all, so its text happens to be correct under any `language` setting by coincidence, not by design | server | |

## Player Protection

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultilogin.protection.chat-block | Not logged in; `gui-mode.enabled: false` | Send a chat message | The message is cancelled — no other player receives it; the sender receives `messages.login-prompt` or `messages.register-prompt` (per registration state) again | server | |
| ultilogin.protection.command-block | Not logged in; attempt a command NOT on the shipped `allowed-commands` list (e.g. `/help`) | Run `/help` | The command is cancelled; the same login/register prompt as `ultilogin.protection.chat-block` is re-sent | server | |
| ultilogin.protection.command-block.neg-allowed | Not logged in | Run `/login <anything>` (a command ON the shipped `allowed-commands` list) | The command is NOT cancelled by this guard — it reaches `LoginCommand#login` normally (its own validation applies separately) | server | |
| ultilogin.protection.damage-block | Not logged in (as the potential victim); a hostile mob or another player nearby able to deal damage | Attempt to take damage while not logged in | The damage event is cancelled — health is unchanged | server | |
| ultilogin.protection.damage-block.neg-attacker | An unauthenticated player attacks an ONLINE, LOGGED-IN target | The unauthenticated player attacks the logged-in target | The damage event is cancelled — the logged-in target's health is unchanged, even though the target itself is authenticated | server | |
| ultilogin.protection.inventory-block | Not logged in; `gui-mode.enabled: false` (so no credential GUI is open) | Attempt to open the player's own inventory (`e`) or a chest | The open attempt is cancelled — the inventory does not open | server | |
| ultilogin.protection.inventory-block.neg-credential-gui-allowed | Not logged in; `gui-mode.enabled: true`; `LoginGUIPage` or `RegisterGUIPage` currently open (its title therefore contains one of the three hardcoded Simplified Chinese substrings `ultilogin.protection.inventory-block` describes) | Click a slot inside the open credential GUI | The click is NOT cancelled by this guard — the GUI's own `Icon#onClick` handler runs normally | pixel | LoginGUIPage, RegisterGUIPage |
| ultilogin.protection.join | `gui-mode.enabled: false`; `session-enabled: false`; a registered, previously-logged-out player joining | The player joins | The join broadcast is unaffected by this module; the joining player alone receives `messages.login-prompt` (or `messages.register-prompt` if unregistered); if `blind-effect: true` (shipped default) the player receives an undiminishing blindness effect | server | |
| ultilogin.protection.join.neg-valid-session | `session-enabled: true` (shipped default); the player logged in from the SAME IP within the last `session-timeout` minutes, then quit (without a server restart) | The same player, from the same IP, rejoins | A hardcoded Simplified Chinese line (`LoginService.java:888`, not reproduced per D-02), green — unaffected by `language: en`; `lang/en.json`'s `session_login` key is declared but never read (`UltiKits/UltiLogin#20`); the player is immediately logged in with no prompt and no blindness effect | server | |
| ultilogin.protection.movement-block | Not logged in | Attempt to walk to a different block | The player is snapped back to their previous block position; looking around in place (no block-coordinate change) is NOT reverted | server | |
| ultilogin.protection.quit | Logged in, with an in-flight `/panel` poll active (see `ultilogin.panel.open`) | The player disconnects | The player's login/join-time/original-location state is cleared; the in-flight panel-poll task is cancelled (confirmed by the poll never firing a completion callback after this point) | server | |
| ultilogin.protection.world-interaction-block | Not logged in | Attempt, in turn: break a block, place a block, interact with a block, interact with an entity, drop an item, pick up an item | Every one of the six actions is cancelled | server | |

## GUI

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultilogin.gui.login-page | `gui-mode.enabled: true`; `gui-mode.password-length: 4` (shipped default); a registered, unauthenticated player joins | Observe the opened GUI, click four number buttons matching the account's real password, do not click Confirm | The GUI title reads the `gui-mode.title-login` value (translated `&`-codes); nine numbered wool buttons and a masked password display (●○○○ progressing to ●●●●) are visible; on the 4th digit the GUI auto-submits after a short delay without a manual Confirm click, closes, and the player is logged in | pixel | LoginGUIPage |
| ultilogin.gui.login-page.neg-wrong-password | Same as `ultilogin.gui.login-page`; zero prior failed attempts this lockout window (so this attempt lands on the attempts-remaining branch, not the lockout branch) | Enter four wrong digits | The password display resets to `○○○○`; the player receives `messages.attempts-remaining`'s shipped text with `{COUNT}` substituted to one less than `security.max-login-attempts` — `LoginGUIPage#attemptLogin` forwards `LoginService#login`'s result unchanged, which is always `messages.attempts-remaining` or, at lockout, `messages.account-locked`; `messages.wrong-password` is never reachable here either (`UltiKits/UltiLogin#23`); the GUI itself remains open, not closed | pixel | LoginGUIPage |
| ultilogin.gui.register-page | `gui-mode.enabled: true`; an unregistered player joins | Observe the opened GUI; enter four digits for the first phase, then the SAME four digits again for the confirmation phase | The GUI title starts at `gui-mode.title-register`, switches to `gui-mode.title-confirm` after the first phase's 4th digit auto-advances; on the matching confirmation, `messages.register-success`'s shipped text appears (the same success path `ultilogin.register.create` exercises) and the GUI closes | pixel | RegisterGUIPage |
| ultilogin.gui.register-page.neg-mismatch | Same as `ultilogin.gui.register-page`, except the confirmation phase's four digits DIFFER from the first phase's | Enter four digits, then four DIFFERENT digits at the confirmation phase | `messages.password-mismatch` is sent; the GUI resets to the first phase (title reverts to `gui-mode.title-register`, password display clears) rather than closing | pixel | RegisterGUIPage |

## Scheduled Tasks

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultilogin.task.timeout-check | `login-timeout: 10` (the minimum value `@Range(min = 10, max = 600)` on `LoginConfig#loginTimeout` accepts — NOT the shipped default 60, lowered for this row only to keep the wait short; a value below 10 fails config validation and would not exercise the real timeout path at all); an unauthenticated player online | Wait at least 12 seconds without logging in — `checkTimeouts` runs only once every 20 ticks (1s) and uses a strict `elapsed > timeout` comparison, so depending on scheduler phase the kick may not fire until nearly a full second past the 10-second mark; waiting only "just over 10 seconds" can false-fail a healthy server | The player is kicked with `messages.timeout-kick`'s shipped (Simplified Chinese, not reproduced per D-02) text as the disconnect reason | server | |
| ultilogin.task.verification-cleanup | `verification.code-expiry-seconds: 60` (the minimum `@Range(min = 60, max = 1800)` on `EmailConfig#codeExpirySeconds` accepts — NOT the shipped default 300, lowered for this row only); a pending email bind or recovery request created via `ultilogin.email.bind` or `ultilogin.recover.request` | Read `EmailVerificationService.java` directly: `cleanupExpired()`'s own `@Scheduled(period = 1200, ...)` fires every 60 seconds regardless of `verification.code-expiry-seconds`, and removes any `pendingBinds`/`pendingRecoveries` entry whose age exceeds that key — separately, `verifyEmailBind`/`verifyRecoveryCode` ALSO discard an expired entry inline on the very call that discovers it (returning `email_code_expired`), so no live command sequence can distinguish "the scheduled sweep already removed this entry" from "my own verification attempt just removed it on this call" — both paths converge on the identical `email_no_pending` outcome for any subsequent attempt, making a live behavioral assertion of the SCHEDULED sweep specifically unobtainable through the command surface alone | The 60-second period argument and the age-based removal condition are both present in `cleanupExpired()`'s source exactly as described; this is a source-level confirmation, not a live gameplay assertion (the same reasoning UltiChat's own `ultichat.channel.cleanup-on-quit` checklist row applies to an analogous unobservable-from-the-command-surface cleanup path) | protocol | |

## Data persistence

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultilogin.account.restart-survival | An account registered via `ultilogin.register.create`, with its email verified via `ultilogin.email.verify`; not logged in at the time of the restart; the account's CURRENT login count noted via `/logadmin info <the account's player>` BEFORE the restart | Stop the server completely, then start it again, then rejoin and run `/login <the original password>`, then run `/logadmin info <the account's player>` again | The `/login` attempt with the pre-restart password succeeds (`messages.login-success`, proving the password hash/salt survived); `/login` itself increments `loginCount` (`LoginService#login`), so the post-restart `/logadmin info` shows the login count as EXACTLY ONE MORE than the pre-restart value noted above, not identical to it; IP history and bound/verified email read back identical to their pre-restart values | server | |
| ultilogin.session.not-persisted | `session-enabled: true` (shipped default); `session-timeout: 30` (shipped default); a player logs in from a known IP, then quits, well within the 30-minute window | Stop the server completely, then start it again, then have the same player rejoin from the same IP | The player is NOT auto-logged-in — they receive the ordinary `messages.login-prompt` exactly as a first-time-this-session join would, because the in-memory session map does not survive the restart, regardless of the 30-minute window not having elapsed | server | |

## Configuration

One row per `@ConfigEntity` class (D-06's config-per-file rule), not per key: `login.yml`
(44 keys) and `email.yml` (8 keys) — 52 keys total, matching `FEATURES.md`'s `## Configuration`
section exactly. Each row confirms every key in the file is present at its `FEATURES.md`-documented
default, then flips one or more representative keys and observes the behaviour follow.

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultilogin.config.login-yml | Fresh `plugins/UltiTools/UltiLogin/config/login.yml` at its shipped default | Load the file; confirm all 44 keys listed under `FEATURES.md`'s `## Configuration` section are present at their documented defaults; then set `gui-mode.enabled: true` (default `false`), restart, and confirm a fresh player join opens `RegisterGUIPage`/`LoginGUIPage` rather than the text prompt (see `ultilogin.gui.register-page`/`ultilogin.gui.login-page`); separately, set `security.max-login-attempts: 2` (default 5) and confirm lockout now triggers on the 2nd wrong `/login` attempt, not the 5th | All 44 keys present at their documented defaults before the change; after `gui-mode.enabled: true`, the GUI opens on join instead of the text prompt; after `security.max-login-attempts: 2`, `messages.account-locked` appears on the 2nd wrong attempt rather than the 5th | server | |
| ultilogin.config.email-yml | Fresh `plugins/UltiTools/UltiLogin/config/email.yml` at its shipped default; `email.enable: true` and a working SMTP configuration | Load the file; confirm all 8 keys listed under `FEATURES.md`'s `## Configuration` section are present at their documented defaults; then set `verification.code-length: 4` (default 6) and request a new email bind, reading the actual received code's length | All 8 keys present at their documented defaults before the change; after the change, the newly-sent code is 4 digits long, not 6 | human | |
