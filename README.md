# UltiLogin

[![UltiTools-API](https://img.shields.io/badge/UltiTools--API-6.3.1%2B-blue)](https://github.com/UltiKits/UltiTools-Reborn)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

UltiLogin 是 UltiTools 插件系统的登录验证模块，为 Minecraft 服务器提供安全的玩家认证系统。

## 功能特性

### 核心功能

- ✅ **玩家注册/登录** - 支持命令和 GUI 两种模式
- ✅ **会话保持** - 同一 IP 短期内免重复登录
- ✅ **密码安全** - SHA-256 + Salt 加密存储
- ✅ **登录保护** - 未登录时限制所有操作
- ✅ **超时踢出** - 登录超时自动踢出

### 安全特性

- ✅ **登录失败保护** - 失败次数限制 + IP/UUID 临时封禁
- ✅ **IP 注册限制** - 同一 IP 最大注册账户数限制
- ✅ **失明效果** - 未登录时添加失明效果防止偷窥

### 管理功能

- ✅ **管理员命令** - 重置密码、强制登录、删除账号
- ✅ **账号查询** - 查看玩家注册信息

### GUI 模式 (可选)

- ✅ **数字键盘界面** - 1-9 数字按钮输入密码
- ✅ **自动密码验证** - 输入完成自动提交
- ✅ **两步注册确认** - 设置密码后需再次确认

## 命令

### 玩家命令

| 命令 | 别名 | 权限 | 描述 |
|------|------|------|------|
| `/login <密码>` | `/l` | 无 | 登录账号 |
| `/register <密码> <确认>` | `/reg` | 无 | 注册账号 |
| `/changepassword <旧密码> <新密码> <确认>` | `/changepw`, `/cpw` | `ultilogin.changepassword` | 修改密码 |
| `/setpassword <新密码> <确认>` | `/setpw` | 无 | 为网页创建、尚无游戏密码的账号设置备用游戏密码 / set the backup game password of an account created on the web |
| `/panel` | — | 无 | 获取 UltiCloud 网页面板链接（登录前可用）/ get an UltiCloud web panel link (usable before `/login`) |

### 管理员命令

| 命令 | 权限 | 描述 |
|------|------|------|
| `/logadmin reset <玩家> [密码]` | `ultilogin.admin` | 重置玩家密码 |
| `/logadmin forcelogin <玩家>` | `ultilogin.admin` | 强制登录玩家 |
| `/logadmin unregister <玩家>` | `ultilogin.admin` | 删除玩家账号 |
| `/logadmin info <玩家>` | `ultilogin.admin` | 查看玩家账号信息 |

## Web panel sign-in (`/panel`) / 网页面板登录

`/panel` gives the player a clickable UltiCloud link for their own game name on this server. It needs UltiTools-API
6.3.1 or later, `ulticloud.enabled: true` in `login.yml` and the server signed in to UltiCloud (`ulticloud login` in
the console). The link is created through UltiTools-API's player-link API and lives for as long as UltiCloud says;
while it is open the login countdown stays paused, for at most ten minutes. Opening the link never logs anyone in by
itself: the web page asks for a proof first, and the player is logged in in game only after the page completes. Nothing
is polled: UltiCloud reports the result and the game acts on it within a few seconds.

The web accepts one of three proofs:

1. **Logged in in game.** The player ran `/login` and then `/panel`. The page may link the game name to the UltiKits
   account the player signs in with (or creates).
2. **Already linked.** The game name is already linked to an UltiKits account on this server. The page asks for that
   account's password, or offers a device the account holder told it to remember (one click; nothing signs in by
   itself). Then the player is logged in in game.
3. **No game account yet.** The name has no UltiLogin account on this server. The page may create a new UltiKits
   account; the game then creates the player's UltiLogin account without a game password and logs them in. A web
   registration counts as an UltiLogin registration, first come first served.

No password and no password digest is ever sent to UltiCloud by this module. A name that has an UltiLogin account but is
neither logged in in game nor linked cannot sign in this way: the player runs `/login` once, then `/panel` again. To
move a game name to another UltiKits account, unlink it in the first account's settings, sign in to the other account,
and run `/panel` again.

What the player sees in game:

| Situation | Message (`language: en`) |
|---|---|
| Link delivered to a player who has not logged in | "Your login countdown is paused while the web panel link is open. You still cannot move or chat until you are logged in." |
| Signed in on the page | "You have been logged in via UltiCloud." |
| A player who was already logged in finishes a web sign-in | "The web panel sign-in is complete." |
| Signed in to an account that has no game password (always the case for one created on the web) | the line above, then "Your account has no game password yet. Set a backup password with /setpassword <password> <confirm>: it lets you log in with /login when UltiCloud cannot be reached. Use a password different from your UltiKits account." |
| `/login` or a join, for such an account that is not logged in | "This account was created on the web and has no game password yet. Log in with /panel, then set a backup password with /setpassword <password> <confirm> for when UltiCloud cannot be reached (different from your UltiKits password)." |
| Cancelled on the page, or a newer `/panel` replaced the link | "You cancelled the web panel sign-in. Run /panel again for a new link." |
| Refused by the page | "The web panel refused this sign-in." |
| Link expired | "Your web panel link has expired. Run /panel again for a new one." |
| Countdown running again | "Your login countdown has resumed. Log in before it runs out." (with the time that was left, never a fresh full timeout) |
| UltiCloud rate-limited the request | "Too many panel links were requested just now. Wait a minute, then run /panel again." |
| UltiCloud will not issue a link here | "The web panel cannot sign you in from here. If your account has a game password, log in with /login first, then run /panel again." |
| The address has reached `max-register-per-ip` (at `/panel`, and again if it reached it before the web sign-in finished) | "This IP has reached the maximum registration limit!" |
| The server is not signed in to UltiCloud | "Could not create a panel link. The server must be signed in to UltiCloud; ask the server owner to run 'ulticloud login' in the console. If already signed in, check the connection and try again." (no link; the remaining login countdown is restored silently) |
| Any other failure to create the link | "Failed to generate panel link. Please try again later." |
| The web sign-in finished but the game account could not be created | "Your game account could not be created. Run /panel again to retry." |
| The web sign-in finished after the link you used had ended (you left, the server restarted, or your password changed) and you are online | "Your web panel sign-in finished after the link you used had ended. Run /panel again to log in." |
| The web sign-in finished for a name with no UltiLogin account, and no account was asked for | "You are not registered in game on this server, so the web panel cannot log you in. Use /register <password> <confirm> first." |

**The backup password, `/setpassword <new> <confirm>`.** An account created on the web has no game password, and
`/login` refuses it whatever is typed; a refused try is not counted as a wrong password. The game password of such an
account is a backup for when UltiCloud cannot be reached: the player sets it once, while logged in, with `/setpassword`
(the same password rules as `/register`), and afterwards `/login` works. The player is reminded after every web sign-in
of such an account and on every join or `/login` while not logged in, and should use a password different from the UltiKits account's. A player who never
sets one cannot log in while UltiCloud is unreachable. With `gui-mode.enabled: true` such an account is not shown the number pad at all: it gets the same
message on joining and whenever it is prompted again, so the player can type `/panel` (which is in the shipped
`allowed-commands`). An account that already has a password uses `/changepassword`.

**If the panel is unavailable** and such a player has no backup password yet: with `session-enabled: true` they are
logged in automatically when they rejoin from the same IP within `session-timeout`; otherwise an administrator runs
`/logadmin reset <player> <password>` and tells them the password.

**Updating.** UltiLogin 2.0.0's `/panel` does not work on UltiTools-API 6.3.1, which replaced the request path that
version used. Update to UltiLogin 2.1.0; until then players can still log in with `/login`. UltiLogin 2.1.0 does not load on
older UltiTools-API versions.

`/panel` 为玩家提供一个可点击的 UltiCloud 链接，对应其在本服务器上的游戏名。需要 UltiTools-API 6.3.1 或更高版本，在
`login.yml` 中设置 `ulticloud.enabled: true`，并且服务器已登录 UltiCloud（在控制台执行 `ulticloud login`）。链接通过 UltiTools-API
的玩家链接接口创建，有效期由 UltiCloud 决定；链接打开期间登录倒计时保持暂停，最长十分钟。仅打开链接不会让任何人登录：网页会先要求一项
证明，只有网页完成后玩家才会在游戏内登录。不再轮询：UltiCloud 报告结果，游戏在几秒内处理。

网页接受以下三种证明之一：

1. **已在游戏内登录。** 玩家先执行 `/login` 再执行 `/panel`。网页可以把游戏名关联到玩家登录（或新建）的 UltiKits 账号。
2. **已关联。** 该游戏名在本服务器上已关联某个 UltiKits 账号。网页要求输入该账号的密码，或提供账号持有人让其记住的设备
   （需点击一次；不会自动登录）。之后玩家在游戏内登录。
3. **尚无游戏账号。** 该名字在本服务器上没有 UltiLogin 账号。网页可以新建 UltiKits 账号；随后游戏为玩家创建一个没有游戏密码的
   UltiLogin 账号并让其登录。网页注册视同 UltiLogin 注册，先到先得。

本模块不会向 UltiCloud 发送任何密码或密码摘要。已有 UltiLogin 账号、但既未在游戏内登录也未关联的名字不能这样登录：玩家先执行一次
`/login`，再执行 `/panel`。要把游戏名移到另一个 UltiKits 账号：在原账号的设置中解除关联，登录另一个账号，再执行 `/panel`。

游戏内可能看到的提示：链接发出时登录倒计时暂停（仍不能移动或聊天）；网页登录成功；已登录的玩家完成网页登录；没有游戏密码的账号登录后提示
用 `/setpassword` 设置备用密码（UltiCloud 连不上时可用 `/login` 登录，请使用与 UltiKits 账号不同的密码）；网页取消，或被新的 `/panel` 替换
（需要重新执行 `/panel`）；网页拒绝；链接过期；倒计时恢复（按暂停时剩余的时间，绝不重新计满）；请求太频繁；UltiCloud 不允许在此生成链接；
同一 IP 注册数已达上限；服务器未登录 UltiCloud（不发链接，静默恢复倒计时）或其他创建失败；网页登录已完成但游戏账号创建失败；网页登录在所用链接结束后才完成
（需重新执行 `/panel`）。具体文字见上表（`language: en`）及 `lang/zh.json`。

**备用密码 `/setpassword <新密码> <确认>`。** 网页创建的账号没有游戏密码，无论输入什么 `/login` 都会拒绝，且不计为密码错误。这类账号的游戏密码是
UltiCloud 连不上时用的备用密码：玩家登录后用 `/setpassword` 设置一次（规则与 `/register` 相同），之后即可用 `/login` 登录。每次这类账号经网页登录后、
以及每次未登录时进服或执行 `/login` 都会提醒玩家，并提示使用与 UltiKits 账号不同的密码。始终不设备用密码的玩家，在 UltiCloud 不可达时无法登录。在 `gui-mode.enabled: true` 下，
这样的账号完全不会弹出数字键盘：进入服务器和每次再次提示时都会收到同一条提示，玩家因此可以输入 `/panel`（它在出厂的 `allowed-commands` 中）。已有密码的账号请使用
`/changepassword`。

**面板不可用时**，若这样的玩家还没有备用密码：在 `session-enabled: true` 下，于 `session-timeout` 内从同一 IP 重进会自动登录；
否则由管理员执行 `/logadmin reset <玩家> <密码>` 并把密码告诉玩家。

**升级。** UltiLogin 2.0.0 的 `/panel` 在 UltiTools-API 6.3.1 上不能使用，因为后者替换了该版本所用的请求路径。请更新到 UltiLogin 2.1.0；在此之前玩家仍可用 `/login` 登录。
UltiLogin 2.1.0 不会在更旧的 UltiTools-API 版本上加载。

### Known limitations / 已知限制

- **A pending link pauses the login countdown for at most ten minutes.** A player who has not logged in can keep the
  countdown paused by running `/panel` again; each link holds it for at most ten minutes. They still cannot move, chat
  or use commands other than the allowed ones while it is paused.
- **A web sign-in that arrives late creates an account if one was asked for, but logs nobody in.** If the player left,
  the server restarted, their password changed or the link had already ended locally, the account is still created (with
  no game password), nobody is logged in, and a player who is online and not logged in is told to run `/panel` again.
  The address of such an account is empty, so it is not counted against `max-register-per-ip`.
- **A crash between creating the account and UltiTools-API recording the answer makes UltiCloud's redelivery answer
  "account exists".** The account itself is untouched; only the answer UltiCloud receives differs.
- **An existing account that completes a web sign-in keeps its password hash, salt and identity.** Its last IP, last login
  time and login count are updated, as a `/login` would.
- **Registering in game while a web registration is pending keeps the in-game account.** If the player runs `/register`
  before the web registration completes, the account they registered in game is kept untouched (with that game
  password), the web sign-in is answered "account exists" and the player is logged in.
- **A per-IP registration limit also applies to web registrations** (see below). It is checked when `/panel` creates the
  link, so the web page is not offered a registration the game would refuse, and again when the web sign-in finishes: if
  the address reached the limit in between, nothing is created, nobody is logged in and the player sees the limit
  message. A sign-in finished after the player left creates the account without counting its address (see the first
  item above).
- **A session auto-login counts as "logged in in game" for `/panel`.** With `session-enabled: true` (the default), a
  player who rejoins from the same IP within `session-timeout` is logged in without a password, and may then link the
  name to an UltiKits account on the web. On an offline-mode server where players share a public IP, someone else
  joining under a name that is not linked yet, from that IP, within that window, could link it to their own account.
  On such a server turn sessions off (`session-enabled: false`) or link your name early.
- **A password reset sent from the panel's remote console is recorded in plain text.** UltiTools writes every command
  the panel's remote console runs to the server log as `[Remote command] > <command>` and records it in its
  remote-action audit log. `logadmin reset <player> <password>` sent that way therefore puts the password in the
  console, `logs/latest.log`, the panel log stream and that audit log; UltiLogin's log filter (see "Passwords in the
  server log" below) only covers the line Paper writes for a player's command, and the registration with
  UltiTools-API 6.3.1 does not cover this echo either, because the echo has no leading `/` (see the same section).
  Reset passwords in game instead (UltiKits/UltiLogin#57).
- **A password reset run from the server console shows the new password in its reply.** `logadmin reset <player>`
  (a generated password) and `logadmin reset <player> <password>` both reply with the new password, so the operator
  can pass it on. Run from the server console or the panel's remote console, that reply is written to
  `logs/latest.log` and mirrored to the panel log stream. Run the reset in game: the reply goes only to the operator
  who ran it, and the command line itself is logged as `/logadmin reset ******` (UltiKits/UltiLogin#57).


- **待处理的链接最多暂停登录倒计时十分钟。** 未登录的玩家可以再次执行 `/panel` 让倒计时继续暂停，每个链接最多十分钟；暂停期间仍
  不能移动、聊天或执行未放行的命令。
- **迟到的网页登录：要求建号就建号，但不让任何人登录。** 若玩家已退出、服务器重启过、其密码已改，或链接已在本地结束，账号仍会创建
  （没有游戏密码），但不会让任何人登录；此刻在线且未登录的玩家会被提示重新执行 `/panel`。这类账号的地址为空，不计入 `max-register-per-ip`。
- **建号成功后、UltiTools-API 记下回执前服务器崩溃，UltiCloud 重投时会被回执为"账号已存在"。** 账号本身不受影响，只是 UltiCloud 收到的回执不同。
- **已有账号完成网页登录时，密码哈希、盐和身份保持不变。** 其最后登录 IP、最后登录时间和登录次数会像 `/login` 一样更新。
- **网页注册尚未完成时在游戏内注册，会保留游戏内账号。** 若玩家在网页注册完成前执行了 `/register`，游戏内注册的账号（及其游戏密码）
  保持不变，网页登录回执"账号已存在"，玩家被登录。
- **同一 IP 注册数限制同样适用于网页注册**（见下文）：创建链接时就会检查，网页不会被提供游戏会拒绝的注册；网页登录完成时再检查一次，若这段时间内
  该地址已达上限，则不建号、不登录，玩家看到上限提示。玩家退出后才完成的登录会创建不计地址的账号（见第一条）。
- **会话自动登录在 `/panel` 中视为"已在游戏内登录"。** 在 `session-enabled: true`（默认）下，于 `session-timeout` 内从同一 IP
  重进的玩家无需密码即被登录，随后可在网页上把该名字关联到 UltiKits 账号。在玩家共用公网 IP 的离线模式服务器上，他人若在这段
  时间内从该 IP 以尚未关联的名字进入，就可能把它关联到自己的账号。此类服务器请关闭会话（`session-enabled: false`），或尽早关联
  自己的名字。
- **通过面板远程控制台发送的密码重置会以明文记录。** UltiTools 会把面板远程控制台执行的每条命令以
  `[Remote command] > <命令>` 写入服务器日志，并记入其远程操作审计日志。因此以这种方式发送的
  `logadmin reset <玩家> <密码>` 会把密码写入控制台、`logs/latest.log`、面板日志流和该审计日志；UltiLogin 的日志过滤器
  （见下文"服务器日志中的密码"）只处理 Paper 为玩家命令写的那一行；登记到 UltiTools-API 6.3.1 也覆盖不到这条回显，因为它没有前导 `/`（见同一节）。
  请改为在游戏内重置密码（UltiKits/UltiLogin#57）。
- **在服务器控制台执行的密码重置会在回复中显示新密码。** `logadmin reset <玩家>`（随机生成密码）和
  `logadmin reset <玩家> <密码>` 都会在回复中给出新密码，以便管理员转告玩家。在服务器控制台或面板远程控制台执行时，这条回复
  会写入 `logs/latest.log` 并同步到面板日志流。请在游戏内执行重置：回复只发给执行命令的管理员，命令行本身记录为
  `/logadmin reset ******`（UltiKits/UltiLogin#57）。

## 配置文件

配置文件位于 `plugins/UltiTools/pluginConfig/UltiLogin/config/login.yml`

```yaml
# ==================== 基础设置 ====================
login-timeout: 60           # 登录超时时间（秒）
session-enabled: true       # 启用会话功能
session-timeout: 30         # 会话过期时间（分钟）
max-register-per-ip: 0      # 同一IP最大注册数（0为不限制，默认关闭；见下方说明）

# ==================== GUI 模式设置 ====================
gui-mode:
  enabled: false            # 启用GUI登录模式
  password-length: 4        # GUI模式密码位数（1-9数字）
  title-login: "&6请输入密码"
  title-register: "&6请设置密码"
  title-confirm: "&6请再次输入密码"

# ==================== 命令模式密码设置 ====================
password:
  min-length: 6             # 密码最小长度
  max-length: 32            # 密码最大长度

# ==================== 登录安全保护 ====================
security:
  max-login-attempts: 5     # 最大登录失败次数（0为不限制）
  lockout-duration: 900     # 封禁时长（秒）
  lockout-type: BOTH        # Pair + distinct-account address protection / 组合及地址防护

# ==================== 位置设置 ====================
spawn-location:
  enabled: false            # 未登录时传送到指定位置
  world: world
  x: 0
  y: 64
  z: 0

# ==================== 其他设置 ====================
allowed-commands:           # 未登录时允许的命令
  - login
  - l
  - register
  - reg
  - panel
  - regs
  - recover
blind-effect: true          # 未登录时失明效果
```

### Per-IP registration limit / 同一 IP 注册数限制

`max-register-per-ip` ships as `0`, which means no limit. Many players can share one public IP address
(carrier-grade NAT, common in mainland China and on mobile and campus networks), so a limit above `0` can stop
legitimate players from registering. Turn it on only if you know your players do not share addresses. Upgrading
does not change a value already in your `login.yml`: a server that has `max-register-per-ip: 3` keeps `3`; only a
file without the key gets `0`.

When the limit is on, it also applies to an account created on the web through `/panel`. The web page then reports
the registration, but the game refuses to create the account: the player sees "This IP has reached the maximum
registration limit!" and is not logged in.

`max-register-per-ip` 默认为 `0`，即不限制。许多玩家可能共用同一个公网 IP（运营商级 NAT，在中国大陆以及移动网络、校园网中很常见），
设为大于 `0` 的值可能导致正常玩家无法注册；只有确认玩家不共用地址时才建议开启。升级不会改动你 `login.yml` 中已有的值：
已写为 `max-register-per-ip: 3` 的服务器保持 `3`，只有缺少该键的文件才写入 `0`。

开启限制后，它同样适用于通过 `/panel` 在网页上创建的账号：网页会显示注册成功，但游戏内会拒绝创建账号，
玩家看到“该IP已达到最大注册数量！”且不会被登录。

## 权限节点

| 权限 | 默认 | 描述 |
|------|------|------|
| `ultilogin.changepassword` | true | 允许修改密码 |
| `ultilogin.admin` | op | 管理员命令权限 |

## 数据存储

账号数据存储在 UltiTools 配置的数据存储中（JSON/SQLite/MySQL），包含以下字段：

| 字段 | 类型 | 描述 |
|------|------|------|
| `player_uuid` | VARCHAR | 玩家 UUID |
| `player_name` | VARCHAR | 玩家名称 |
| `password_hash` | VARCHAR(128) | 密码哈希值 |
| `salt` | VARCHAR(32) | 密码盐值 |
| `last_ip` | VARCHAR(45) | 最后登录 IP |
| `last_login` | BIGINT | 最后登录时间戳 |
| `register_time` | BIGINT | 注册时间戳 |
| `register_ip` | VARCHAR(45) | 注册 IP |
| `email` | VARCHAR(100) | 邮箱（预留） |
| `email_verified` | BOOLEAN | 邮箱验证状态 |
| `login_count` | INT | 登录次数 |
| `failed_attempts` | INT | 失败次数 |
| `last_failed_time` | BIGINT | 最后失败时间 |

## 登录模式说明

### 命令模式（默认）
- 玩家加入后显示文本提示
- 通过 `/login` 和 `/register` 命令操作
- 密码长度可配置（默认 6-32 字符）
- 支持任意字符作为密码

### GUI 模式（可选）

- 玩家加入后自动弹出 GUI 界面
- 通过点击数字按钮 (1-9) 输入密码
- 密码为固定位数的纯数字（默认 4 位）
- 输入完成后自动提交
- 关闭 GUI 后会自动重新打开

## 安全机制

### 密码加密
使用 SHA-256 算法 + 随机 Salt 进行密码哈希：
1. 每个账号生成唯一的 16 字节随机 Salt
2. 将密码与 Salt 拼接后进行 SHA-256 哈希
3. 哈希结果以 Base64 编码存储

### Failed-login protection / 登录失败保护

`security.max-login-attempts` is the threshold (default `5`; `0` disables new failure counting and locking).
`security.lockout-duration` is the lock duration in seconds (default `900`, or 15 minutes).

- **IP:** count failures and lock the source address. Accounts at that address share the count. A successful
  login does **not** clear it, closing the success-reset bypass (UltiKits/UltiLogin#50).
- **UUID:** count failures and lock the account, regardless of address. Success clears only that account's count.
- **BOTH (default):** count failures per **(source address, account UUID) pair**. Reaching the threshold locks
  only that pair: the same account at another address and a different account at the original address are not
  refused by that pair lock. Separately, reaching the same threshold of **different accounts** with retained
  failures at one address locks that whole address. Success clears only its own pair's count, never the
  address's collection of failed accounts.

IP-mode counts expire `lockout-duration` seconds after the address's latest failure. In BOTH, each account's
membership in an address's collection expires that many seconds after **that account's** latest failure at that
address. Unlocked UUID and pair counts do not time-expire: their own success or their corresponding lock's expiry
resets them. Each lock keeps its recorded unlock time; a success never removes or shortens it. These counts and
locks are in memory and do not survive a server restart.

**Upgrading:** an existing `login.yml` keeps its `security.lockout-type` value byte for byte, including `IP` or
lowercase `uuid`. Only a missing key gets `BOTH`. To opt in, explicitly set `security.lockout-type: BOTH` and run
`/ul reload UltiLogin`. Values are case-insensitive with surrounding spaces ignored. An unusable value is named in
a warning and uses `BOTH` in memory only; the value on disk is never repaired automatically. Recognised historical
shipped comments refresh to the new explanation; operator-written comments are kept.

**Address limits:** without proxy address forwarding, players share the proxy's observed address. IP locks and
BOTH's whole-address protection can therefore affect those players together. These address limits do not prevent
an attacker from distributing guesses over many addresses.

`security.max-login-attempts` 是阈值（默认 `5`；`0` 不再计入新的失败或建立新封禁）；
`security.lockout-duration` 是封禁秒数（默认 `900`，即 15 分钟）。

- **IP：** 按来源地址计数并封禁，同一地址的账号共用计数；登录成功不再清零，修复 UltiKits/UltiLogin#50 的绕过漏洞。
- **UUID：** 按账号计数并封禁，不区分地址；成功只清除自身账号计数。
- **BOTH（默认）：** 按 **（来源地址、账号 UUID）组合** 计数，达到阈值只封禁该组合，不影响其他地址的同一账号或原地址的其他账号。
  另按每个地址上仍保留失败记录的 **不同账号数** 计数，达到同一阈值则封禁整个地址。成功只清除自身组合计数，不清除地址集合。

IP 计数在该地址最近一次失败的 `lockout-duration` 秒后过期；BOTH 地址集合中的每个账号在该账号于该地址最近一次失败的相同时长后过期。
未封禁的 UUID 和组合计数不随时间过期，只在自身成功或对应封禁到期时重置。每项封禁保留自己的解锁时间，成功不会解除或缩短封禁；
这些计数和封禁只在内存中保存，重启后不保留。

**升级：** 现有 `login.yml` 的 `security.lockout-type` 值逐字节保留，包括 `IP`、小写 `uuid`；只有缺少该键时才写入 `BOTH`。
如需采用新默认，请主动设置 `security.lockout-type: BOTH` 并执行 `/ul reload UltiLogin`。值忽略大小写和两端空格；
无法使用的值会被警告指出，仅在内存中按 `BOTH` 执行，绝不自动修改磁盘值。已识别的旧版出厂注释会更新，服主自写注释保留。

**地址限制：** 代理未转发玩家地址时，玩家共用服务器看到的代理地址，因此 IP 封禁和 BOTH 的整地址防护会一起影响这些玩家。
这不能阻止攻击者使用大量不同地址分散猜测。

### Passwords in the server log / 服务器日志中的密码

Paper writes every player command line to the server's own console and `logs/latest.log`. While UltiLogin is enabled it keeps a filter on the server's log (Log4j's root
logger) that replaces the arguments of its password commands with `******`: `/login`, `/l`, `/register`, `/reg`,
`/changepassword`, `/changepw`, `/cpw`, `/setpassword`, `/setpw`, `/recover`, and `/logadmin reset` (also
`/loginadmin reset`), in any letter case and with or without a namespace such as `ultitools:`. The console then shows
`Steve issued server command: /login ******`, so a login attempt stays visible without its password. Other commands
are logged unchanged (a command of another plugin with one of these names is masked too). The list comes from the
commands' own declarations, so a new alias is covered automatically. The filter is removed when the module is
unloaded. If the server's logging is not Log4j Core, the module logs a warning at start-up and these lines are not
masked. Two cases the filter cannot cover are listed under "Known limitations" above.

On UltiTools-API 6.3.1 the module also registers `/login`, `/l`, `/register`, `/reg`, `/changepassword`, `/changepw`,
`/cpw`, `/setpassword`, `/setpw`, `/recover`, `/logadmin`, `/loginadmin` and `/regs` with the framework, which replaces
their arguments with `***` in everything it sends to UltiCloud (the panel console, error reports, chat). The local
console and log files keep the module's own `******` mask, and `/regs` stays readable locally.

Paper 会把玩家输入的每条命令写入服务器本地的控制台和 `logs/latest.log`。
UltiLogin 启用期间会在服务器日志（Log4j 根日志器）上挂一个过滤器，把其密码类命令的参数替换为 `******`：`/login`、`/l`、
`/register`、`/reg`、`/changepassword`、`/changepw`、`/cpw`、`/setpassword`、`/setpw`、`/recover` 以及 `/logadmin reset`
（也包括 `/loginadmin reset`），不区分大小写，带或不带 `ultitools:` 之类的命名空间前缀均可。控制台上显示为
`Steve issued server command: /login ******`，登录尝试仍然可见，但看不到密码。其他命令照常记录（其他插件中同名的命令也会被遮蔽）。
命令列表取自命令本身的声明，新增别名会自动覆盖。模块卸载时过滤器随之移除。若服务器的日志系统不是 Log4j Core，模块会在启动时
输出一条警告，这些命令行不会被遮蔽。过滤器无法覆盖的两种情况见上文"已知限制"。

在 UltiTools-API 6.3.1 上，模块还会把 `/login`、`/l`、`/register`、`/reg`、`/changepassword`、`/changepw`、`/cpw`、
`/setpassword`、`/setpw`、`/recover`、`/logadmin`、`/loginadmin` 和 `/regs` 登记到框架，框架发往 UltiCloud 的所有内容
（面板控制台、错误报告、聊天）中，这些命令的参数都会被替换为 `***`。本地控制台与日志文件仍使用模块自己的 `******` 遮蔽，
`/regs` 在本地照常可见。

### 会话保持

- 基于 IP + UUID 组合标识会话
- 会话有效期内无需重复登录
- 适用于短时间内重连的情况

## 依赖

- **UltiTools-API** 6.3.1 or later / 6.3.1 或更高版本 (required / 必需).
  This module declares `api-version: 631`, so UltiTools-API 6.3.0 and older refuse to load it with a warning that
  the UltiTools version is outdated. 本模块声明 `api-version: 631`，UltiTools-API 6.3.0 及更早版本会拒绝加载它，并警告
  UltiTools 版本过旧。
- Install: put `UltiLogin-<version>.jar` into `plugins/UltiTools/plugins/` and restart the server (`/ul reload` only
  reloads configuration; it does not load a new module jar). 安装：将 JAR 放入 `plugins/UltiTools/plugins/` 并重启服务器
  （`/ul reload` 只重载配置，不会加载新的模块 JAR）。

## 版本历史

### 未发布 / Unreleased

- 需要 UltiTools-API 6.3.1 或更高版本（`api-version: 631`）
- `/panel` 改用 UltiTools-API 的玩家链接接口：不再轮询，结果以事件送达；三种证明、完成结果、重复投递去重、迟到的完成、按原因区分的结束提示
- 网页创建的账号：提示设置“备用游戏密码”（UltiCloud 连不上时使用，不要与 UltiKits 密码相同）
- 建链接前先检查同一 IP 注册数上限
- 敏感命令登记到框架，上云内容中参数被遮蔽

### v1.0.0 (2026-02-15)

- 基础登录/注册功能
- 会话保持功能
- SHA-256 + Salt 密码加密
- 登录超时踢出
- 登录前操作限制
- GUI 数字键盘登录模式（`gui-mode.enabled`）
- 登录失败保护机制（IP/UUID 封禁）
- 管理员命令（reset/forcelogin/unregister/info）
- 密码验证逻辑支持 GUI/命令模式切换
- 邮箱绑定（`/regs`）与密码找回（`/recover`，使用 UltiTools-API 提供的 EmailService）
- 中英文语言文件

## 未来计划

- [ ] 二次验证 (2FA)
- [ ] 登录日志记录
- [ ] Web API 接口
