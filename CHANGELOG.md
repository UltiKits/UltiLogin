# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Changed

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
- A wrong password is now answered with the account-locked message only when that attempt really
  locks the account. Two configurations used to show it although nothing was locked and the next
  attempt was always accepted: `security.max-login-attempts: 0` (unlimited attempts), on every
  wrong password; and a `security.lockout-type` other than `IP`, `UUID` or `BOTH`, on every wrong
  password from the one that reaches the limit onwards. Both now answer "Wrong password! Please
  try again." The account-locked message was `messages.account-locked` ("try again in `{TIME}`
  seconds", with `{TIME}` set to `security.lockout-duration`); the new reply comes from this
  module's language catalogue (key `wrong_password`), so it follows the server's `language`
  setting. Whether an unrecognised `lockout-type` should lock at all is unchanged here and is
  tracked in UltiKits/UltiLogin#37. Servers with a limit and a recognised `lockout-type` are
  unchanged: a wrong password still shows the attempts remaining, and the attempt that reaches the
  limit still shows the account-locked message and locks the account (UltiKits/UltiLogin#23).
- 输错密码时，只有该次尝试确实锁定了账户，才会回复账户锁定消息。此前有两种配置会在实际并未锁定、下一次尝试
  也总是照常受理的情况下显示该消息：`security.max-login-attempts: 0`（不限制尝试次数）时的每一次输错；以及
  `security.lockout-type` 不是 `IP`、`UUID` 或 `BOTH` 时，从达到上限的那一次起的每一次输错。两者现在都回复
  "密码错误！请重试。"。账户锁定消息即 `messages.account-locked`（"请在 `{TIME}` 秒后重试"，`{TIME}` 取
  `security.lockout-duration`）；新的回复来自本模块的语言文件（键 `wrong_password`），因此会跟随服务器的
  `language` 设置。无法识别的 `lockout-type` 究竟是否应当锁定，本次不作改变，由 UltiKits/UltiLogin#37 跟踪。
  设置了次数上限且 `lockout-type` 可识别的服务器不受影响：输错密码仍显示剩余尝试次数，达到上限的那一次仍
  显示账户锁定消息并锁定账户（UltiKits/UltiLogin#23）。

### Removed

- `lang/en.yml` and `lang/zh.yml`. The framework reads only `lang/en.json` and `lang/zh.json`, and
  every entry in the two YAML files was a copy of an entry in the JSON file of the same language, so
  editing them never changed anything. If you customised `lang/*.yml` on disk, move those edits to
  `lang/*.json`.
- The `email_bind_reward` and `panel_auth_success` language entries. No code ever read them, so
  removing them changes nothing players see.
- 移除 `lang/en.yml` 与 `lang/zh.yml`。框架只读取 `lang/en.json` 与 `lang/zh.json`，两个 YAML 文件中的每一条都是同语言
  JSON 文件中某一条的副本，因此修改它们从未产生任何效果。若你在磁盘上自定义过 `lang/*.yml`，请把这些修改移到
  `lang/*.json`。
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
  server's `language` setting and is customised there. Removing the setting does not remove the
  ability to change the text; it moves it to the file that already held it in both languages.
  A server upgraded from an earlier version keeps the key in its `login.yml`, because the framework
  never deletes a key from an operator's file; while it is there, the module logs one warning at
  startup and on every reload of this module (`/ul reload` or `/ul reload UltiLogin`), naming the
  file and the key, and the key can simply
  be deleted (UltiKits/UltiLogin#23).
- 移除 `config/login.yml` 中的 `messages.wrong-password` 设置项。它在任何版本中都从未生效：没有任何代码读取它，
  修改它从未改变玩家看到的内容。现在新增的"密码错误"回复（见 `### Fixed`）改为从本模块的语言文件读取文本——
  `config` 文件夹旁 `lang/<语言>.json` 中的 `wrong_password` 条目——因此会跟随服务器的 `language` 设置，
  也应在那里修改。移除该设置项并不意味着无法再修改这段文本，只是把它移到了早已以两种语言保存这段文本的文件中。
  从旧版本升级的服务器，其 `login.yml` 中仍会保留该键，因为框架从不删除运维文件中的键；只要该键还在，
  本模块会在启动时以及每次重载本模块时（`/ul reload` 或 `/ul reload UltiLogin`）记录一条警告，指出文件与键名，直接删除该键即可
  （UltiKits/UltiLogin#23）。

### Fixed

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
