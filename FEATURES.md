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
  feature — for every Kind, `config` included: all 51 `config` rows below cite the reading
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
`@EventListener` = 2 (classes), `@EventHandler` = 25 (handler methods: 21 in
`LoginProtectionListener`, 4 in `LoginProtectionPaperListener`; 1 class / 15 methods before
UltiKits/UltiLogin#24), `@Scheduled` = 2,
`@ConditionalOnConfig` = 0, `@ConfigEntity` = 2 (classes), `@ConfigEntry` = 51 (43 on
`LoginConfig`, 8 on `EmailConfig`; 52 and 44 before UltiKits/UltiLogin#23 removed
`messages.wrong-password`) — confirmed by reading every one of the 18 source files
directly, not by trusting the count alone. `LoginAdminCommand`'s six `@CmdMapping` sites
(`reset <player>` at line 44, `reset <player> <password>` at line 73, `forcelogin <player>` at
line 111, `unregister <player>` at line 145, `info <player>` at line 169, and the bare `""` at
line 200) are this module's standing positive control — the class with the most sub-commands
behind one executor, the shape most likely to silently drop a row under a naive approach. This
document's command-row count (20) diverges from the `@CmdMapping` count (17) for two explained
reasons, stated in the `## Email Binding` and `## Password Recovery`/`## Panel` sections below.

## Login

`LoginCommand` — class-level `@CmdExecutor(alias = {"login", "l"})` (its `description` is the language key `command_login_description`, so it follows `language`),
`@CmdTarget(PLAYER)`. Two `@CmdMapping` sites: `<password>` (the real login attempt) and the bare
`""` (dispatches to `#help`, which itself calls `#handleHelp`). `/login help` (the literal word)
reaches the identical `#handleHelp` body through the framework's own short-circuit ahead of
`matchMethod` — the same mechanism the framework's own `FEATURES.md` documents for `/upm help` —
so both invocation styles are one row here, not two.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.login.authenticate | Authenticate a registered, currently-unauthenticated player against their stored password hash; locks the account/IP after too many wrong attempts (see `## Player Protection`'s security-lockout config rows). A wrong password is answered with `messages.account-locked` only when that attempt actually recorded a lock (the one that reaches `security.max-login-attempts` under `security.lockout-type` `IP`, `UUID` or `BOTH`); otherwise with the attempts remaining while that count is above zero, and with the language catalogue's `wrong_password` text when it is not — which is every wrong password under `security.max-login-attempts: 0`, and every one from the limit onwards under an unrecognised `lockout-type`, which locks nothing (UltiKits/UltiLogin#23, #37) | command | `/login <password>` (alias `/l`) | none | player | player | brief | LoginCommand#login |
| ultilogin.login.help | Print `/login` usage from the language file's `help_login`, so it follows `language` (UltiKits/UltiLogin#20) | command | bare `/login` or literal `/login help` | none | player | player | none | LoginCommand#handleHelp |

## Register

`RegisterCommand` — class-level `@CmdExecutor(alias = {"register", "reg"}, description =
"command_register_description")` (a language key, so it follows `language`), `@CmdTarget(PLAYER)`. Same help short-circuit note as `## Login` above applies
identically here.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.register.create | Create a new account for an unregistered player, subject to the per-IP registration cap, password-mode validation (GUI: exact-length digits; command: length range), and a password/confirm match check; auto-logs the new account in on success | command | `/register <password> <confirm>` (alias `/reg`) | none | player | player | brief | RegisterCommand#register |
| ultilogin.register.help | Print `/register` usage, with a mode-dependent second line (GUI: required digit count, `help_password_digits`; command: min/max length, `help_password_length`) — both lines from the language file (`help_register` first), so they follow `language` (UltiKits/UltiLogin#20) | command | bare `/register` or literal `/register help` | none | player | player | none | RegisterCommand#handleHelp |

## Change Password

`ChangePasswordCommand` — class-level `@CmdExecutor(alias = {"changepassword", "changepw", "cpw"},
permission = "ultilogin.changepassword")` (its `description` is the language key `command_changepassword_description`, so it follows `language`), `@CmdTarget(PLAYER)`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.changepassword.update | Change a currently-authenticated player's own password, verifying the old one first; on success this invalidates every session for the account (see `## Player Protection`'s session rows) and force-revokes the caller's own active login state, immediately re-presenting the credential prompt (F-L1, fixed 13-uat-results.md #14) | command | `/changepassword <old> <new> <confirm>` (aliases `/changepw`, `/cpw`) | ultilogin.changepassword | player | player | detailed | ChangePasswordCommand#changePassword |
| ultilogin.changepassword.help | Print `/changepassword` usage (`help_change_password`), with the same mode-dependent second line as `/register`; the command's own replies — not logged in (`please_login_first`), new passwords differ (`change_password_mismatch`), changed (`change_password_success`), wrong old password (`change_password_wrong`) — also come from the language file, so all of it follows `language` (UltiKits/UltiLogin#20) | command | bare `/changepassword` or literal `/changepassword help` | ultilogin.changepassword | player | player | none | ChangePasswordCommand#handleHelp |

## Email Binding

`EmailBindCommand` — class-level `@CmdExecutor(alias = {"regs"}, permission = "ultilogin.email",
description = "command_regs_description")` (a language key, so it follows `language`). Both `@CmdMapping` methods carry their own `@CmdTarget(PLAYER)`
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
"ultilogin.recover")` (its `description` is the language key `command_recover_description`, so it follows `language`). Both `@CmdMapping` methods carry their own
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
"ultilogin.admin")` (its `description` is the language key `command_logadmin_description`, so it follows `language`), `@CmdTarget(BOTH)`. Six `@CmdMapping`
sites, all documented in full below (this module's standing positive control, see the
reconciliation note above): `reset <player>` (line 44), `reset <player> <password>` (line 73),
`forcelogin <player>` (line 111), `unregister <player>` (line 145), `info <player>` (line 169),
bare `""` (line 200, dispatches to `#help` → `#handleHelp`).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.admin.force-login | Log a currently-online, registered, not-yet-authenticated player in without their password | command | `/logadmin forcelogin <player>` | ultilogin.admin | both | admin | brief | LoginAdminCommand#forceLogin |
| ultilogin.admin.help | Print the `/logadmin` sub-command list from the language file (`help_admin_header`, `help_admin_reset`, `help_admin_forcelogin`, `help_admin_unregister`, `help_admin_info`), so it follows `language` (UltiKits/UltiLogin#20) | command | bare `/logadmin` or literal `/logadmin help` | ultilogin.admin | both | admin | none | LoginAdminCommand#handleHelp |
| ultilogin.admin.info | Show a named player's full account record: name, UUID, registration/last IP, login count, bound email and its verification state, registration and last-login timestamps — every label and the not-bound / verified / not-verified values come from the language file's `info_*` keys, so they follow `language` (UltiKits/UltiLogin#20) | command | `/logadmin info <player>` | ultilogin.admin | both | admin | brief | LoginAdminCommand#showInfo |
| ultilogin.admin.reset-password | Reset a named player's password to an admin-supplied value, after the same password-mode validation `## Register` uses | command | `/logadmin reset <player> <password>` | ultilogin.admin | both | admin | brief | LoginAdminCommand#resetPasswordWithValue |
| ultilogin.admin.reset-random | Reset a named player's password to a freshly-generated random value (numeric in GUI mode, alphanumeric in command mode) and report it in chat | command | `/logadmin reset <player>` | ultilogin.admin | both | admin | brief | LoginAdminCommand#resetPassword |
| ultilogin.admin.unregister | Permanently delete a named player's account record | command | `/logadmin unregister <player>` | ultilogin.admin | both | admin | brief | LoginAdminCommand#unregister |

## Panel

`PanelCommand` — class-level `@CmdExecutor(alias = {"panel"}, description =
"command_panel_description")` (a language key, so it follows `language`), `@CmdTarget(PLAYER)`. One `@CmdMapping(format = "")` site (`#openPanel`) — the bare
invocation is the real feature (unlike `## Login`/`## Register`/etc.), so `/panel help` (the
literal word) reaches `#handleHelp` only through the framework's short-circuit, with no
`@CmdMapping` site of its own — the same shape as `## Password Recovery`'s own extra row.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.panel.help | Print `/panel` usage from the language file's `help_panel`, so it follows `language` (UltiKits/UltiLogin#20) | command | literal `/panel help` | none | player | player | none | PanelCommand#handleHelp |
| ultilogin.panel.open | Gated behind `ulticloud.enabled`; on success sends an async-generated, clickable UltiCloud magic-link URL and starts polling for authentication completion. Every stage is fenced against a credential change (admin reset/unregister, self password change) racing the async HTTP call or the poll itself, keyed on a per-player invalidation generation and, once published, the exact request id — layered fixes from Codex PR UltiLogin#18 rounds 4, 5, 7, 9 and 11 | command | `/panel` | none | player | player | detailed | PanelCommand#openPanel |

## Player Protection

`LoginProtectionListener` plus `LoginProtectionPaperListener` — two `@EventListener`-annotated classes
(`grep -c '@EventListener'` confirms this: exactly 2), registering 25 `@EventHandler` methods between
them (21 and 4), all at `EventPriority.LOWEST` except `#onPlayerQuit`. This section groups the 25
handler methods into 8 rows, one row per
distinct guarded behaviour rather than one row per Bukkit event type, following the same "one row
per handler, bundled by behaviour" convention the framework's own `FEATURES.md` and UltiChat's
`FEATURES.md` (`ChatListener#onChat`) already apply — ten of the twenty-five handlers
(`onBlockBreak`/`onBlockPlace`/`onPlayerInteract`/`onPlayerInteractEntity`/
`onPlayerInteractAtEntity`/`onPlayerArmorStandManipulate`/`onPlayerSwapHandItems`/
`onPlayerEditBook`/`onSignChange`/`onPlayerDropItem`) share the byte-identical single-line guard
body (`cancelIfNotLoggedIn`); `onPlayerPickupItem` is the eleventh member of the same group but adds
the `instanceof Player` narrowing that `EntityPickupItemEvent`'s signature forces, and `onSignChange`
adds a null guard (gate 1 IN-09), so neither is byte-identical — and
two more (`onPlayerDamage`/`onPlayerDamageEntity`) are the symmetric halves of one property (an
unauthenticated player can neither take nor deal damage) — bundling these does not lose any
distinguishable behaviour a reconciliation reviewer could find missing. The reconciliation table's
`@EventHandler` line (25) and this section's row count (8) are reconciled by this paragraph, not
by a 1:1 count. UltiKits/UltiLogin#24 raised the handler count from 15 to 25 without adding a row:
all ten went into `ultilogin.protection.world-interaction-block` and
`ultilogin.protection.inventory-block`, whose Feature cells name them.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.protection.chat-block | Cancel an unauthenticated player's chat message and re-send the login/register prompt (text or GUI, per `gui-mode.enabled`) | event | send a chat message while not logged in | n/a | n/a | player | brief | LoginProtectionListener#onPlayerChat |
| ultilogin.protection.command-block | Cancel any command an unauthenticated player runs that is not on the `allowed-commands` whitelist, and re-send the login/register prompt | event | run a non-whitelisted command while not logged in | n/a | n/a | player | brief | LoginProtectionListener#onPlayerCommand |
| ultilogin.protection.damage-block | Cancel damage taken by an unauthenticated player (`onPlayerDamage`), and separately cancel damage dealt by an unauthenticated attacker against any target (`onPlayerDamageEntity`) — two independent guards on the two ends of a damage event | event | be damaged, or attack another entity, while not logged in | n/a | n/a | player | brief | LoginProtectionListener#onPlayerDamage, LoginProtectionListener#onPlayerDamageEntity |
| ultilogin.protection.inventory-block | Cancel inventory open/click for an unauthenticated player unless the inventory's title equals one of the three resolved credential-GUI titles, colour codes applied as the pages apply them — `gui-mode.title-login`, `gui-mode.title-register`, `gui-mode.title-confirm`, as `login.yml` holds them (`LoginProtectionListener#onInventoryClick`, `#onInventoryOpen`). This is a title comparison, not a type check against `LoginGUIPage`/`RegisterGUIPage`, and the title covers the whole view, including the player's own inventory rows (UltiKits/UltiLogin#35). It replaced a check for three Chinese substrings meaning "password", "login" and "register", which refused every keypad click under a title without them — any English title — and allowed clicks in another inventory whose title held one (UltiKits/UltiLogin#20). Inventory **drags** are also cancelled, added by UltiKits/UltiLogin#24's sweep (`InventoryDragEvent` is a sibling of `InventoryClickEvent`, declaring its own `HandlerList`, so the click handler never received one) — **defensively: no reachable drag bypass was demonstrated**, because a drag needs a cursor that the click guard refuses to load with `gui-mode.enabled: false`, and obliviate-invs cancels every drag while one of its GUIs is open. It is cancelled deliberately **without** the title allowance: a drag cannot enter a digit into the credential GUI, and that GUI's view includes the player's own inventory rows, so allowing drags inside it would let an unauthenticated player rearrange their items and push stacks toward its container slots | event | open, click, or drag in any inventory while not logged in | n/a | n/a | player | detailed | LoginProtectionListener#onInventoryClick, LoginProtectionListener#onInventoryDrag, LoginProtectionListener#onInventoryOpen |
| ultilogin.protection.join | On join: mark the player unauthenticated, record their pre-login location, auto-login if a valid session exists for their `ip:uuid` (announcing the language file's `session_login`, so it follows `language`, UltiKits/UltiLogin#20), otherwise apply the configured blindness effect and spawn-location teleport and send the login/register prompt — opening `LoginGUIPage`/`RegisterGUIPage` after a 20-tick delay when `gui-mode.enabled`, or the equivalent text prompt otherwise | event | join the server | n/a | n/a | player | detailed | LoginProtectionListener#onPlayerJoin |
| ultilogin.protection.movement-block | Cancel an unauthenticated player's block-to-block movement (looking around in place is deliberately allowed — only a change to `getBlockX`/`Y`/`Z` is reverted) | event | attempt to move while not logged in | n/a | n/a | player | brief | LoginProtectionListener#onPlayerMove |
| ultilogin.protection.quit | Clear the quitting player's in-memory login/join-time/original-location state and cancel any in-flight `/panel` polling task for them | event | quit the server | n/a | n/a | internal | none | LoginProtectionListener#onPlayerQuit |
| ultilogin.protection.world-interaction-block | Cancel block-break, block-place, block/air-interact, entity-interact, precise-position entity-interact ("interact at"), armor-stand equip/unequip, off-hand swap, book-write, sign-write, item-drop and item-pickup for an unauthenticated player — eleven handlers sharing the identical `cancelIfNotLoggedIn` guard body. The last five were added by UltiKits/UltiLogin#24: Bukkit dispatches an event on the `HandlerList` of the nearest class that *declares* `getHandlerList()`, so `PlayerInteractAtEntityEvent` and `PlayerArmorStandManipulateEvent` (siblings, both declaring their own list and both extending `PlayerInteractEntityEvent` directly) never reached the entity-interact handler, and `PlayerSwapHandItemsEvent` was preceded by no handled event at all. Book-writing is **not** defensive either, though an earlier revision of this row said it was: the premise was that the client opens that editor only after a server packet following an uncancelled `PlayerInteractEvent`, and the wave-1 real-machine run of `ultilogin.protection.world-interaction-block` measured it and disproved it. An unauthenticated player right-clicking a writable book at air gets the editor, and the edit packet then reaches `PlayerEditBookEvent` on its own — `handleEditBook`'s three earlier refusal sites do not apply (book-size and rate-limit *disconnect* rather than refuse silently, the hotbar-slot check passes, and `signBook`'s `has(WRITABLE_BOOK_CONTENT)` guard passes on a book with no NBT because `Items.WRITABLE_BOOK` registers that component as a default). `#onPlayerEditBook` is therefore the only thing refusing the write, and it must not be deleted as unreachable. **Known limitation, deliberate:** the book editor itself opens for an unauthenticated player and cannot be prevented server-side. The server's `Player#openItemGui` body is empty — the client opens that screen itself while predicting the item use, so the server never opens, sends or observes it — and `paper-api` 1.21.11 carries no book-editor-open event to cancel (seven book events, none an open), unlike the sign editor's `PlayerOpenSignEvent`. The consequence is bounded and is a worse experience rather than a weaker guarantee, exactly as in the sign case described next: the editor opens, the write and the signing are still refused, and the book stays a text-less `writable_book`. **Do not try to close it with a client-side suppression — there is no server-side hook to hang one on.** Sign-writing is **not** defensive: `PlayerSignOpenEvent`'s `Cause` enum includes `PLUGIN` and `HumanEntity#openSign(Sign, Side)` is public API, so another plugin can open a sign editor for an unauthenticated player with no interaction of theirs at all — that handler is the only thing refusing the write. **Known limitation, deliberate:** the sign editor is refused at the point it opens only for `io.papermc.paper.event.player.PlayerOpenSignEvent` (`LoginProtectionPaperListener#onPlayerOpenSign`). The API also carries an unrelated sibling type, `org.bukkit.event.player.PlayerSignOpenEvent` — both extend `PlayerEvent` directly and each declares its own `HandlerList`, so a handler for one is never delivered the other — and **which of the two a real server dispatches on a plugin-initiated open is not established**. It is therefore not handled, on purpose: the `org.bukkit` type is `@Deprecated(forRemoval = true)`, and a handler whose parameter type later disappears empties the *whole* listener's handler map, so adding one for an event that may never fire would be a declared protection that does not execute. The consequence is bounded and is a worse experience rather than a weaker guarantee: on such a server the editor opens for an unauthenticated player and the write is still refused by `#onSignChange`, so no sign text is written either way. **Do not add the second handler to close this — it is deferred pending the measurement in `UltiKits/UltiLogin#36`**, which also records where it would go if the measurement calls for it. Four more client intents in Paper's own event namespaces — middle-click item pick (one handler covers both `PlayerPickBlockEvent` and `PlayerPickEntityEvent`), held-item/equipment-slot swap, recipe-book click, and the sign-editor open above — are refused by **`LoginProtectionPaperListener`**, a separate class on purpose: Bukkit's `JavaPluginLoader#createRegisteredListeners` catches a `NoClassDefFoundError` from its `getDeclaredMethods()` call and returns an *empty* handler map for the whole listener, so one Paper-only event class missing at runtime would otherwise disable all of this row's core handlers. Which events belong to this set is declared as data in `LoginProtectionEventCoverageTest`, which scans the **whole** `paper-api` jar (449 event classes, not just the 279 under `org.bukkit.event`) and fails when a required handler is dropped, when either listener loses its `@EventListener`, or when any `paper-api` version adds another diverted subclass of anything handled here; the deliberately-uncovered list is keyed on the dispatch list rather than the class name, so one entry covers a whole event family — `PlayerTeleportEvent`'s list (which carries `PlayerTeleportEndGatewayEvent` too), `PlayerPortalEvent`'s, and `AsyncPlayerChatPreviewEvent`'s. Every handler runs at `EventPriority.LOWEST` with no `ignoreCancelled`, which is deliberate but means a plugin listening later can `setCancelled(false)` and undo any of them | event | break/place a block, interact with a block or entity, equip an armor stand, swap hands, write a book or a sign, drop, or pick up an item while not logged in | n/a | n/a | player | brief | LoginProtectionListener#onBlockBreak, LoginProtectionListener#onBlockPlace, LoginProtectionListener#onPlayerInteract, LoginProtectionListener#onPlayerInteractEntity, LoginProtectionListener#onPlayerInteractAtEntity, LoginProtectionListener#onPlayerArmorStandManipulate, LoginProtectionListener#onPlayerSwapHandItems, LoginProtectionListener#onPlayerEditBook, LoginProtectionListener#onSignChange, LoginProtectionListener#onPlayerDropItem, LoginProtectionListener#onPlayerPickupItem, LoginProtectionPaperListener#onPlayerPickItem, LoginProtectionPaperListener#onPlayerSwapWithEquipmentSlot, LoginProtectionPaperListener#onPlayerRecipeBookClick, LoginProtectionPaperListener#onPlayerOpenSign |

## GUI

Phase 9 excluded both classes below from this module's JaCoCo `check` gate (0/115 lines,
0/30 branches for `LoginGUIPage`; 0/138 lines, 0/38 branches for `RegisterGUIPage` —
`.planning/phases/09-module-ecosystem-readiness-and-test-coverage/gui-exclusions/UltiLogin.md`).
Both are 54-slot `Gui` pages presenting a numeric keypad; neither exists unless
`gui-mode.enabled` is `true`.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.gui.login-page | Numeric-keypad login page: nine digit buttons append to a masked password display, auto-submits once the configured digit count is reached, a clear button resets the input, an exit button closes the GUI and falls back to the text login prompt. Reopens itself after a 10-tick delay if the player is still registered-but-unauthenticated when it closes for any other reason (guarded against re-opening during a deliberate transition to `RegisterGUIPage`, and against stacking a second reopen on top of one `presentCredentialPrompt` already opened — Codex PR UltiLogin#18 rounds 9 and 10). Every item name and lore line comes from the language file (`gui_password_display`, `gui_entered`, `gui_login_confirm_button`, `gui_login_confirm_lore`, `gui_clear_button`, `gui_clear_lore`, `gui_exit_button`, `gui_login_exit_lore`, `gui_login_exit_warning`), so it follows `language` (UltiKits/UltiLogin#20) | gui | open when `gui-mode.enabled` and a registered, unauthenticated player joins or is re-prompted (see `ultilogin.protection.join`) | n/a | n/a | player | detailed | LoginGUIPage#onOpen |
| ultilogin.gui.register-page | Numeric-keypad registration page: two-phase entry (set password, then confirm), title changes between phases, a mismatch resets to phase one with a chat message, a match calls the same registration path as `/register`. Reopens itself after a 10-tick delay if the player is still unregistered when it closes for any other reason, with the identical transition/stacking guards as `LoginGUIPage` above. Every item name, lore line and chat line it sends itself comes from the language file (`gui_display_set`, `gui_display_confirm`, `gui_entered`, `gui_register_digits_hint`, `gui_register_repeat_hint`, `gui_confirm_button`, `gui_register_confirm_lore`, `gui_clear_button`, `gui_clear_lore`, `gui_exit_button`, `gui_register_exit_lore`, `gui_register_exit_warning`, `gui_register_confirm_prompt`, `register_failed`), so it follows `language` (UltiKits/UltiLogin#20) | gui | open when `gui-mode.enabled` and an unregistered player joins or is re-prompted (see `ultilogin.protection.join`) | n/a | n/a | player | detailed | RegisterGUIPage#onOpen |

## Scheduled Tasks

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.task.timeout-check | Every second, kick any unauthenticated player whose join (or last invalidation) exceeded `login-timeout` seconds ago, and separately sweep expired (>5 minute) pending `/panel` link requests | scheduled | runs automatically every 20 ticks (1s) while the server is up | n/a | n/a | internal | brief | LoginService#checkTimeouts |
| ultilogin.task.verification-cleanup | Every 60 seconds, discard pending email-bind and password-recovery verification entries older than `verification.code-expiry-seconds`, and discard resend-cooldown timestamps older than a fixed 10-minute TTL | scheduled | runs automatically every 1200 ticks (60s) while the server is up | n/a | n/a | internal | none | EmailVerificationService#cleanupExpired |

## Lifecycle Hooks

This module overrides neither framework template method (`reloadSelf`, `unregisterSelf`) and declares no
`onUnregister()` hook (UltiKits/UltiLogin#29); its one `onReload()` hook is described below.
`/ul reload UltiLogin` (or a bare `/ul reload`, which reloads every module) runs UltiTools' own final `reloadSelf()`, whose first step
(`ConfigManager#reloadConfigs`) re-initialises, in place, the same `LoginConfig` instance the container
injected into `LoginService`; the module's one `onReload()` hook, added by UltiKits/UltiLogin#23, then runs
the removed-key check of `ultilogin.lifecycle.removed-key-warning` and the text-default rewrite of
`ultilogin.lifecycle.legacy-text-defaults` (both also run when the module is enabled). All three rows below are
`event`-Kind with no `@EventHandler` site behind them: module enable and reload are framework-invoked
lifecycle steps, not commands this repository maps or config keys of its own, so neither is counted in the
reconciliation table's `@EventHandler` line (25).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.lifecycle.legacy-text-defaults | When the module is enabled and again on every reload of it (`UltiLogin#registerSelf`, `#onReload`, both after the framework has read `config/login.yml` and loaded the language — never from a configuration change listener, which the framework fires before it reloads the language): each of the three `gui-mode.title-*` values and the twenty-one `messages.*` values that is still built-in text — the Chinese default an earlier version shipped, or its colour code plus this jar's English or Chinese text for it (read from the module jar, never from the language files on disk) — and differs from the current text is replaced with its colour code plus the language file's text in the server's language, and the file is saved once. An untouched value therefore follows a `language` switch in both directions; a value that differs in any way, even by one character, is the operator's and is kept byte for byte; a text that would break the setting's `@NotEmpty` is never written; a second start with the same language writes nothing. A single-module `/ul reload UltiLogin` does not re-read the framework's `language`, so a changed `language` is picked up on a bare `/ul reload` or a restart. A failed save is logged as one warning from the language file (`log_config_default_save_failed`) and costs nothing else (UltiKits/UltiLogin#20) | event | module enable and every reload of it: bare `/ul reload`, which reloads every module, or `/ul reload UltiLogin` | n/a | n/a | admin | brief | UltiLogin#registerSelf, UltiLogin#onReload, LoginConfig#materializeText, ConfigTextDefaults |
| ultilogin.lifecycle.reload | `/ul reload UltiLogin` re-reads `config/login.yml` into the running module, so an edited `allowed-commands` list governs the very next command an unauthenticated player runs, in both directions (an added entry is permitted, a removed one is refused again), without a restart; this module adds no reload work of its own beyond the removed-key check of `ultilogin.lifecycle.removed-key-warning`, and prints no reload line of its own. A regression guard for UltiKits/UltiLogin#13, not a changed behaviour: before UltiKits/UltiLogin#29 the module's reload override already called the framework's reload first | event | `/ul reload UltiLogin` (framework calls `reloadSelf()`, which reloads configuration, refreshes language, reports `@ConditionalOnConfig` drift and logs its own per-module line) | n/a | n/a | admin | brief | LoginService#isCommandAllowed |
| ultilogin.lifecycle.removed-key-warning | When the module is enabled and again on every reload of it (`/ul reload` or `/ul reload UltiLogin`), read the operator's own `config/login.yml` and, for each key this version no longer reads that is still in it, log one console WARNING naming the file, the key, where the setting went and that the key can be deleted. The one such key is `messages.wrong-password`, removed by UltiKits/UltiLogin#23 (its text is the language catalogue's `wrong_password` now); the framework writes a declared default only for a missing key and never deletes one, so every server that ran an earlier version still has it. A missing or unparseable file produces no warning, and an error inside the check itself is logged as one warning and never stops the module enabling or reloading. The file checked is the one `LoginConfig` binds (`LoginConfig#CONFIG_FILE`), not a second copy of its path. The warning and its guidance come from the language file (`removed_key_warning`, `removed_key_reason_wrong_password`), and so does the line for a failed check (`log_removed_key_check_failed`), so they follow `language` (UltiKits/UltiLogin#20) | event | module enable (server start, or loading the module) and every reload of it: bare `/ul reload`, which reloads every module, or `/ul reload UltiLogin` | n/a | n/a | admin | brief | UltiLogin#registerSelf, UltiLogin#onReload, RemovedConfigKeys#warnAboutLeftovers |

## Data persistence

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.account.restart-survival | An account's password hash/salt, registration/last IP, login count, and bound-email state (`login_accounts` table, `@Table`-backed) survive a full server restart | persistence | register or change credentials, then restart the server | n/a | n/a | admin | none | AccountData, LoginService#getAccount |
| ultilogin.session.not-persisted | The IP-keyed session map (`session-enabled`'s backing store) is an in-memory `ConcurrentHashMap` only — never written to disk anywhere in this module's source. A full server restart discards every session regardless of how much of `session-timeout` remained, so every previously-authenticated player must log in again after a restart even within their session window | persistence | log in with `session-enabled: true`, then restart the server before the session would have expired, then rejoin | n/a | n/a | admin | brief | LoginService#sessions |

## Configuration

Every `@ConfigEntry`-annotated field across this module's two `@ConfigEntity` classes (51 keys
total: `LoginConfig` 43, `EmailConfig` 8 — matching the reconciliation table's own `@ConfigEntry`
count of 51 exactly). Several of these keys already have a behavioural row above (login/register/
change-password validation, the security lockout, the GUI gate, the two scheduled tasks) — that
row documents the *feature* the key drives, this row documents the *key* itself, at file-and-key
granularity, so the reconciliation table can prove every key is accounted for without also making
every behavioural row carry a `config` Kind.

**One `messages.*` key is read by some command paths and not by another whose name it resembles:
`messages.password-mismatch` is read by `/register` and the GUI register page but never by
`/logadmin reset`, as its own row below states. A second key this paragraph used to count,
`messages.wrong-password`, was never read by anything and is removed (UltiKits/UltiLogin#23);
while an operator's `login.yml` still holds it, `ultilogin.lifecycle.removed-key-warning` reports
it.**

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
| ultilogin.config.login.allowed-commands | Commands (without the leading `/`) an unauthenticated player may still run. `LoginService#isCommandAllowed` lowercases the first token and strips only the leading `/` — it does NOT strip a namespace prefix, unlike the framework's own remote command blocklist: `/ultilogin:login password` compares as the literal string `ultilogin:login`, which never matches a bare `login` entry, so a namespaced invocation of an otherwise-allowed command is refused | config | `config/login.yml: allowed-commands (default: [login, l, register, reg, panel, regs, recover])` | n/a | n/a | admin | detailed | LoginService#isCommandAllowed |
| ultilogin.config.login.blind-effect | Whether an unauthenticated player is given an infinite, undiminishing blindness potion effect | config | `config/login.yml: blind-effect (default: true)` | n/a | n/a | admin | brief | LoginService#applyNoSessionProtections |
| ultilogin.config.login.gui-mode.enabled | Whether the numeric-keypad GUI pages (`LoginGUIPage`/`RegisterGUIPage`) are used instead of the plain-text command flow | config | `config/login.yml: gui-mode.enabled (default: false)` | n/a | n/a | admin | detailed | LoginProtectionListener#onPlayerJoin |
| ultilogin.config.login.gui-mode.password-length | Required digit count for a GUI-mode password (1-9 digits) | config | `config/login.yml: gui-mode.password-length (default: 4)` | n/a | n/a | admin | brief | LoginService#isPasswordValid |
| ultilogin.config.login.gui-mode.title-confirm | GUI title shown during the register page's password-confirmation phase. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `gui_confirm_password` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: gui-mode.title-confirm (default: written in the server's language; under language: en '&6Confirm Password')` | n/a | n/a | admin | none | RegisterGUIPage#processInput |
| ultilogin.config.login.gui-mode.title-login | GUI title shown on `LoginGUIPage`. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `gui_enter_password` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: gui-mode.title-login (default: written in the server's language; under language: en '&6Enter Password')` | n/a | n/a | admin | none | LoginGUIPage#LoginGUIPage |
| ultilogin.config.login.gui-mode.title-register | GUI title shown on `RegisterGUIPage`'s first phase. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `gui_set_password` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: gui-mode.title-register (default: written in the server's language; under language: en '&6Set Password')` | n/a | n/a | admin | none | RegisterGUIPage#RegisterGUIPage |
| ultilogin.config.login.login-timeout | Seconds an unauthenticated player may stay connected before `ultilogin.task.timeout-check` kicks them | config | `config/login.yml: login-timeout (default: 60)` | n/a | n/a | admin | brief | LoginService#checkTimeouts |
| ultilogin.config.login.max-register-per-ip | Maximum accounts one IP address may register (0 = unlimited) | config | `config/login.yml: max-register-per-ip (default: 3)` | n/a | n/a | admin | brief | LoginService#register |
| ultilogin.config.login.messages.account-locked | Message shown for a locked-out login attempt, with `{TIME}` substituted. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `account_locked` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.account-locked (default: written in the server's language; under language: en '&cToo many failed login attempts! Please try again in {TIME} seconds.')` | n/a | n/a | admin | brief | LoginCommand#login |
| ultilogin.config.login.messages.admin.account-not-found | Message shown to an admin naming a player with no account, with `{PLAYER}` substituted. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `admin_account_not_found` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.admin.account-not-found (default: written in the server's language; under language: en '&cPlayer {PLAYER} is not registered')` | n/a | n/a | admin | none | LoginAdminCommand#resetPassword |
| ultilogin.config.login.messages.admin.force-login | Message shown to an admin on a successful `/logadmin forcelogin`, with `{PLAYER}` substituted. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `admin_force_login` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.admin.force-login (default: written in the server's language; under language: en '&aForce logged in player {PLAYER}')` | n/a | n/a | admin | none | LoginAdminCommand#forceLogin |
| ultilogin.config.login.messages.admin.password-reset | Message shown to an admin on a successful `/logadmin reset`, with `{PLAYER}`/`{PASSWORD}` substituted. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `admin_password_reset` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.admin.password-reset (default: written in the server's language; under language: en '&aReset password for player {PLAYER} to: {PASSWORD}')` | n/a | n/a | admin | none | LoginAdminCommand#resetPassword |
| ultilogin.config.login.messages.admin.player-not-found | Message shown to an admin naming a player who is not online, with `{PLAYER}` substituted. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `admin_player_not_found` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.admin.player-not-found (default: written in the server's language; under language: en '&cPlayer {PLAYER} not found')` | n/a | n/a | admin | none | LoginAdminCommand#forceLogin |
| ultilogin.config.login.messages.admin.unregister | Message shown to an admin on a successful `/logadmin unregister`, with `{PLAYER}` substituted. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `admin_unregister` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.admin.unregister (default: written in the server's language; under language: en '&aDeleted account for player {PLAYER}')` | n/a | n/a | admin | none | LoginAdminCommand#unregister |
| ultilogin.config.login.messages.already-logged | Message shown when a login/register is attempted while already logged in. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `already_logged` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.already-logged (default: written in the server's language; under language: en '&eYou are already logged in!')` | n/a | n/a | admin | brief | LoginCommand#login |
| ultilogin.config.login.messages.already-registered | Message shown when `/register` is run by an already-registered player. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `already_registered` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.already-registered (default: written in the server's language; under language: en '&cYou are already registered! Please login.')` | n/a | n/a | admin | brief | RegisterCommand#register |
| ultilogin.config.login.messages.attempts-remaining | Message shown after a wrong password, with `{COUNT}` substituted with attempts remaining before lockout. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `attempts_remaining` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.attempts-remaining (default: written in the server's language; under language: en '&cWrong password! Remaining attempts: {COUNT}')` | n/a | n/a | admin | brief | LoginCommand#login |
| ultilogin.config.login.messages.gui-password-invalid | Message shown for a GUI-mode password rejected by validation, with `{LENGTH}` substituted. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `gui_password_invalid` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.gui-password-invalid (default: written in the server's language; under language: en '&cPassword must be {LENGTH} digits!')` | n/a | n/a | admin | none | LoginService#getPasswordValidationError |
| ultilogin.config.login.messages.login-prompt | Text-mode login prompt shown on join/blocked-action for a registered, unauthenticated player. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `login_prompt` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.login-prompt (default: written in the server's language; under language: en '&ePlease use /login <password> to login')` | n/a | n/a | admin | brief | LoginService#onPlayerJoin |
| ultilogin.config.login.messages.login-prompt-gui | GUI-mode login prompt text shown on the same trigger as `messages.login-prompt`, before the GUI itself opens. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `login_prompt_gui` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.login-prompt-gui (default: written in the server's language; under language: en '&ePlease enter your password in the popup')` | n/a | n/a | admin | none | LoginService#onPlayerJoin |
| ultilogin.config.login.messages.login-success | Message shown on successful `/login`. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `login_success` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.login-success (default: written in the server's language; under language: en '&aLogin successful! Welcome back!')` | n/a | n/a | admin | brief | LoginCommand#login |
| ultilogin.config.login.messages.not-registered | Message shown when `/login` is run by an unregistered player. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `not_registered` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.not-registered (default: written in the server's language; under language: en '&cYou are not registered! Please register first.')` | n/a | n/a | admin | brief | LoginCommand#login |
| ultilogin.config.login.messages.password-mismatch | Message shown when `/register`'s two password entries do not match; also read by `RegisterGUIPage#processInput` when the GUI-mode confirmation phase does not match the first phase. `/logadmin reset` has no confirmation parameter at all (its two mappings take either one generated password or one admin-supplied password) and never reads this key. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `password_mismatch` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.password-mismatch (default: written in the server's language; under language: en '&cPasswords do not match!')` | n/a | n/a | admin | brief | RegisterCommand#register, RegisterGUIPage#processInput |
| ultilogin.config.login.messages.password-too-long | Message shown for a command-mode password exceeding `password.max-length`, with `{MAX}` substituted. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `password_too_long` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.password-too-long (default: written in the server's language; under language: en '&cPassword too long! Maximum {MAX} characters allowed.')` | n/a | n/a | admin | none | LoginService#getPasswordValidationError |
| ultilogin.config.login.messages.password-too-short | Message shown for a command-mode password below `password.min-length`, with `{MIN}` substituted. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `password_too_short` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.password-too-short (default: written in the server's language; under language: en '&cPassword too short! At least {MIN} characters required.')` | n/a | n/a | admin | none | LoginService#getPasswordValidationError |
| ultilogin.config.login.messages.register-prompt | Text-mode registration prompt shown on join/blocked-action for an unregistered player. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `register_prompt` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.register-prompt (default: written in the server's language; under language: en '&ePlease use /register <password> <confirm> to register')` | n/a | n/a | admin | brief | LoginService#onPlayerJoin |
| ultilogin.config.login.messages.register-prompt-gui | GUI-mode registration prompt text shown on the same trigger as `messages.register-prompt`. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `register_prompt_gui` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.register-prompt-gui (default: written in the server's language; under language: en '&ePlease set your password in the popup')` | n/a | n/a | admin | none | LoginService#onPlayerJoin |
| ultilogin.config.login.messages.register-success | Message shown on successful `/register` (both text and GUI mode). The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `register_success` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.register-success (default: written in the server's language; under language: en '&aRegistration successful! Welcome to the server!')` | n/a | n/a | admin | brief | RegisterCommand#register |
| ultilogin.config.login.messages.timeout-kick | Kick message shown to a player removed by `ultilogin.task.timeout-check`. The value shown is the file's value, as written. It is written in the server's language — its colour code plus the language file's `timeout_kick` — while it is still built-in text, so an untouched value follows `language` (`ultilogin.lifecycle.legacy-text-defaults`, UltiKits/UltiLogin#20) | config | `config/login.yml: messages.timeout-kick (default: written in the server's language; under language: en '&cLogin timeout! Please reconnect.')` | n/a | n/a | admin | brief | LoginService#checkTimeouts |
| ultilogin.config.login.password.max-length | Command-mode maximum password length | config | `config/login.yml: password.max-length (default: 32)` | n/a | n/a | admin | brief | LoginService#isPasswordValid |
| ultilogin.config.login.password.min-length | Command-mode minimum password length | config | `config/login.yml: password.min-length (default: 6)` | n/a | n/a | admin | brief | LoginService#isPasswordValid |
| ultilogin.config.login.security.lockout-duration | Seconds an IP/UUID stays locked out after exceeding `security.max-login-attempts` | config | `config/login.yml: security.lockout-duration (default: 900)` | n/a | n/a | admin | brief | LoginService#recordFailedAttempt |
| ultilogin.config.login.security.lockout-type | Whether a lockout is keyed by `IP`, `UUID`, or `BOTH` (case-insensitive); any other value records and checks no lock at all, so failures are counted but nothing is ever locked (UltiKits/UltiLogin#37) | config | `config/login.yml: security.lockout-type (default: "IP")` | n/a | n/a | admin | detailed | LoginService#isLocked |
| ultilogin.config.login.security.max-login-attempts | Wrong-password attempts allowed before lockout (0 = unlimited: nothing is ever locked, and a wrong password is answered with the language catalogue's `wrong_password` text rather than `messages.account-locked`, UltiKits/UltiLogin#23) | config | `config/login.yml: security.max-login-attempts (default: 5)` | n/a | n/a | admin | brief | LoginService#recordFailedAttempt |
| ultilogin.config.login.session-enabled | Whether a successful login is remembered per `ip:uuid` so a reconnect within `session-timeout` auto-logs in — see `ultilogin.session.not-persisted` for the in-memory-only caveat | config | `config/login.yml: session-enabled (default: true)` | n/a | n/a | admin | brief | LoginService#hasValidSession |
| ultilogin.config.login.session-timeout | Minutes a remembered session stays valid | config | `config/login.yml: session-timeout (default: 30)` | n/a | n/a | admin | brief | LoginService#hasValidSession |
| ultilogin.config.login.spawn-location.enabled | Whether an unauthenticated player is teleported to a fixed spawn location (and returned to their original location on successful login) | config | `config/login.yml: spawn-location.enabled (default: false)` | n/a | n/a | admin | brief | LoginService#applyNoSessionProtections |
| ultilogin.config.login.spawn-location.world | World name for the unauthenticated-player spawn teleport | config | `config/login.yml: spawn-location.world (default: "world")` | n/a | n/a | admin | none | LoginService#applyNoSessionProtections |
| ultilogin.config.login.spawn-location.x | X coordinate for the unauthenticated-player spawn teleport | config | `config/login.yml: spawn-location.x (default: 0)` | n/a | n/a | admin | none | LoginService#applyNoSessionProtections |
| ultilogin.config.login.spawn-location.y | Y coordinate for the unauthenticated-player spawn teleport | config | `config/login.yml: spawn-location.y (default: 64)` | n/a | n/a | admin | none | LoginService#applyNoSessionProtections |
| ultilogin.config.login.spawn-location.z | Z coordinate for the unauthenticated-player spawn teleport | config | `config/login.yml: spawn-location.z (default: 0)` | n/a | n/a | admin | none | LoginService#applyNoSessionProtections |
| ultilogin.config.login.ulticloud.enabled | Whether `/panel` may generate a UltiCloud magic-link. Declared and read correctly (`LoginService#isPanelEnabled`) — not a defect — but note this key's name and its config file's own `EmailConfig`-adjacent section header make it easy to mistake for an email setting; it gates the panel path only | config | `config/login.yml: ulticloud.enabled (default: false)` | n/a | n/a | admin | brief | LoginService#isPanelEnabled |

## Language

Every chat line, GUI title, item name and lore line, command description and console line this
module writes comes from its language file (`lang/en.json`, `lang/zh.json`), so it follows the
framework's `language` setting (UltiKits/UltiLogin#20). The three GUI titles and twenty-one
messages of `config/login.yml` are written into the file in the server's language while they are
still built-in text, and the module sends exactly what the file holds; an operator's edit is kept
(`ultilogin.lifecycle.legacy-text-defaults`). The two YAML copies `lang/en.yml` and `lang/zh.yml`
are removed: the framework reads only the `.json` files, and every YAML entry duplicated one of
them. Two JUnit guards (`UltiLoginLanguageCatalogueTest`, `UltiLoginCjkLiteralScopeTest`) fail the
build when a key is missing from either catalogue, a catalogue key is read by nothing, or Chinese
text appears outside one.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultilogin.i18n.language | All of this module's chat, GUI, command-description and console text in the server's language: `lang/en.json` under `language: en`, `lang/zh.json` under `language: zh` | config | framework `config.yml: language` | n/a | both | admin | none | `lang/en.json`, `lang/zh.json`, every `i18n(...)` call |
