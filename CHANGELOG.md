# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Added

- On UltiTools-API 6.3.1 the module registers `/login`, `/l`, `/register`, `/reg`, `/changepassword`, `/changepw`,
  `/cpw`, `/setpassword`, `/setpw`, `/recover`, `/logadmin`, `/loginadmin` and `/regs` with the framework's
  sensitive-command registry when it is enabled, so their arguments are replaced with `***` in everything the
  framework sends to UltiCloud (the panel console, error reports and chat). The list is taken from the commands' own
  declarations: every alias of every command that takes arguments. The local console and log files keep the module's
  own `******` mask, which is unchanged.
- 在 UltiTools-API 6.3.1 上，模块启用时会把 `/login`、`/l`、`/register`、`/reg`、`/changepassword`、`/changepw`、`/cpw`、
  `/setpassword`、`/setpw`、`/recover`、`/logadmin`、`/loginadmin` 和 `/regs` 登记到框架的敏感命令表，框架发往 UltiCloud 的所有内容
  （面板控制台、错误报告、聊天）中，这些命令的参数都会被替换为 `***`。列表取自命令本身的声明：凡是带参数的命令，其全部别名都会登记。
  本地控制台与日志文件仍使用模块自己的 `******` 遮蔽，规则不变。

### Changed

- Declining a web sign-in with "Not me" now uses neutral wording and asks the player to run `/panel` again. The bind
  and replace pages' "Cancel" buttons give the same declined message; the unused old refusal text is no longer shipped.
- 在网页登录页点击“不是我”时，现在使用中性的拒绝提示，并说明需重新执行 `/panel`。绑定页和替换页的“取消”按钮也显示同一条提示；不再随模块提供无人读取的旧拒绝文字。
- Requires UltiTools-API 6.3.1 or later. `api-version` rises from 630 to 631, so UltiTools-API 6.3.0 refuses this
  version at load with a warning that the UltiTools version is outdated.
- 需要 UltiTools-API 6.3.1 或更高版本。`api-version` 从 630 提高到 631，UltiTools-API 6.3.0 会在加载时拒绝此版本，并警告
  UltiTools 版本过旧。
- `/panel` now creates its link through UltiTools-API 6.3.1's player-link API instead of the module's own HTTP request, and
  nothing is polled any more: UltiCloud reports the result as an event. All three proofs still work (logged in in game, an
  already linked account, a web registration). A sign-in that completes is answered to UltiCloud (account created, account
  already exists, not requested, registration rejected, login plugin unavailable); a repeated delivery of the same sign-in
  creates and logs in nothing twice; a sign-in that arrives after the player left, the server restarted or the link ended
  creates a requested account but logs nobody in, and tells a player who is online to run `/panel` again. A link ends when
  UltiCloud says so (expired, cancelled, declined) or at its own expiry time, which holds the login countdown for at most
  ten minutes.
- Messages changed. New: "You have been logged in via UltiCloud." (no more "(Player)" or "(Server Owner)"), "The web panel
  sign-in is complete.", a cancelled link now says to run `/panel` again (cancelling is final), the rate-limit and
  link-not-permitted answers, "Your game account could not be created. Run /panel again to retry.", and the late sign-in
  line. The refusal reasons the old protocol gave are gone: clicking "Not me" now says "The web panel sign-in was
  declined. Run /panel again for a new link."
- A game password set with `/setpassword` is now described as a backup for when UltiCloud cannot be reached, and the
  player is told to use a password different from their UltiKits account. The reminder follows every web sign-in of an
  account that has no game password and every join, `/login` and re-prompt in which that account is not logged in; the
  `/login` refusal and `/setpassword`'s help text say the same.
- `/panel` for a name without an account is refused before any link is requested when its address has already reached
  `max-register-per-ip`, so the web side no longer creates an UltiKits account the game would refuse. The limit is checked
  again when the web sign-in finishes.
- 现在 `/panel` 通过 UltiTools-API 6.3.1 的玩家链接接口创建链接，不再使用模块自己的 HTTP 请求，也不再轮询：UltiCloud 以事件报告结果。三种证明仍然
  可用（已在游戏内登录、已关联账号、网页注册）。完成的网页登录会回执给 UltiCloud（账号已创建、账号已存在、未要求、注册被拒绝、登录插件不可用）；
  同一次登录重复投递不会重复建号或登录；玩家已退出、服务器已重启或链接已结束后才到达的登录会创建被要求的账号但不让任何人登录，并提示在线的玩家重新
  执行 `/panel`。链接在 UltiCloud 报告结束（过期、取消、拒绝）时，或到自己的过期时间时结束，对登录倒计时的暂停最长十分钟。
- 提示文字有变化。新增：“You have been logged in via UltiCloud.”（不再区分 Player／Server Owner）、网页登录完成提示、取消后需重新执行 `/panel`（取消是终态）、
  请求太频繁和不允许生成链接的提示、游戏账号创建失败提示，以及迟到登录的提示。旧协议给出的各种拒绝原因已取消，点击“不是我”会提示“网页面板登录已被拒绝。请重新执行 /panel 获取新链接。”。
- 用 `/setpassword` 设置的游戏密码现在被说明为 UltiCloud 连不上时用的备用密码，并提醒玩家使用与 UltiKits 账号不同的密码。每次没有游戏密码的账号经网页登录后，
  以及该账号未登录时的每次进服、`/login` 和再次提示，都会提醒；`/login` 的拒绝提示和 `/setpassword` 的帮助文字同样如此。
- 名字没有账号时，若其地址已达 `max-register-per-ip`，`/panel` 在请求链接前就会拒绝，网页不再建出游戏会拒绝的 UltiKits 账号；网页登录完成时会再检查一次。

### Removed

- UltiLogin 2.0.0's `/panel` request path (the HTTP call to UltiCloud, the three-second polling, the owner/player
  distinction in the sign-in message, the four refusal reasons and the credential-refused message) is gone, with no
  bridge: UltiLogin 2.0.0's `/panel` does not work on UltiTools-API 6.3.1, so update to this version. Players can still
  use `/login`.
- UltiLogin 2.0.0 的 `/panel` 请求路径（对 UltiCloud 的 HTTP 请求、三秒轮询、登录提示中服主／玩家的区分、四种拒绝原因以及凭据被拒绝的提示）已移除，且没有
  兼容桥接：UltiLogin 2.0.0 的 `/panel` 在 UltiTools-API 6.3.1 上不能使用，请更新到此版本。玩家仍可使用 `/login`。

## [2.0.0] - 2026-10-21

### Added

- `/setpassword <new> <confirm>` (alias `/setpw`) sets the first game password of an account created on the web
  through `/panel`. It works only while the player is logged in and the account has no game password, and it follows
  the same password rules as `/register`; afterwards `/login` works with it. An account that already has a password
  is told to use `/changepassword`.
- `/setpassword <新密码> <确认>`（别名 `/setpw`）为通过 `/panel` 在网页上创建的账号设置第一个游戏密码。仅在玩家已登录且账号没有
  游戏密码时可用，密码规则与 `/register` 相同；之后即可用 `/login` 登录。已有密码的账号会被提示使用 `/changepassword`。

- A web registration counts as an UltiLogin registration. When a player whose name has no UltiLogin account on this
  server creates a new UltiKits account on the `/panel` page, the game creates their UltiLogin account without a game
  password, logs them in and tells them once to set one with `/setpassword`. `/login` refuses such an account, whatever
  is typed, until a game password is set, and the refused try is not counted as a wrong password. If the player
  registers in game first, that account is kept untouched. A `max-register-per-ip` limit, when set, applies to it too.
  With `gui-mode.enabled: true` such an account is never shown the number pad, which no digits could satisfy: on
  joining and at every re-prompt the player is told to log in with `/panel` and then set a game password.
- 网页注册视同 UltiLogin 注册。名字在本服务器上没有 UltiLogin 账号的玩家在 `/panel` 页面上新建 UltiKits 账号后，游戏会为其创建
  一个没有游戏密码的 UltiLogin 账号、让其登录，并提示一次用 `/setpassword` 设置游戏密码。在设置游戏密码之前，无论输入什么
  `/login` 都会拒绝该账号，且不计为密码错误。若玩家先在游戏内注册，则保留该账号不变。设置了 `max-register-per-ip` 时同样适用。
  在 `gui-mode.enabled: true` 下，这样的账号不会弹出任何数字都无法通过的数字键盘：进入服务器及每次再次提示时，玩家都会被告知
  用 `/panel` 登录，然后设置游戏密码。

- Every web outcome of a `/panel` link is shown in game: cancelled on the page, refused by the web (with the reason),
  expired, signed in, or the server's UltiCloud credential refused (also logged to the console). While a player who
  has not logged in holds a pending link, the login countdown is paused; it resumes with the time that was left when
  the link is cancelled, refused or expires, never with a fresh full timeout. The other restrictions stay. A player who
  leaves the server loses their pending links, so a sign-in completed on the web after they left logs nobody in.
- `/panel` 链接在网页上的每种结果都会在游戏内显示：网页取消、网页拒绝（附原因）、链接过期、登录成功，或服务器的 UltiCloud 凭据被
  拒绝（同时写入控制台）。未登录的玩家持有待处理链接期间，登录倒计时暂停；链接被取消、拒绝或过期后，按暂停时剩余的时间继续，
  绝不重新计满。其他限制保持不变。玩家离开服务器后其待处理链接作废，
  此后在网页上完成的登录不会让任何人登录。

### Changed

- **Lockout redesign (UltiKits/UltiLogin#50):** BOTH now counts and locks each (address, account UUID) pair,
  independently of that account at other addresses, and separately locks an address after `max-login-attempts`
  different accounts fail there. Success clears only its own pair, never the address's failed-account memberships.
  IP-mode successes no longer clear the shared address count, closing the success-reset bypass. IP counts and
  each BOTH address membership expire `lockout-duration` seconds after their latest failure; unlocked UUID/pair
  counts still reset only on their own success or corresponding lock expiry. BOTH is the new fresh/missing-key
  default. Existing operator values (including IP and lowercase uuid) stay byte for byte; unusable values warn and
  fall back to BOTH only in memory. Recognised old shipped comments refresh without overwriting custom comments.
- **登录封禁重构（UltiKits/UltiLogin#50）：** BOTH 改为按（地址、账号 UUID）组合计数并封禁，同一账号在其他地址不受该组合封禁影响；
  同一地址失败的不同账号数达到 `max-login-attempts` 时另封禁整个地址。成功只清除自身组合计数，不清除地址集合。
  IP 模式成功不再清除共用地址计数，修复成功登录清零绕过；IP 计数和 BOTH 的每个地址集合成员在最近一次失败的 `lockout-duration` 秒后过期。
  未封禁的 UUID/组合计数仍只在自身成功或对应封禁到期时重置。新文件或缺少键时默认 BOTH；现有值（含 IP、小写 uuid）逐字节保留，
  无效值只警告并在内存回退到 BOTH。已识别的旧出厂注释更新，自写注释不覆盖。

- **`/panel` requires the server to be signed in to UltiCloud.** Create and poll both use the server owner's
  credential; there is no anonymous fallback or grace period for older plugins. If the server is not signed in,
  no link is created: the player is told to ask the owner to run `ulticloud login` in the console. A failed creation
  restores the remaining login countdown silently; it does not grant a new timeout. A refused credential (HTTP
  401/403) is reported in game and in the console, never retried without it. Opening a link never logs the player
  in by itself: the page asks for the linked account's password, a device that account told it to remember (one
  click), confirmation by a signed-in account (only for a player logged in in game), or a new account (only for a
  name without an UltiLogin account). A name with an UltiLogin account that is neither logged in in game nor linked
  is refused until the player runs `/login` once. Normal website account login, browser link verification,
  completion and cancellation, and game `/login` remain available.
- **`/panel` 要求服务器先登录 UltiCloud。** 创建和轮询链接都使用服务器所有者的凭据；没有匿名回退，也不为旧插件保留宽限期。
  服务器未登录时不生成链接，玩家会被提示请服主在控制台执行 `ulticloud login`。创建失败会按剩余时间静默恢复登录倒计时，
  不会重新给予完整超时。凭据被拒绝（HTTP 401/403）时会在游戏内及控制台提示，绝不改用无凭据请求重试。仅打开链接不会让玩家登录：
  网页会要求输入关联账号的密码、使用该账号让其记住的设备（点击一次）、由已登录账号确认（仅限已在游戏内登录的玩家），或新建账号
  （仅限没有 UltiLogin 账号的名字）。已有 UltiLogin 账号、但既未在游戏内登录也未关联的名字会被拒绝，直到玩家先执行一次 `/login`。
  正常网站账号登录、浏览器链接验证／完成／取消，以及游戏 `/login` 均保留。

- UltiLogin 2.0.0 requires UltiTools-API 6.3.0 or later and raises `api-version` in `plugin.yml` from `620`
  (the last released version, 1.0.0) to `630`. Older frameworks cannot load this module; upgrade UltiTools before
  installing it. A new module JAR needs a server restart; `/ul reload` only reloads configuration
  (UltiKits/UltiTools-Reborn#544).
- UltiLogin 2.0.0 需要 UltiTools-API 6.3.0 或更高版本，`plugin.yml` 中的 `api-version` 从上一个发布版本 1.0.0 的 `620`
  提高到 `630`。更早的框架无法加载本模块；请先升级 UltiTools，再安装本模块。新的模块 JAR 需要重启服务器，`/ul reload` 只会
  重载配置（UltiKits/UltiTools-Reborn#544）。

- `plugin.yml` now declares `identify-string: ultilogin`, the key of this module's entry in the UltiCloud catalogue.
  The framework's update check and `/upm update` skip a module that does not declare it, so this module now takes part
  in both: a later published version carrying the same key is reported at startup and can be installed with
  `/upm update` (UltiKits/UltiTools-Reborn#474).
- `plugin.yml` 现在声明 `identify-string: ultilogin`，即本模块在 UltiCloud 模块目录中的条目键。框架的更新检查和 `/upm update`
  会跳过未声明该键的模块，因此本模块现在会参与两者：带有同一键的更高发布版本会在启动时提示，并可用 `/upm update` 安装
  （UltiKits/UltiTools-Reborn#474）。

- `max-register-per-ip` in `config/login.yml` now ships as `0` (no per-IP registration limit) instead of `3`. Many
  players can share one public IP address (carrier-grade NAT, common in mainland China and on mobile and campus
  networks), so a limit can stop legitimate players from registering; the comment above the key now says so. Only a
  file without the key gets the new default: a value already in your file, including `3`, is kept, and only the comment
  earlier versions wrote above it is replaced. When you set a limit, it also applies to an account created on the web
  through `/panel`: the web page reports the registration, but the game refuses the account and the player sees the
  IP-limit message.
- `config/login.yml` 中的 `max-register-per-ip` 出厂值由 `3` 改为 `0`（不限制同一 IP 的注册数）。许多玩家可能共用同一个公网
  IP（运营商级 NAT，在中国大陆以及移动网络、校园网中很常见），限制可能导致正常玩家无法注册；该键上方的注释现已写明这一点。
  只有缺少该键的文件才写入新默认值：文件中已有的值（包括 `3`）保持不变，只替换旧版本写在其上方的注释。设置限制后，它同样
  适用于通过 `/panel` 在网页上创建的账号：网页会显示注册成功，但游戏内拒绝创建账号，玩家会看到 IP 上限提示。

- `allowed-commands: []` (or `~`) in `config/login.yml` no longer refuses every command to a player who has not
  logged in. The setting is declared `@NotEmpty`, and from UltiTools 6.3.0 the framework treats an empty value of such a
  list as unusable: the module runs on the shipped list (`login, l, register, reg, panel, regs, recover`) and logs one
  WARNING naming `allowed-commands`, the value as written and that default. The file is left exactly as you wrote it.
  Before, `[]` was loaded as an empty list, so `/login` and `/register` were refused too; `~` ran the default list but
  the file was rewritten. To allow fewer commands, list them; to allow more, add them (UltiKits/UltiLogin#52).
- `config/login.yml` 中的 `allowed-commands: []`（或 `~`）不再让未登录玩家的所有命令都被拒绝。该设置声明了 `@NotEmpty`，
  自 UltiTools 6.3.0 起框架把此类列表的空值视为不可用：模块改用出厂列表（`login, l, register, reg, panel, regs,
  recover`），并记录一条 WARNING，写明 `allowed-commands`、你写下的值与该默认值；文件保持你写的原样。此前 `[]` 按空列表
  加载，连 `/login` 与 `/register` 也会被拒绝；`~` 会使用默认列表，但文件会被改写。想放行更少的命令，请逐条列出；
  想放行更多，请追加（UltiKits/UltiLogin#52）。

- Message and title settings in `config/login.yml` — the three GUI titles (`gui-mode.title-login`,
  `gui-mode.title-register`, `gui-mode.title-confirm`) and the twenty-one messages (`messages.register-prompt`,
  `register-prompt-gui`, `login-prompt`, `login-prompt-gui`, `register-success`, `login-success`, `already-logged`,
  `not-registered`, `already-registered`, `password-mismatch`, `password-too-short`, `password-too-long`,
  `timeout-kick`, `account-locked`, `attempts-remaining`, `gui-password-invalid`, and `messages.admin.password-reset`,
  `force-login`, `unregister`, `player-not-found`, `account-not-found`) — are written in the server's language when
  the module starts, and the file is what the module shows (for example `messages.login-success:
  '&aLogin successful! Welcome back!'` under `language: en`); previously they were fixed Chinese text, so
  `language: en` had no effect on them. A setting that is still built-in text — in any language, or a default an
  earlier version shipped — follows `language`: it is rewritten when the module starts or after `/ul reload`. A
  setting you edited is kept. To keep a built-in text but stop it following `language`, change at least one
  character (UltiKits/UltiLogin#20). The text written is this module's built-in text: edit these settings in `config/login.yml`; an edit of the extracted
  language file does not change them (earlier versions never read them from the language file either).
- `config/login.yml` 中的消息与标题设置——三个界面标题（`gui-mode.title-login`、`gui-mode.title-register`、
  `gui-mode.title-confirm`）与二十一条消息（`messages.register-prompt`、`register-prompt-gui`、`login-prompt`、
  `login-prompt-gui`、`register-success`、`login-success`、`already-logged`、`not-registered`、`already-registered`、
  `password-mismatch`、`password-too-short`、`password-too-long`、`timeout-kick`、`account-locked`、`attempts-remaining`、
  `gui-password-invalid`，以及 `messages.admin.password-reset`、`force-login`、`unregister`、`player-not-found`、
  `account-not-found`）——在模块启动时按服务器语言写入，文件内容即模块显示的内容；此前它们是写死的中文，`language: en`
  对它们不起作用。仍为内置文本（任一语言的内置文本，或旧版本的出厂默认值）的设置会跟随 `language`：模块启动或执行
  `/ul reload` 后改写为当前语言的文本。你改过的设置保持不变。若想保留内置文本又不让它跟随语言，请至少改动一个字符
  （UltiKits/UltiLogin#20）。写入的是本模块的内置文本：请在 `config/login.yml` 中修改这些设置；修改已解压的语言文件不会改变它们（旧版本同样从不从语言文件读取它们）。

- `language: en` now applies to everything this module shows or logs: the help of `/login`,
  `/register`, `/changepassword`, `/logadmin` and `/panel`, the `/changepassword` and `/logadmin`
  replies, the `/logadmin info` block, the registration refusal when an IP has reached its limit,
  the session auto-login line, the login and registration keypads (item names, lore, the
  confirmation step and the registration failure), the seven command descriptions, and the console
  lines. Most of this was fixed Chinese text in every language, although the language files already
  held English text for much of it that no code read; the console lines were fixed English text and
  now follow `language: zh` too (UltiKits/UltiLogin#20).
- The login and registration keypads' buttons now work under every language and under titles you
  have customised; how the keypads are recognised is in the UltiKits/UltiLogin#35 entry under
  `### Fixed`. They used to be
  recognised by whether the inventory title contained the Chinese word for "password", "login" or
  "register": a keypad whose title contained none of them cancelled every click, which with the
  titles now following `language` would have included the English ones, and another plugin's
  inventory whose title happened to contain one of those words could be clicked by a player who had
  not logged in (UltiKits/UltiLogin#20).
- `language: en` 现在对本模块显示或记录的全部内容生效：`/login`、`/register`、`/changepassword`、`/logadmin`、`/panel`
  的帮助，`/changepassword` 与 `/logadmin` 的回复，`/logadmin info` 信息块，IP 注册数达到上限时的拒绝提示，会话自动登录
  提示，登录与注册数字键盘（物品名称、说明、确认步骤与注册失败提示），七个命令描述以及控制台日志。其中大部分原先在任何
  语言下都是写死的中文，而语言文件中其实已有其中许多内容的无人读取的英文文本；控制台日志原先写死为英文，现在也跟随
  `language: zh`（UltiKits/UltiLogin#20）。
- 登录与注册数字键盘的按钮现在在任何语言以及你自定义的标题下都能正常使用；键盘如何识别见 `### Fixed` 中 UltiKits/UltiLogin#35
  一条。此前的识别方式是
  物品栏标题是否包含"密码"、"登录"或"注册"：标题不含这些词的键盘会取消每一次点击——标题跟随 `language` 后英文标题也会如此；
  而其他插件的物品栏只要标题恰好含有其中一个词，未登录的玩家就能在其中点击（UltiKits/UltiLogin#20）。

- A wrong code in `/recover <code> <password> <confirm>` now says how many attempts are left, for
  example `Invalid verification code! Remaining attempts: 2`. It used to show the placeholder itself,
  `Remaining attempts: {COUNT}`, because the recovery path never filled it in; `/regs <code>`
  already did (UltiKits/UltiLogin#21).
- `/recover <验证码> <密码> <确认密码>` 输错验证码时，现在会显示剩余尝试次数，例如 `验证码错误！剩余尝试次数: 2`。
  此前找回密码流程从未填入该值，直接显示占位符 `{COUNT}`；`/regs <验证码>` 一直是正常的（UltiKits/UltiLogin#21）。

- A player who has not logged in can no longer equip or remove an armor stand's items, interact with
  an entity at a precise point on its body, or swap their main hand and off hand. All three were
  still allowed: the login protection already refused ordinary entity interaction, but Bukkit
  delivers each of these through a separate event, so the existing guards never saw them.
  Armor-stand equipping is the one that was reported and reproduced — an unauthenticated player
  placed a block onto an armor stand and their held stack went down by one.
- Writing a sign is now refused before login. Another plugin can open a sign editor for a player who
  has not logged in yet, which follows no interaction of the player's own, so nothing else refused
  it. Opening the editor is refused as well as the write.
- Writing a book is now refused before login. A player who has not logged in really can reach the
  book editor — the client opens it without asking the server, so no guard can stop it opening — and
  the write then reaches the server on its own, where nothing else refused it. The editor still
  opens; the text and the signature no longer do anything, and the book stays blank.
- Also refused before login, though no way to reach it was demonstrated and none may exist: dragging
  items inside an inventory, middle-click item pick, swapping the held item with an equipment slot,
  and recipe-book clicks. These are refused explicitly rather than left resting on the expectation
  that other guards make them unreachable (UltiKits/UltiLogin#24).
- 未登录的玩家现在无法再为盔甲架穿脱物品、对实体身体的精确位置进行交互，也无法交换主手与副手的物品。此前这三种
  操作均可执行：登录保护已经拦截了普通的实体交互，但 Bukkit 通过各自独立的事件分发上述操作，因此原有的拦截从未
  收到它们。其中被报告并复现的是盔甲架穿戴——未登录玩家成功把一个方块装备到盔甲架上，其手持物品数量随之减少一个。
- 登录前书写告示牌现在会被拒绝。其他插件可以为尚未登录的玩家打开告示牌编辑界面，这一路径不经过该玩家自身的任何
  交互，因此此前没有任何拦截。除写入之外，打开编辑界面本身也会被拒绝。
- 登录前书写成书现在会被拒绝。未登录玩家确实能打开成书编辑界面——该界面由客户端自行打开，不询问服务器，因此
  任何拦截都无法阻止它打开——写入随后会独立到达服务器，而此前没有任何拦截。编辑界面仍会打开，但输入的正文与
  签名不再产生任何效果，成书保持空白。
- 以下操作在登录前同样会被拒绝，但并未证实存在可触发的路径，也可能本就不存在：在物品栏内拖拽物品、中键取物、
  将手持物品与装备栏互换，以及配方书点击。此处选择明确拒绝，而不是继续依赖"其他拦截使其无法触发"这一预期
  （UltiKits/UltiLogin#24）。
- After `/upm uninstall UltiLogin`, this module's commands (`/login`, `/register`,
  `/changepassword`, `/logadmin`, `/recover`, `/regs`, `/panel`) are now really removed and its
  `LoginProtectionListener` stops firing. Previously this module replaced the framework's unload
  method with one that only logged a line, so both stayed active until the server restarted
  (UltiKits/UltiLogin#29).
- 执行 `/upm uninstall UltiLogin` 后，本模块的命令（`/login`、`/register`、`/changepassword`、
  `/logadmin`、`/recover`、`/regs`、`/panel`）现在会被真正移除，其 `LoginProtectionListener` 监听器也不再
  触发。此前本模块用一个只打印日志的方法替换了框架的卸载方法，因此两者都会一直保持生效，直到服务器重启
  （UltiKits/UltiLogin#29）。
- With `security.max-login-attempts: 0` (unlimited attempts), a wrong password now replies "Wrong password! Please
  try again." instead of claiming the account was locked when nothing was locked. This reply uses `wrong_password`
  in the module's language catalogue and follows `language`. With a limit, a wrong password shows the remaining
  attempts until a lock is actually reached, then shows `messages.account-locked` (UltiKits/UltiLogin#23).
- 设置 `security.max-login-attempts: 0`（不限制尝试次数）时，输错密码现在回复“密码错误！请重试。”，不再在实际未封禁时声称
  账号已锁定。该回复取自本模块语言文件的 `wrong_password`，跟随 `language`。设置次数上限时，输错密码会显示剩余次数，
  只有实际达到封禁条件时才显示 `messages.account-locked`（UltiKits/UltiLogin#23）。

### Removed

- `lang/en.yml` and `lang/zh.yml`. The framework reads only `lang/en.json` and `lang/zh.json`, and
  every entry in the two YAML files was a copy of an entry in the JSON file of the same language, so
  editing them never changed anything. If you customised `lang/*.yml` on disk, carry those edits into a
  custom language file, not into the official `lang/*.json`: copy the official file in the same `lang/` folder to a name that starts with its language code and a hyphen,
  keeping the extension (for example `lang/en-myserver.json`), and set `language: en-myserver` in `plugins/UltiTools/config.yml`.
  An edit made in an official file is undone when the file is restored, at every start and module reload,
  and survives only in its `.bak` (UltiKits/UltiTools-Reborn#616).
- The `email_bind_reward` and `panel_auth_success` language entries. No code ever read them, so
  removing them changes nothing players see.
- 移除 `lang/en.yml` 与 `lang/zh.yml`。框架只读取 `lang/en.json` 与 `lang/zh.json`，两个 YAML 文件中的每一条都是同语言
  JSON 文件中某一条的副本，因此修改它们从未产生任何效果。若你在磁盘上自定义过 `lang/*.yml`，请把这些修改放进自定义语言文件，
  而不是官方的 `lang/*.json`：在同一 `lang/` 目录中把官方文件复制为以其语言代码加连字符开头、扩展名不变的文件
  （例如 `lang/zh-myserver.json`），并在 `plugins/UltiTools/config.yml` 中设置 `language: zh-myserver`。在官方文件中
  做的修改会在每次启动和模块重载恢复该文件时被撤销，只保留在其 `.bak` 中（UltiKits/UltiTools-Reborn#616）。
- 移除 `email_bind_reward` 与 `panel_auth_success` 两个语言条目。从未有代码读取它们，因此移除它们不会改变玩家看到的任何内容。

- The module's own `UltiLogin 已禁用！` ("UltiLogin disabled!") console line on unload and its own
  `UltiLogin 配置已重载！` ("UltiLogin configuration reloaded!") console line on `/ul reload UltiLogin`
  (both printed in Chinese under either `language` setting), and the never-read `login_disabled` and
  `login_reloaded` language keys that described them. UltiTools 6.3.0 logs one reload line per module
  (`Module 'UltiLogin' reloaded.`). Reloading still re-reads `login.yml` into the running module,
  exactly as before: that was already done by the framework's reload method, which this module's
  override called first (UltiKits/UltiLogin#29).
- 移除本模块卸载时输出的"UltiLogin 已禁用！"控制台行、`/ul reload UltiLogin` 时输出的
  "UltiLogin 配置已重载！"控制台行，以及未被读取的 `login_disabled`、`login_reloaded` 语言键。
  UltiTools 6.3.0 会为每个模块输出一行重载日志（`Module 'UltiLogin' reloaded.`）。重载仍会像以前一样把
  `login.yml` 重新读入运行中的模块：此前本模块的重载方法已先调用框架的重载方法完成这一步
  （UltiKits/UltiLogin#29）。
- The `messages.wrong-password` setting in `config/login.yml`. It never took effect in any version:
  no code read it, so editing it never changed what a player saw. The wrong-password reply that
  now exists (see `### Fixed`) takes its text from this module's language file instead — entry
  `wrong_password` in `lang/<language>.json`, beside the `config` folder — so it follows the
  server's `language` setting. To customise it, copy the official language file to one whose name starts
  with its language code and a hyphen (for example `lang/en-myserver.json`), edit `wrong_password` there
  and set `language: en-myserver` in `plugins/UltiTools/config.yml`; an edit made in the official file
  itself is restored at the next start or module reload, the edited file kept as `.bak` (UltiKits/UltiTools-Reborn#616).
  A server upgraded from an earlier version keeps the key in its `login.yml`, because the framework
  never deletes a key from an operator's file; while it is there, the module logs one warning at
  startup and on every reload of this module (`/ul reload` or `/ul reload UltiLogin`), naming the
  file and the key, and the key can simply
  be deleted (UltiKits/UltiLogin#23).
- 移除 `config/login.yml` 中的 `messages.wrong-password` 设置项。它在任何版本中都从未生效：没有任何代码读取它，
  修改它从未改变玩家看到的内容。现在新增的"密码错误"回复（见 `### Fixed`）改为从本模块的语言文件读取文本——
  `config` 文件夹旁 `lang/<语言>.json` 中的 `wrong_password` 条目——因此会跟随服务器的 `language` 设置，
  要自定义它，请把官方语言文件复制为以其语言代码加连字符开头的文件（例如 `lang/zh-myserver.json`），在副本中修改
  `wrong_password`，并在 `plugins/UltiTools/config.yml` 中设置 `language: zh-myserver`；直接修改官方文件的改动会在
  下次启动或模块重载时被恢复，修改过的文件保留为 `.bak`（UltiKits/UltiTools-Reborn#616）。
  从旧版本升级的服务器，其 `login.yml` 中仍会保留该键，因为框架从不删除运维文件中的键；只要该键还在，
  本模块会在启动时以及每次重载本模块时（`/ul reload` 或 `/ul reload UltiLogin`）记录一条警告，指出文件与键名，直接删除该键即可
  （UltiKits/UltiLogin#23）。

### Fixed

- Passwords no longer reach the server log in plain text. Paper writes every player command line to the console and
  `logs/latest.log`, and UltiTools-API 6.3.0 mirrors the console to the UltiPanel log stream, so `/login`, `/register`,
  `/changepassword`, `/setpassword`, `/recover` and `/logadmin reset`, with every alias, used to be recorded with the
  password. While the module is enabled a filter on the server's Log4j root logger now replaces their arguments with
  `******` (`Steve issued server command: /login ******`), in any letter case and with any namespace prefix; other
  commands are logged unchanged, and the filter is removed when the module is unloaded. Two paths are not covered and
  are documented under Known limitations: a reset sent from the panel's remote console, and the reply to
  `logadmin reset` run from the console (UltiKits/UltiLogin#57).
- 密码不再以明文进入服务器日志。Paper 会把玩家输入的每条命令写入控制台和 `logs/latest.log`，UltiTools-API 6.3.0 还会把控制台同步到
  UltiPanel 面板日志流，因此 `/login`、`/register`、`/changepassword`、`/setpassword`、`/recover` 和 `/logadmin reset`
  （含所有别名）过去会连同密码一起被记录。现在模块启用期间，服务器 Log4j 根日志器上的过滤器会把这些命令的参数替换为 `******`
  （`Steve issued server command: /login ******`），不区分大小写、带任何命名空间前缀均可；其他命令照常记录，模块卸载时过滤器随之
  移除。有两条路径未覆盖，已写入"已知限制"：通过面板远程控制台发送的重置，以及在控制台执行 `logadmin reset` 时的回复
  （UltiKits/UltiLogin#57）。
- The failed-login count now follows what `security.lockout-type` locks. With `UUID` it is kept per account, so
  one account's lock no longer leaves every other account at the same address one wrong password away from being
  locked, and one account's wrong passwords no longer count against another's; `messages.attempts-remaining`
  is per account too. With `IP` it stays per address (accounts behind one address share it, by design). With
  `BOTH`, failures are counted per (address, account UUID) pair and separately by different failing accounts at
  each address, as described in the lockout redesign above; remaining attempts follow the pair's count
  (UltiKits/UltiLogin#48, UltiKits/UltiLogin#45).
- 失败登录次数现在按 `security.lockout-type` 所封禁的对象计数。使用 `UUID` 时按账号计数：一个账号被封禁后，同一地址上
  的其他账号不再只差一次输错就被封禁，一个账号输错密码也不再算到另一个账号头上；`messages.attempts-remaining`
  同样按账号。使用 `IP` 时仍按地址计数（同一地址背后的账号共用，设计如此）。使用 `BOTH` 时按（地址、账号 UUID）组合计数，
  另统计每个地址上失败的不同账号数，详见上面的登录封禁重构；显示的剩余次数按组合计数
  （UltiKits/UltiLogin#48、UltiKits/UltiLogin#45）。
- An account write whose stored row no longer exists is now reported as failed on every storage type. On a
  database shared by several servers, another server's `/logadmin unregister` between this module's read and
  its write used to leave the write silently lost. Each of the six writes (login, panel login, `/changepassword`,
  an admin password reset, a recovery password reset, an email bind) now logs its own error line followed by
  "The stored account row no longer exists, so nothing was written" and takes the result it already has for a
  failed write: an admin or recovery reset hands out no new password and `/changepassword` does not change
  anything, while a login, a panel login and an email bind carry on as they do for a failed write (a player
  whose bind was lost is still told it succeeded and still receives the reward) (UltiKits/UltiLogin#47).
- 账号记录已不存在时的写入，现在在所有存储类型上都按失败处理。多台服务器共用数据库时，另一台服务器的
  `/logadmin unregister` 恰好发生在本模块读与写之间，过去这次写入会悄悄落空。六处写入（登录、面板登录、`/changepassword`、
  管理员重置密码、找回密码重置、绑定邮箱）现在都会先输出该处原有的错误日志，再附上“已存储的账号记录已不存在，未写入任何内容”，
  并沿用该处对失败写入原有的结果：管理员或找回重置不会给出新密码，`/changepassword` 不会改动任何内容；登录、面板登录和
  绑定邮箱则和写入失败时一样继续（绑定丢失的玩家仍会被告知绑定成功并领取奖励）（UltiKits/UltiLogin#47）。
- The comments above the keys of `config/login.yml` (42) and `config/email.yml` (8) now come from the
  module's language files: a server set to `language: en` writes English comments on a fresh install
  (earlier versions wrote Chinese-only comments in every language). The comments the framework wrote on
  these keys, the Chinese ones earlier versions wrote included (the old comment above `security.lockout-type`
  too, UltiKits/UltiLogin#51), switch to the server's language at the next start, and after you change
  `language` and run a bare `/ul reload`; values are untouched, and a comment you wrote yourself is kept as you
  wrote it (UltiKits/UltiTools-Reborn#611). The comment above `security.lockout-type` now also says
  what each type counts and locks (see the failed-login count entry) (UltiKits/UltiLogin#46, UltiKits/UltiLogin#45).
- `config/login.yml` 与 `config/email.yml` 中各配置项上方的注释（分别 42 条和 8 条）现在取自模块的语言文件：
  `language: en` 的服务器全新安装时写入英文注释（此前所有语言下都写入纯中文注释）。框架在这些项上写下的注释（包括旧版本写下的
  中文注释，`security.lockout-type` 上方的旧注释也在内，UltiKits/UltiLogin#51）会在下次启动时、以及你修改 `language` 并执行
  不带参数的 `/ul reload` 后切换为服务器语言；配置值不变，你自己写的注释保持原样（UltiKits/UltiTools-Reborn#611）。`security.lockout-type` 上方的注释同时写明各类型计数和封禁的对象（见失败登录次数一条）（UltiKits/UltiLogin#46、UltiKits/UltiLogin#45）。
- The console warning about a setting this version no longer reads names the file exactly as it is: a
  server installed under a directory whose name contains `{KEY}` or `{REASON}` used to get a path that does
  not exist, because the path was inserted before those placeholders were filled. Every placeholder of the
  line is now filled in one pass (the same fix as UltiKits/UltiMail#37).
- 关于本版本已不再读取的配置项的控制台警告现在按原样给出文件路径：此前路径先于 `{KEY}`、`{REASON}` 占位符插入，
  安装目录名含这两个占位符的服务器会看到不存在的路径。现在同一行的所有占位符一次性替换（与 UltiKits/UltiMail#37 相同的修复）。

- Plugin NPCs are no longer treated as unauthenticated players: a player-type NPC carrying the `NPC`
  metadata that NPC plugins such as Citizens set is left alone by the whole login protection — it can take
  damage, ride and move, and it is not prompted or timed out. Before, such an NPC was invulnerable, could not
  ride and was dropped from moving vehicles (UltiKits/UltiLogin#42).
- 插件 NPC 不再被当作未登录玩家：带有 Citizens 等 NPC 插件所设 `NPC` 元数据的玩家型 NPC 不受登录保护的任何限制——可以受伤、乘骑和移动，
  也不会收到登录提示或因超时被踢出。此前这样的 NPC 无敌、无法乘骑，行驶中的载具还会让其下车（UltiKits/UltiLogin#42）。

- With `security.lockout-type: UUID`, a player whose lockout has expired gets the full
  `security.max-login-attempts` again. Before, the failure count was kept, so the first wrong password
  after the lockout locked the account again at once (UltiKits/UltiLogin#38).
- 在 `security.lockout-type: UUID` 下，封禁到期后玩家重新获得完整的 `security.max-login-attempts` 次机会。
  此前失败计数被保留，封禁结束后第一次输错密码就会立即再次锁定（UltiKits/UltiLogin#38）。

- An unusable `security.lockout-type` in `config/login.yml` (anything other than `IP`, `UUID` or `BOTH`, ignoring
  letter case and surrounding spaces) now falls back to `BOTH` in memory only. The module names the original value
  in a console warning at start-up and on `/ul reload`; the file is unchanged. Before, an unrecognised value switched
  lockout off while the wrong-password reply still claimed the account was locked (UltiKits/UltiLogin#37).
- `config/login.yml` 中无法使用的 `security.lockout-type`（忽略大小写及两端空格后，仍不是 `IP`、`UUID` 或 `BOTH` 的值）现在仅在
  内存中回退到 `BOTH`。模块在启动和 `/ul reload` 时会在控制台警告中原样引用该值，文件不变。此前无法识别的值会关闭登录封禁，
  而密码错误的回复仍称账号已被锁定（UltiKits/UltiLogin#37）。

- The registration window (`gui-mode.enabled: true`) now shows `gui-mode.title-confirm` once the first
  password is entered, and `gui-mode.title-register` again after a mismatch: the page re-opens its window
  with the new title, keeping what was entered. Before, the title was stored but the open window kept its
  first title, so both stages looked the same (UltiKits/UltiLogin#25).
- 注册窗口（`gui-mode.enabled: true`）在输入第一次密码后会显示 `gui-mode.title-confirm`，密码不一致后重新显示
  `gui-mode.title-register`：页面以新标题重新打开窗口，已输入的内容保留。此前标题只被记录，已打开的窗口仍显示最初的标题，
  两个阶段看起来一样（UltiKits/UltiLogin#25）。

- On the login screen an unauthenticated player can no longer rearrange their own inventory. While
  the login or registration keypad was open, clicks in the player's own inventory rows were allowed,
  because the keypad was recognised by the title of the whole window, and that window includes the
  player's own rows. The keypad is now recognised by which window is open — this module's own login
  or registration page, whatever its title says — and only clicks on the keypad itself are allowed;
  another inventory whose title reads like the keypad's no longer opens for a player who has not
  logged in (UltiKits/UltiLogin#35).
- 修复：未登录玩家不能再在登录界面整理自己的背包。登录或注册数字键盘打开时，玩家自己背包那几行的点击此前是被允许的，因为键盘是按
  整个窗口的标题识别的，而窗口包含玩家自己的背包行。现在按打开的是哪个窗口识别——本模块自己的登录或注册页面，无论标题如何——并且
  只允许点击键盘本身；标题看起来像键盘的其他物品栏不再对未登录玩家打开（UltiKits/UltiLogin#35）。
- A player who has not logged in can no longer be carried by a vehicle. A minecart rider, a boat's
  second-seat passenger or anyone carried by rails or water was moved across blocks while not
  logged in, because only a player steering a vehicle is held in place, and a player who quit while
  riding a moving minecart was put back in it on rejoin and carried on. A player who has not logged
  in now rides nothing: getting into a vehicle is refused, a player who quit while riding is taken
  off the vehicle one tick after rejoining (the vehicle stays where it is), a player whose login is
  revoked while riding is dismounted, and a moving minecart or boat drops such a passenger. After
  logging in they can get back in (UltiKits/UltiLogin#41).
- 修复：未登录玩家不能再被载具带着移动。此前矿车乘客、船的第二座乘客、被铁轨或水流带着走的玩家会在未登录状态下跨方块移动，
  因为只有操控载具的玩家会被拦住；坐在行驶中的矿车里下线的玩家重进时会被放回矿车并继续移动。现在未登录玩家不乘坐任何载具：
  上载具被拒绝，坐着下线的玩家重进后一刻会被请下载具（载具留在原处），登录被撤销时会下载具，行驶中的矿车或船会让这样的乘客下车。
  登录后可以重新上载具（UltiKits/UltiLogin#41）。
