# UltiLogin — Feature Inventory

This document catalogues every operator- or player-visible function, command, content item and
configuration key in this repository, as read directly from source. It is an internal reference
for UAT execution and issue reconciliation — the public description of these features lives on
<https://doc.ultikits.com/>. Update this file in the same pull request as any feature change.

## Conventions

- **ID grammar:** `<repo-slug>.<area>.<action>`, dot-separated, every segment lowercase ASCII
  drawn from `[a-z0-9-]`. `<repo-slug>` is the repository name lowercased with no separators —
  `ultilogin` here, `ultitools`, `ultichat`, `ultiessentials`, and `ultitools-example` for
  `UltiTools-External-Example`. `<area>` is the feature section's slug. `<action>` is the verb.
  A `config` row is the one shape that exceeds three segments and is exempt from the
  lowercase-ASCII rule for its key-path suffix:
  `<repo-slug>.config.<file-stem>.<yml key path>`, the key path keeping its own dots and its own
  casing verbatim from the yml file — a config ID is a citation of the key, not a re-derived slug,
  so lowercasing it would make it un-greppable against its own source line. An ID changes only
  when the feature's identity changes, never on rewording. IDs are unique within a repository.
- **Kind**, exactly these eight values: `command`, `config`, `event`, `gui`, `scheduled`,
  `placeholder`, `persistence`, `gate`. Each maps one-to-one onto a reconciliation-table line.
  This module has no `gate` rows (`@ConditionalOnConfig` count is 0, confirmed below) and no
  `placeholder` rows (this module registers no PlaceholderAPI expansion and consumes none) — both
  Kinds stay in the vocabulary for cross-repository consistency even though neither appears below.
  This module is the fan-out's first to carry `gui`-Kind rows: its two Phase 9 GUI-excluded
  classes (`LoginGUIPage`, `RegisterGUIPage`) are catalogued as their own rows, distinct from the
  `event` row documenting when each one opens.
- **Tier**, exactly three: `player`, `admin`, `internal`. Judged from what the feature is for, not
  from whether it carries a permission string — of this module's seven `@CmdExecutor` classes,
  only three declare a permission node at all (`ChangePasswordCommand`: `ultilogin.changepassword`,
  `EmailBindCommand`: `ultilogin.email`, `RecoverCommand`: `ultilogin.recover`, `LoginAdminCommand`:
  `ultilogin.admin`) and Bukkit grants every one of them to OP by default with no explicit
  `plugin.yml` permission declaration, so the string alone cannot distinguish "for every player"
  from "for the server owner" the way this document's Tier column can.
- **Manual**, exactly three: `detailed`, `brief`, `none`.
- **Target**, exactly four: `player`, `console`, `both`, or `n/a` — the first three read straight
  off `@CmdTarget` for a `command` row; it is a property, not a tier. `n/a` is for every other
  Kind (`config`, `event`, `gate`, `gui`, `persistence`, `scheduled`, `placeholder`). `PanelCommand`
  and `RecoverCommand`'s individual `@CmdMapping` methods carry no class-level `@CmdTarget` at all
  and rely on the framework's own `@CmdSender Player` parameter binding to reject a non-player
  sender before the method body runs — Target is recorded as `player` for those rows, matching the
  actual restriction, not `n/a`.
- **Permission:** the literal node string, `none`, or `n/a`, each optionally suffixed with the
  literal text `(requireOp=true)` (preceded by one space) when the row's class-level
  `@CmdExecutor` carries that flag — none of this module's seven `@CmdExecutor` classes sets
  `requireOp = true`, so no row below carries the suffix. `n/a` is for every Kind that is not
  `command`.
- **Source:** `ClassName#member` — the class and member that actually reads or applies the
  feature — for every Kind, `config` included: all 52 `config` rows below cite the reading
  member. Unlike the framework's own `config.yml` (read directly via Bukkit's
  `FileConfiguration`, with no bound entity at all), both of this module's configuration files
  (`login.yml`, `email.yml`) are real `@ConfigEntity`/`@ConfigEntry`-bound classes, so a config
  row's Source cites whichever class and method actually calls the generated getter — not the
  config class's own field declaration, which merely binds the key.
- **Row order:** by section, then by ID ascending within the section.
- **No manual prose:** no troubleshooting column, no explanatory paragraphs, no draft page text.
  A hazard noticed while reading becomes a negative checklist row, not a note here. Where a
  feature's actual runtime behaviour genuinely diverges from what its lang key or config comment
  describes (a declared-but-dead i18n key, a hardcoded literal ignoring `language`), that fact is
  itself part of "what the feature does" and is stated here as a plain, sourced observation, with
  the filed issue number, never as advice on how to fix it.

### Reconciliation command family

The canonical form for counting an annotation site across this repository's real sources:

```bash
find <repo-root> -path '*/src/main/java/*' -name '*.java' -not -path '*/target/*' \
  -not -path '*/.worktrees/*' -print0 | xargs -0 grep -nE '^[[:space:]]*@AnnotationName\b' | wc -l
```

This form defeats three measured traps, each of which produces a wrong-but-plausible number
rather than an error:

1. **Multi-root repositories** — UltiBot's sources live under `ultibot-api/`, `ultibot-core/`
   and `ultibot-v1_21_R1/`, so a naive `<repo>/src/main/java` glob returns 0 for it, silently.
   This module is a single-root Maven project (`src/main/java` only), so this trap does not apply
   to it, but the robust `find` form is used regardless — the same command must work unmodified
   across all 18 repositories.
2. **Git worktrees and build output** — UltiEconomy carries
   `.worktrees/economy-v2/src/main/java`, so a `find` without the `-not -path` exclusions above
   reports 48 `@CmdMapping` sites where the real number is 24. This module carries no worktree
   directory.
3. **Javadoc and string literals** — requiring the annotation to start its own line (the
   `^[[:space:]]*@` anchor) is what defeats a javadoc mention or a warning-message string literal
   that merely contains the annotation's name as text. This module's own long javadoc blocks
   reference method names like `#invalidateSession(UUID)` and Codex thread ids extensively, but
   never write `@CmdMapping`/`@EventHandler`/`@Scheduled`/`@ConfigEntry` as literal text at the
   start of a line, so this module's naive and line-start counts are identical for every
   annotation kind measured below — no javadoc or string-literal false positive exists in this
   module's source — but the anchored form is still the one used, so the same command is
   trustworthy unmodified against every repository in the fan-out.

**Positive control:** the line-start form returns `@CmdExecutor` = 7, `@CmdMapping` = 17,
`@EventListener` = 1 (class), `@EventHandler` = 15 (handler methods), `@Scheduled` = 2,
`@ConditionalOnConfig` = 0, `@ConfigEntity` = 2 (classes), `@ConfigEntry` = 52 (44 on
`LoginConfig`, 8 on `EmailConfig`) — confirmed by reading every one of the 16 source files
directly, not by trusting the count alone. `LoginAdminCommand`'s six `@CmdMapping` sites
(`reset <player>` at line 44, `reset <player> <password>` at line 73, `forcelogin <player>` at
line 111, `unregister <player>` at line 145, `info <player>` at line 169, and the bare `""` at
line 200) are this module's standing positive control — the class with the most sub-commands
behind one executor, the shape most likely to silently drop a row under a naive approach. This
document's command-row count (20) diverges from the `@CmdMapping` count (17) for two explained
reasons, stated in the `## Email Binding` and `## Password Recovery`/`## Panel` sections below.

## Login

`LoginCommand` — class-level `@CmdExecutor(alias = {"login", "l"})` (its `description` attribute is Simplified Chinese, not reproduced in this English-only document per D-02),
`@CmdTarget(PLAYER)`. Two `@CmdMapping` sites: `<password>` (the real login attempt) and the bare
`""` (dispatches to `#help`, which itself calls `#handleHelp`). `/login help` (the literal word)
reaches the identical `#handleHelp` body through the framework's own short-circuit ahead of
`matchMethod` — the same mechanism the framework's own `FEATURES.md` documents for `/upm help` —
so both invocation styles are one row here, not two.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.login.authenticate | Authenticate a registered, currently-unauthenticated player against their stored password hash; locks the account/IP after too many wrong attempts (see `## Player Protection`'s security-lockout config rows) | command | `/login <password>` (alias `/l`) | none | player | player | brief | LoginCommand#login |
| ultilogin.login.help | Print `/login` usage — a hardcoded Simplified Chinese line (`LoginCommand.java:67`), unaffected by `language: en`; `lang/en.json`'s `help_login` key is declared but never read (UltiKits/UltiLogin#20) | command | bare `/login` or literal `/login help` | none | player | player | none | LoginCommand#handleHelp |

## Register

`RegisterCommand` — class-level `@CmdExecutor(alias = {"register", "reg"}, description =
`description` attribute, Simplified Chinese, not reproduced here)`, `@CmdTarget(PLAYER)`. Same help short-circuit note as `## Login` above applies
identically here.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.register.create | Create a new account for an unregistered player, subject to the per-IP registration cap, password-mode validation (GUI: exact-length digits; command: length range), and a password/confirm match check; auto-logs the new account in on success | command | `/register <password> <confirm>` (alias `/reg`) | none | player | player | brief | RegisterCommand#register |
| ultilogin.register.help | Print `/register` usage, with a mode-dependent second line (GUI: required digit count; command: min/max length) — both lines hardcoded Simplified Chinese, unaffected by `language: en`; `lang/en.json`'s `help_register` key is declared but never read (UltiKits/UltiLogin#20) | command | bare `/register` or literal `/register help` | none | player | player | none | RegisterCommand#handleHelp |

## Change Password

`ChangePasswordCommand` — class-level `@CmdExecutor(alias = {"changepassword", "changepw", "cpw"},
permission = "ultilogin.changepassword")` (its `description` attribute is Simplified Chinese, not reproduced here), `@CmdTarget(PLAYER)`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.changepassword.update | Change a currently-authenticated player's own password, verifying the old one first; on success this invalidates every session for the account (see `## Player Protection`'s session rows) and force-revokes the caller's own active login state, immediately re-presenting the credential prompt (F-L1, fixed 13-uat-results.md #14) | command | `/changepassword <old> <new> <confirm>` (aliases `/changepw`, `/cpw`) | ultilogin.changepassword | player | player | detailed | ChangePasswordCommand#changePassword |
| ultilogin.changepassword.help | Print `/changepassword` usage, with a mode-dependent second line, plus three inline validation messages (`ChangePasswordCommand.java:43,56,62,64`) — all hardcoded Simplified Chinese, unaffected by `language: en`; `lang/en.json`'s `help_change_password`/`change_password_success`/`change_password_wrong` keys are declared but never read (UltiKits/UltiLogin#20) | command | bare `/changepassword` or literal `/changepassword help` | ultilogin.changepassword | player | player | none | ChangePasswordCommand#handleHelp |

## Email Binding

`EmailBindCommand` — class-level `@CmdExecutor(alias = {"regs"}, permission = "ultilogin.email",
`description` attribute, Simplified Chinese, not reproduced here)`. Both `@CmdMapping` methods carry their own `@CmdTarget(PLAYER)`
directly (no class-level `@CmdTarget` exists on this class). One `@CmdMapping(format = "<arg>")`
site (`#handleCommand`) branches internally on whether `arg` contains `@`: an address starts a new
email-verification request, anything else is treated as a submitted verification code — two
materially different, independently-observable behaviours sharing one annotation site, split into
two rows here exactly as the framework's own `FEATURES.md` splits `/upm update`/`/upm update all`.
This is this section's explained reason for the command-row count exceeding the `@CmdMapping`
count.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.email.bind | Request binding an email address to the caller's own account (must be logged in); validated against a regex, an already-bound-and-verified guard, a domain blacklist, a per-email account cap, and a resend cooldown, then sends a verification code | command | `/regs <email>` (e.g. `/regs user@example.com`) | ultilogin.email | player | player | brief | EmailBindCommand#handleCommand |
| ultilogin.email.verify | Verify a previously-requested email bind with its code (case-insensitive match, expiry, and a bounded attempt count that discards the pending request on exhaustion); on success marks the account's email verified and, if `reward.enabled`, dispatches the configured reward command(s) as console | command | `/regs <code>` (e.g. `/regs 123456`) | ultilogin.email | player | player | brief | EmailBindCommand#handleCommand |
| ultilogin.email.help | Print the two `/regs` usage lines (bind, verify), both via `plugin.i18n(...)` — respects `language` | command | bare `/regs` or literal `/regs help` | ultilogin.email | player | player | none | EmailBindCommand#handleHelp |

## Password Recovery

`RecoverCommand` — class-level `@CmdExecutor(alias = {"recover"}, permission =
"ultilogin.recover")` (its `description` attribute is Simplified Chinese, not reproduced here). Both `@CmdMapping` methods carry their own
`@CmdTarget(PLAYER)` directly. The bare `""` mapping is a real feature here (request a recovery
code), unlike `## Login`/`## Register`/`## Change Password`/`LoginAdminCommand` above, where the
bare mapping dispatches to help text — so `/recover help` (the literal word) reaches
`#handleHelp` only through the framework's own short-circuit, with no `@CmdMapping` site of its
own at all, exactly like the framework's own `/upm help`. That is this section's contribution to
the command-row count exceeding the `@CmdMapping` count.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.recover.request | Request a password-recovery code by email for an unauthenticated, registered account with a bound and verified email address; subject to the shared email resend cooldown | command | `/recover` | ultilogin.recover | player | player | brief | RecoverCommand#requestRecovery |
| ultilogin.recover.reset | Verify a recovery code and, on success, set a new password and log the account straight in — without presenting the ordinary post-invalidation credential prompt in between (round 7, Codex PR UltiLogin#18), since this call completes the login itself immediately afterward | command | `/recover <code> <password> <confirm>` | ultilogin.recover | player | player | detailed | RecoverCommand#resetPassword |
| ultilogin.recover.help | Print the two `/recover` usage lines, both via `plugin.i18n(...)` — respects `language`. Reachable only via the literal word `/recover help`, since bare `/recover` matches the real `requestRecovery` mapping above instead | command | literal `/recover help` | ultilogin.recover | player | player | none | RecoverCommand#handleHelp |

## Admin

`LoginAdminCommand` — class-level `@CmdExecutor(alias = {"logadmin", "loginadmin"}, permission =
"ultilogin.admin")` (its `description` attribute is Simplified Chinese, not reproduced here), `@CmdTarget(BOTH)`. Six `@CmdMapping`
sites, all documented in full below (this module's standing positive control, see the
reconciliation note above): `reset <player>` (line 44), `reset <player> <password>` (line 73),
`forcelogin <player>` (line 111), `unregister <player>` (line 145), `info <player>` (line 169),
bare `""` (line 200, dispatches to `#help` → `#handleHelp`).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.admin.force-login | Log a currently-online, registered, not-yet-authenticated player in without their password | command | `/logadmin forcelogin <player>` | ultilogin.admin | both | admin | brief | LoginAdminCommand#forceLogin |
| ultilogin.admin.help | Print the `/logadmin` sub-command list — hardcoded Simplified Chinese, unaffected by `language: en`; `lang/en.json`'s `help_admin_header`/`help_admin_reset`/`help_admin_forcelogin`/`help_admin_unregister`/`help_admin_info` keys are declared but never read (UltiKits/UltiLogin#20) | command | bare `/logadmin` or literal `/logadmin help` | ultilogin.admin | both | admin | none | LoginAdminCommand#handleHelp |
| ultilogin.admin.info | Show a named player's full account record: name, UUID, registration/last IP, login count, bound email and its verification state, registration and last-login timestamps — entirely hardcoded Simplified Chinese labels, unaffected by `language: en`; `lang/en.json`'s 11 `info_*` keys are declared but never read (UltiKits/UltiLogin#20) | command | `/logadmin info <player>` | ultilogin.admin | both | admin | brief | LoginAdminCommand#showInfo |
| ultilogin.admin.reset-password | Reset a named player's password to an admin-supplied value, after the same password-mode validation `## Register` uses | command | `/logadmin reset <player> <password>` | ultilogin.admin | both | admin | brief | LoginAdminCommand#resetPasswordWithValue |
| ultilogin.admin.reset-random | Reset a named player's password to a freshly-generated random value (numeric in GUI mode, alphanumeric in command mode) and report it in chat | command | `/logadmin reset <player>` | ultilogin.admin | both | admin | brief | LoginAdminCommand#resetPassword |
| ultilogin.admin.unregister | Permanently delete a named player's account record | command | `/logadmin unregister <player>` | ultilogin.admin | both | admin | brief | LoginAdminCommand#unregister |

## Panel

`PanelCommand` — class-level `@CmdExecutor(alias = {"panel"}, description = "Open UltiCloud web
panel")`, `@CmdTarget(PLAYER)`. One `@CmdMapping(format = "")` site (`#openPanel`) — the bare
invocation is the real feature (unlike `## Login`/`## Register`/etc.), so `/panel help` (the
literal word) reaches `#handleHelp` only through the framework's short-circuit, with no
`@CmdMapping` site of its own — the same shape as `## Password Recovery`'s own extra row.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.panel.help | Print `/panel` usage — the one help line in this module written in English regardless of `language`, since it is a plain hardcoded literal rather than routed through either `plugin.i18n(...)` or a lang-catalogue key at all | command | literal `/panel help` | none | player | player | none | PanelCommand#handleHelp |
| ultilogin.panel.open | Gated behind `ulticloud.enabled`; on success sends an async-generated, clickable UltiCloud magic-link URL and starts polling for authentication completion. Every stage is fenced against a credential change (admin reset/unregister, self password change) racing the async HTTP call or the poll itself, keyed on a per-player invalidation generation and, once published, the exact request id — layered fixes from Codex PR UltiLogin#18 rounds 4, 5, 7, 9 and 11 | command | `/panel` | none | player | player | detailed | PanelCommand#openPanel |

## Player Protection

`LoginProtectionListener` — one `@EventListener`-annotated class (`grep -c '@EventListener'`
confirms this: exactly 1), registering 15 `@EventHandler` methods, all at `EventPriority.LOWEST`
except `#onPlayerQuit`. This section groups the 15 handler methods into 8 rows, one row per
distinct guarded behaviour rather than one row per Bukkit event type, following the same "one row
per handler, bundled by behaviour" convention the framework's own `FEATURES.md` and UltiChat's
`FEATURES.md` (`ChatListener#onChat`) already apply — six of the fifteen handlers
(`onBlockBreak`/`onBlockPlace`/`onPlayerInteract`/`onPlayerInteractEntity`/`onPlayerDropItem`/
`onPlayerPickupItem`) share the byte-identical single-line guard body (`cancelIfNotLoggedIn`), and
two more (`onPlayerDamage`/`onPlayerDamageEntity`) are the symmetric halves of one property (an
unauthenticated player can neither take nor deal damage) — bundling these does not lose any
distinguishable behaviour a reconciliation reviewer could find missing. The reconciliation table's
`@EventHandler` line (15) and this section's row count (8) are reconciled by this paragraph, not
by a 1:1 count.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.protection.chat-block | Cancel an unauthenticated player's chat message and re-send the login/register prompt (text or GUI, per `gui-mode.enabled`) | event | send a chat message while not logged in | n/a | n/a | player | brief | LoginProtectionListener#onPlayerChat |
| ultilogin.protection.command-block | Cancel any command an unauthenticated player runs that is not on the `allowed-commands` whitelist, and re-send the login/register prompt | event | run a non-whitelisted command while not logged in | n/a | n/a | player | brief | LoginProtectionListener#onPlayerCommand |
| ultilogin.protection.damage-block | Cancel damage taken by an unauthenticated player (`onPlayerDamage`), and separately cancel damage dealt by an unauthenticated attacker against any target (`onPlayerDamageEntity`) — two independent guards on the two ends of a damage event | event | be damaged, or attack another entity, while not logged in | n/a | n/a | player | brief | LoginProtectionListener#onPlayerDamage, LoginProtectionListener#onPlayerDamageEntity |
| ultilogin.protection.inventory-block | Cancel inventory open/click for an unauthenticated player unless the inventory's title contains one of three hardcoded Simplified Chinese substrings meaning "password", "login", and "register" (`LoginProtectionListener.java:124,138`) — a hardcoded-string check against the GUI's own (config-supplied) title, not a type check against `LoginGUIPage`/`RegisterGUIPage`, so a server-owner-customised `gui-mode.title-*` value that happens to drop all three substrings would silently lock an unauthenticated player out of their own credential GUI | event | open or click any inventory while not logged in | n/a | n/a | player | detailed | LoginProtectionListener#onInventoryClick, LoginProtectionListener#onInventoryOpen |
| ultilogin.protection.join | On join: mark the player unauthenticated, record their pre-login location, auto-login if a valid session exists for their `ip:uuid` (announcing a hardcoded Simplified Chinese line, `LoginService.java:888`, regardless of `language` — `lang/en.json`'s `session_login` key declared but never read, UltiKits/UltiLogin#20), otherwise apply the configured blindness effect and spawn-location teleport and send the login/register prompt — opening `LoginGUIPage`/`RegisterGUIPage` after a 20-tick delay when `gui-mode.enabled`, or the equivalent text prompt otherwise | event | join the server | n/a | n/a | player | detailed | LoginProtectionListener#onPlayerJoin |
| ultilogin.protection.movement-block | Cancel an unauthenticated player's block-to-block movement (looking around in place is deliberately allowed — only a change to `getBlockX`/`Y`/`Z` is reverted) | event | attempt to move while not logged in | n/a | n/a | player | brief | LoginProtectionListener#onPlayerMove |
| ultilogin.protection.quit | Clear the quitting player's in-memory login/join-time/original-location state and cancel any in-flight `/panel` polling task for them | event | quit the server | n/a | n/a | internal | none | LoginProtectionListener#onPlayerQuit |
| ultilogin.protection.world-interaction-block | Cancel block-break, block-place, entity-interact, block/entity-interact, item-drop and item-pickup for an unauthenticated player — six handlers sharing the identical `cancelIfNotLoggedIn` guard body | event | break/place a block, interact, drop, or pick up an item while not logged in | n/a | n/a | player | brief | LoginProtectionListener#onBlockBreak, LoginProtectionListener#onBlockPlace, LoginProtectionListener#onPlayerInteract, LoginProtectionListener#onPlayerInteractEntity, LoginProtectionListener#onPlayerDropItem, LoginProtectionListener#onPlayerPickupItem |

## GUI

Phase 9 excluded both classes below from this module's JaCoCo `check` gate (0/115 lines,
0/30 branches for `LoginGUIPage`; 0/138 lines, 0/38 branches for `RegisterGUIPage` —
`.planning/phases/09-module-ecosystem-readiness-and-test-coverage/gui-exclusions/UltiLogin.md`).
Both are 54-slot `Gui` pages presenting a numeric keypad; neither exists unless
`gui-mode.enabled` is `true`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.gui.login-page | Numeric-keypad login page: nine digit buttons append to a masked password display, auto-submits once the configured digit count is reached, a clear button resets the input, an exit button closes the GUI and falls back to the text login prompt. Reopens itself after a 10-tick delay if the player is still registered-but-unauthenticated when it closes for any other reason (guarded against re-opening during a deliberate transition to `RegisterGUIPage`, and against stacking a second reopen on top of one `presentCredentialPrompt` already opened — Codex PR UltiLogin#18 rounds 9 and 10) | gui | open when `gui-mode.enabled` and a registered, unauthenticated player joins or is re-prompted (see `ultilogin.protection.join`) | n/a | n/a | player | detailed | LoginGUIPage#onOpen |
| ultilogin.gui.register-page | Numeric-keypad registration page: two-phase entry (set password, then confirm), title changes between phases, a mismatch resets to phase one with a chat message, a match calls the same registration path as `/register`. Reopens itself after a 10-tick delay if the player is still unregistered when it closes for any other reason, with the identical transition/stacking guards as `LoginGUIPage` above | gui | open when `gui-mode.enabled` and an unregistered player joins or is re-prompted (see `ultilogin.protection.join`) | n/a | n/a | player | detailed | RegisterGUIPage#onOpen |

## Scheduled Tasks

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.task.timeout-check | Every second, kick any unauthenticated player whose join (or last invalidation) exceeded `login-timeout` seconds ago, and separately sweep expired (>5 minute) pending `/panel` link requests | scheduled | runs automatically every 20 ticks (1s) while the server is up | n/a | n/a | internal | brief | LoginService#checkTimeouts |
| ultilogin.task.verification-cleanup | Every 60 seconds, discard pending email-bind and password-recovery verification entries older than `verification.code-expiry-seconds`, and discard resend-cooldown timestamps older than a fixed 10-minute TTL | scheduled | runs automatically every 1200 ticks (60s) while the server is up | n/a | n/a | internal | none | EmailVerificationService#cleanupExpired |

## Data persistence

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.account.restart-survival | An account's password hash/salt, registration/last IP, login count, and bound-email state (`login_accounts` table, `@Table`-backed) survive a full server restart | persistence | register or change credentials, then restart the server | n/a | n/a | admin | none | AccountData, LoginService#getAccount |
| ultilogin.session.not-persisted | The IP-keyed session map (`session-enabled`'s backing store) is an in-memory `ConcurrentHashMap` only — never written to disk anywhere in this module's source. A full server restart discards every session regardless of how much of `session-timeout` remained, so every previously-authenticated player must log in again after a restart even within their session window | persistence | log in with `session-enabled: true`, then restart the server before the session would have expired, then rejoin | n/a | n/a | admin | brief | LoginService#sessions |

## Configuration

Every `@ConfigEntry`-annotated field across this module's two `@ConfigEntity` classes (52 keys
total: `LoginConfig` 44, `EmailConfig` 8 — matching the reconciliation table's own `@ConfigEntry`
count of 52 exactly). Several of these keys already have a behavioural row above (login/register/
change-password validation, the security lockout, the GUI gate, the two scheduled tasks) — that
row documents the *feature* the key drives, this row documents the *key* itself, at file-and-key
granularity, so the reconciliation table can prove every key is accounted for without also making
every behavioural row carry a `config` Kind.

**Two `messages.*` keys are declared and validated but never read by the command path whose name
they most resemble, each documented in its own row below with the filed issue number
(UltiKits/UltiLogin#20) rather than a claim that editing it changes anything reachable from that
command.**

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.config.email.domain-blacklist | Email domains refused for binding (disposable/temporary-mail providers) | config | `config/email.yml: domain-blacklist (default: 5 entries — 10minutemail.com, tempmail.com, guerrillamail.com, mailinator.com, throwaway.email)` | n/a | n/a | admin | brief | EmailVerificationService#isDomainBlocked |
| ultilogin.config.email.max-accounts-per-email | Maximum number of verified accounts allowed to share one bound email address | config | `config/email.yml: max-accounts-per-email (default: 1)` | n/a | n/a | admin | brief | EmailVerificationService#validateEmailBind |
| ultilogin.config.email.reward.commands | Console commands run once per successful email verification when `reward.enabled` is true; `%player%` is replaced with the player's name | config | `config/email.yml: reward.commands (default: 1 entry, "givemoney %player% 500")` | n/a | n/a | admin | none | EmailVerificationService#executeRewards |
| ultilogin.config.email.reward.enabled | Whether a successful email verification runs the configured reward command(s) | config | `config/email.yml: reward.enabled (default: false)` | n/a | n/a | admin | brief | EmailVerificationService#verifyEmailBind |
| ultilogin.config.email.verification.code-expiry-seconds | How long an email verification or recovery code stays valid | config | `config/email.yml: verification.code-expiry-seconds (default: 300)` | n/a | n/a | admin | brief | EmailVerificationService#verifyEmailBind |
| ultilogin.config.email.verification.code-length | Length of a generated numeric verification code | config | `config/email.yml: verification.code-length (default: 6)` | n/a | n/a | admin | none | EmailVerificationService#requestEmailBind |
| ultilogin.config.email.verification.cooldown-seconds | Minimum time between successive verification/recovery code sends for the same player | config | `config/email.yml: verification.cooldown-seconds (default: 60)` | n/a | n/a | admin | brief | EmailVerificationService#checkCooldown |
| ultilogin.config.email.verification.max-attempts | Maximum wrong-code attempts before a pending verification/recovery is discarded outright | config | `config/email.yml: verification.max-attempts (default: 3)` | n/a | n/a | admin | brief | EmailVerificationService#verifyEmailBind |
| ultilogin.config.login.allowed-commands | Commands (without the leading `/`, namespace-stripped) an unauthenticated player may still run | config | `config/login.yml: allowed-commands (default: [login, l, register, reg, panel, regs, recover])` | n/a | n/a | admin | detailed | LoginService#isCommandAllowed |
| ultilogin.config.login.blind-effect | Whether an unauthenticated player is given an infinite, undiminishing blindness potion effect | config | `config/login.yml: blind-effect (default: true)` | n/a | n/a | admin | brief | LoginService#applyNoSessionProtections |
| ultilogin.config.login.gui-mode.enabled | Whether the numeric-keypad GUI pages (`LoginGUIPage`/`RegisterGUIPage`) are used instead of the plain-text command flow | config | `config/login.yml: gui-mode.enabled (default: false)` | n/a | n/a | admin | detailed | LoginProtectionListener#onPlayerJoin |
| ultilogin.config.login.gui-mode.password-length | Required digit count for a GUI-mode password (1-9 digits) | config | `config/login.yml: gui-mode.password-length (default: 4)` | n/a | n/a | admin | brief | LoginService#isPasswordValid |
| ultilogin.config.login.gui-mode.title-confirm | GUI title shown during the register page's password-confirmation phase | config | `config/login.yml: gui-mode.title-confirm (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | RegisterGUIPage#processInput |
| ultilogin.config.login.gui-mode.title-login | GUI title shown on `LoginGUIPage` | config | `config/login.yml: gui-mode.title-login (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | LoginGUIPage#LoginGUIPage |
| ultilogin.config.login.gui-mode.title-register | GUI title shown on `RegisterGUIPage`'s first phase | config | `config/login.yml: gui-mode.title-register (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | RegisterGUIPage#RegisterGUIPage |
| ultilogin.config.login.login-timeout | Seconds an unauthenticated player may stay connected before `ultilogin.task.timeout-check` kicks them | config | `config/login.yml: login-timeout (default: 60)` | n/a | n/a | admin | brief | LoginService#checkTimeouts |
| ultilogin.config.login.max-register-per-ip | Maximum accounts one IP address may register (0 = unlimited) | config | `config/login.yml: max-register-per-ip (default: 3)` | n/a | n/a | admin | brief | LoginService#register |
| ultilogin.config.login.messages.account-locked | Message shown for a locked-out login attempt, with `{TIME}` substituted; hardcoded Chinese default, customizable, independent of `language` | config | `config/login.yml: messages.account-locked (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | LoginCommand#login |
| ultilogin.config.login.messages.admin.account-not-found | Message shown to an admin naming a player with no account, with `{PLAYER}` substituted | config | `config/login.yml: messages.admin.account-not-found (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | LoginAdminCommand#resetPassword |
| ultilogin.config.login.messages.admin.force-login | Message shown to an admin on a successful `/logadmin forcelogin`, with `{PLAYER}` substituted | config | `config/login.yml: messages.admin.force-login (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | LoginAdminCommand#forceLogin |
| ultilogin.config.login.messages.admin.password-reset | Message shown to an admin on a successful `/logadmin reset`, with `{PLAYER}`/`{PASSWORD}` substituted | config | `config/login.yml: messages.admin.password-reset (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | LoginAdminCommand#resetPassword |
| ultilogin.config.login.messages.admin.player-not-found | Message shown to an admin naming a player who is not online, with `{PLAYER}` substituted | config | `config/login.yml: messages.admin.player-not-found (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | LoginAdminCommand#forceLogin |
| ultilogin.config.login.messages.admin.unregister | Message shown to an admin on a successful `/logadmin unregister`, with `{PLAYER}` substituted | config | `config/login.yml: messages.admin.unregister (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | LoginAdminCommand#unregister |
| ultilogin.config.login.messages.already-logged | Message shown when a login/register is attempted while already logged in | config | `config/login.yml: messages.already-logged (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | LoginCommand#login |
| ultilogin.config.login.messages.already-registered | Message shown when `/register` is run by an already-registered player | config | `config/login.yml: messages.already-registered (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | RegisterCommand#register |
| ultilogin.config.login.messages.attempts-remaining | Message shown after a wrong password, with `{COUNT}` substituted with attempts remaining before lockout | config | `config/login.yml: messages.attempts-remaining (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | LoginCommand#login |
| ultilogin.config.login.messages.gui-password-invalid | Message shown for a GUI-mode password rejected by validation, with `{LENGTH}` substituted | config | `config/login.yml: messages.gui-password-invalid (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | LoginService#getPasswordValidationError |
| ultilogin.config.login.messages.login-prompt | Text-mode login prompt shown on join/blocked-action for a registered, unauthenticated player | config | `config/login.yml: messages.login-prompt (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | LoginService#onPlayerJoin |
| ultilogin.config.login.messages.login-prompt-gui | GUI-mode login prompt text shown on the same trigger as `messages.login-prompt`, before the GUI itself opens | config | `config/login.yml: messages.login-prompt-gui (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | LoginService#onPlayerJoin |
| ultilogin.config.login.messages.login-success | Message shown on successful `/login` | config | `config/login.yml: messages.login-success (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | LoginCommand#login |
| ultilogin.config.login.messages.not-registered | Message shown when `/login` is run by an unregistered player | config | `config/login.yml: messages.not-registered (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | LoginCommand#login |
| ultilogin.config.login.messages.password-mismatch | Message shown when `/register`'s or `/logadmin reset`'s two password entries do not match | config | `config/login.yml: messages.password-mismatch (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | RegisterCommand#register |
| ultilogin.config.login.messages.password-too-long | Message shown for a command-mode password exceeding `password.max-length`, with `{MAX}` substituted | config | `config/login.yml: messages.password-too-long (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | LoginService#getPasswordValidationError |
| ultilogin.config.login.messages.password-too-short | Message shown for a command-mode password below `password.min-length`, with `{MIN}` substituted | config | `config/login.yml: messages.password-too-short (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | LoginService#getPasswordValidationError |
| ultilogin.config.login.messages.register-prompt | Text-mode registration prompt shown on join/blocked-action for an unregistered player | config | `config/login.yml: messages.register-prompt (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | LoginService#onPlayerJoin |
| ultilogin.config.login.messages.register-prompt-gui | GUI-mode registration prompt text shown on the same trigger as `messages.register-prompt` | config | `config/login.yml: messages.register-prompt-gui (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | LoginService#onPlayerJoin |
| ultilogin.config.login.messages.register-success | Message shown on successful `/register` (both text and GUI mode) | config | `config/login.yml: messages.register-success (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | RegisterCommand#register |
| ultilogin.config.login.messages.timeout-kick | Kick message shown to a player removed by `ultilogin.task.timeout-check` | config | `config/login.yml: messages.timeout-kick (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | LoginService#checkTimeouts |
| ultilogin.config.login.messages.wrong-password | Declared and validated, but never read by any production code — `LoginService#login`'s wrong-password branch always returns either `messages.attempts-remaining` or `messages.account-locked` (the latter even when `security.max-login-attempts` is configured as unlimited, since `getRemainingAttempts()` then returns `-1`, which fails the `remaining > 0` check the same way `0` does), never this key. A declared-but-dead key with no observable effect, editing it changes nothing an operator can see | config | `config/login.yml: messages.wrong-password (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters, has no effect)` | n/a | n/a | admin | none | LoginService#login (declared, never read outside this class) |
| ultilogin.config.login.password.max-length | Command-mode maximum password length | config | `config/login.yml: password.max-length (default: 32)` | n/a | n/a | admin | brief | LoginService#isPasswordValid |
| ultilogin.config.login.password.min-length | Command-mode minimum password length | config | `config/login.yml: password.min-length (default: 6)` | n/a | n/a | admin | brief | LoginService#isPasswordValid |
| ultilogin.config.login.security.lockout-duration | Seconds an IP/UUID stays locked out after exceeding `security.max-login-attempts` | config | `config/login.yml: security.lockout-duration (default: 900)` | n/a | n/a | admin | brief | LoginService#recordFailedAttempt |
| ultilogin.config.login.security.lockout-type | Whether a lockout is keyed by `IP`, `UUID`, or `BOTH` | config | `config/login.yml: security.lockout-type (default: "IP")` | n/a | n/a | admin | detailed | LoginService#isLocked |
| ultilogin.config.login.security.max-login-attempts | Wrong-password attempts allowed before lockout (0 = unlimited) | config | `config/login.yml: security.max-login-attempts (default: 5)` | n/a | n/a | admin | brief | LoginService#recordFailedAttempt |
| ultilogin.config.login.session-enabled | Whether a successful login is remembered per `ip:uuid` so a reconnect within `session-timeout` auto-logs in — see `ultilogin.session.not-persisted` for the in-memory-only caveat | config | `config/login.yml: session-enabled (default: true)` | n/a | n/a | admin | brief | LoginService#hasValidSession |
| ultilogin.config.login.session-timeout | Minutes a remembered session stays valid | config | `config/login.yml: session-timeout (default: 30)` | n/a | n/a | admin | brief | LoginService#hasValidSession |
| ultilogin.config.login.spawn-location.enabled | Whether an unauthenticated player is teleported to a fixed spawn location (and returned to their original location on successful login) | config | `config/login.yml: spawn-location.enabled (default: false)` | n/a | n/a | admin | brief | LoginService#applyNoSessionProtections |
| ultilogin.config.login.spawn-location.world | World name for the unauthenticated-player spawn teleport | config | `config/login.yml: spawn-location.world (default: "world")` | n/a | n/a | admin | none | LoginService#applyNoSessionProtections |
| ultilogin.config.login.spawn-location.x | X coordinate for the unauthenticated-player spawn teleport | config | `config/login.yml: spawn-location.x (default: 0)` | n/a | n/a | admin | none | LoginService#applyNoSessionProtections |
| ultilogin.config.login.spawn-location.y | Y coordinate for the unauthenticated-player spawn teleport | config | `config/login.yml: spawn-location.y (default: 64)` | n/a | n/a | admin | none | LoginService#applyNoSessionProtections |
| ultilogin.config.login.spawn-location.z | Z coordinate for the unauthenticated-player spawn teleport | config | `config/login.yml: spawn-location.z (default: 0)` | n/a | n/a | admin | none | LoginService#applyNoSessionProtections |
| ultilogin.config.login.ulticloud.enabled | Whether `/panel` may generate a UltiCloud magic-link. Declared and read correctly (`LoginService#isPanelEnabled`) — not a defect — but note this key's name and its config file's own `EmailConfig`-adjacent section header make it easy to mistake for an email setting; it gates the panel path only | config | `config/login.yml: ulticloud.enabled (default: false)` | n/a | n/a | admin | brief | LoginService#isPanelEnabled |
