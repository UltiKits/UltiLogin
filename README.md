# UltiLogin

[![UltiTools-API](https://img.shields.io/badge/UltiTools--API-6.3.0%2B-blue)](https://github.com/UltiKits/UltiTools-Reborn)
[![Paper](https://img.shields.io/badge/Paper-1.21%2B-green)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://adoptium.net/)
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
| `/setpassword <新密码> <确认>` | `/setpw` | 无 | 为网页创建、尚无游戏密码的账号设置游戏密码 / set the first game password of an account created on the web |
| `/panel` | — | 无 | 获取 UltiCloud 网页面板链接（登录前可用）/ get an UltiCloud web panel link (usable before `/login`) |

### 管理员命令

| 命令 | 权限 | 描述 |
|------|------|------|
| `/logadmin reset <玩家> [密码]` | `ultilogin.admin` | 重置玩家密码 |
| `/logadmin forcelogin <玩家>` | `ultilogin.admin` | 强制登录玩家 |
| `/logadmin unregister <玩家>` | `ultilogin.admin` | 删除玩家账号 |
| `/logadmin info <玩家>` | `ultilogin.admin` | 查看玩家账号信息 |

## Web panel sign-in (`/panel`) / 网页面板登录

`/panel` gives the player a clickable UltiCloud link, valid for five minutes, for their own game name on this server.
It needs `ulticloud.enabled: true` in `login.yml` and the server signed in to UltiCloud (`ulticloud login` in the
console). Opening the link never logs anyone in by itself: the web page asks for a proof first, and the player is
logged in in game only after the page completes.

The web accepts one of three proofs:

1. **Logged in in game.** The player ran `/login` and then `/panel`. The page may link the game name to the UltiKits
   account the player signs in with (or creates).
2. **Already linked.** The game name is already linked to an UltiKits account on this server. The page asks for that
   account's password, or offers a device the account holder told it to remember (one click; nothing signs in by
   itself). Then the player is logged in in game.
3. **No game account yet.** The name has no UltiLogin account on this server. The page may create a new UltiKits
   account; the game then creates the player's UltiLogin account without a game password and logs them in. A web
   registration counts as an UltiLogin registration.

A name that has an UltiLogin account but is neither logged in in game nor linked is refused: the player runs `/login`
once, then `/panel` again. To move a game name to another UltiKits account, unlink it in the first account's settings,
sign in to the other account, and run `/panel` again.

What the player sees in game:

| Situation | Message (`language: en`) |
|---|---|
| Link delivered to a player who has not logged in | "Your login countdown is paused while the web panel link is open. You still cannot move or chat until you are logged in." |
| Signed in on the page | "You have been logged in via UltiCloud! (Player)" or "(Server Owner)" |
| Account created on the web | the line above, then "Your account was created on the web and has no game password yet. Set one with /setpassword <password> <confirm>, so you can also log in with /login." |
| Cancelled on the page | "You cancelled the web panel sign-in. The link stays valid until it expires, so you can still finish it in the browser." |
| Refused by the web | "The web panel refused this sign-in: …" with the reason |
| Link expired | "Your web panel link has expired. Run /panel again for a new one." |
| Countdown running again | "Your login countdown has resumed. Log in before it runs out." (with the time that was left, never a fresh full timeout) |
| The server's UltiCloud credential is refused | "UltiCloud refused this server's credential, so no panel link was created. Ask the server owner to run 'ulticloud login' in the console." (also logged to the console) |

**`/setpassword <new> <confirm>`.** An account created on the web has no game password, and `/login` refuses it
whatever is typed ("This account was created on the web and has no game password yet. …"); a refused try is not
counted as a wrong password. With `gui-mode.enabled: true` such an account is not shown the number pad at all: it
gets the same message on joining and whenever it is prompted again, so the player can type `/panel` (which is in the
shipped `allowed-commands`). While logged in, the player sets one with `/setpassword`, which follows the same password
rules as `/register`. Afterwards `/login` works. An account that already has a password uses `/changepassword`.

**If the panel is unavailable** and such a player has no game password yet: with `session-enabled: true` they are
logged in automatically when they rejoin from the same IP within `session-timeout`; otherwise an administrator runs
`/logadmin reset <player> <password>` and tells them the password.

**Older UltiLogin versions** (1.0.0 and earlier) keep working with the new panel under restricted rules: their links
can only sign in to an account the game name is already linked to, with that account's password. A server running
this version that is not signed in to UltiCloud gets the same restricted links. The old path is switched off some time
after this release.

`/panel` 为玩家提供一个可点击的 UltiCloud 链接，有效期五分钟，对应其在本服务器上的游戏名。需要在 `login.yml` 中设置
`ulticloud.enabled: true`，并且服务器已登录 UltiCloud（在控制台执行 `ulticloud login`）。仅打开链接不会让任何人登录：网页会先要求
一项证明，只有网页完成后玩家才会在游戏内登录。

网页接受以下三种证明之一：

1. **已在游戏内登录。** 玩家先执行 `/login` 再执行 `/panel`。网页可以把游戏名关联到玩家登录（或新建）的 UltiKits 账号。
2. **已关联。** 该游戏名在本服务器上已关联某个 UltiKits 账号。网页要求输入该账号的密码，或提供账号持有人让其记住的设备
   （需点击一次；不会自动登录）。之后玩家在游戏内登录。
3. **尚无游戏账号。** 该名字在本服务器上没有 UltiLogin 账号。网页可以新建 UltiKits 账号；随后游戏为玩家创建一个没有游戏密码的
   UltiLogin 账号并让其登录。网页注册视同 UltiLogin 注册。

已有 UltiLogin 账号、但既未在游戏内登录也未关联的名字会被拒绝：玩家先执行一次 `/login`，再执行 `/panel`。要把游戏名移到另一个
UltiKits 账号：在原账号的设置中解除关联，登录另一个账号，再执行 `/panel`。

游戏内可能看到的提示：链接发出时登录倒计时暂停（仍不能移动或聊天）；网页登录成功；网页创建账号后提示用 `/setpassword` 设置游戏
密码；网页取消（链接在到期前仍可完成）；网页拒绝及原因；链接过期；倒计时恢复（按暂停时剩余的时间，绝不重新计满）；服务器的
UltiCloud 凭据被拒绝（同时写入控制台）。

**`/setpassword <新密码> <确认>`。** 网页创建的账号没有游戏密码，无论输入什么 `/login` 都会拒绝，且不计为密码错误。在
`gui-mode.enabled: true` 下，这样的账号完全不会弹出数字键盘：进入服务器和每次再次提示时都会收到同一条提示，玩家因此可以输入
`/panel`（它在出厂的 `allowed-commands` 中）。玩家登录后用
`/setpassword` 设置游戏密码，规则与 `/register` 相同，之后即可用 `/login` 登录。已有密码的账号请使用 `/changepassword`。

**面板不可用时**，若这样的玩家还没有游戏密码：在 `session-enabled: true` 下，于 `session-timeout` 内从同一 IP 重进会自动登录；
否则由管理员执行 `/logadmin reset <玩家> <密码>` 并把密码告诉玩家。

**旧版 UltiLogin**（1.0.0 及更早）在新面板下仍可使用，但规则受限：其链接只能登录到游戏名已关联的账号，并需要该账号的密码。运行本版本但未登录 UltiCloud 的
服务器得到的链接同样受限。旧路径将在本版本发布后的某个时间关闭。

### Known limitations / 已知限制

- **A pending link pauses the login countdown for at most its lifetime.** A player who has not logged in can keep the
  countdown paused by running `/panel` again, five minutes per link. They still cannot move, chat or use commands other
  than the allowed ones while it is paused.
- **Registering in game while a web registration is pending keeps the in-game account.** If the player runs `/register`
  before the web registration completes, the account they registered in game is kept untouched (with that game
  password) and the web completion simply logs them in.
- **A per-IP registration limit also applies to web registrations** (see below): over the limit the web page reports
  the registration, but the game refuses the account and does not log the player in.
- **A session auto-login counts as "logged in in game" for `/panel`.** With `session-enabled: true` (the default), a
  player who rejoins from the same IP within `session-timeout` is logged in without a password, and may then link the
  name to an UltiKits account on the web. On an offline-mode server where players share a public IP, someone else
  joining under a name that is not linked yet, from that IP, within that window, could link it to their own account.
  On such a server turn sessions off (`session-enabled: false`) or link your name early.

- **待处理的链接最多按其有效期暂停登录倒计时。** 未登录的玩家可以再次执行 `/panel` 让倒计时继续暂停，每个链接五分钟；暂停期间仍
  不能移动、聊天或执行未放行的命令。
- **网页注册尚未完成时在游戏内注册，会保留游戏内账号。** 若玩家在网页注册完成前执行了 `/register`，游戏内注册的账号（及其游戏密码）
  保持不变，网页完成后只会让其登录。
- **同一 IP 注册数限制同样适用于网页注册**（见下文）：超出限制时网页显示注册成功，但游戏拒绝创建账号且不会让玩家登录。
- **会话自动登录在 `/panel` 中视为"已在游戏内登录"。** 在 `session-enabled: true`（默认）下，于 `session-timeout` 内从同一 IP
  重进的玩家无需密码即被登录，随后可在网页上把该名字关联到 UltiKits 账号。在玩家共用公网 IP 的离线模式服务器上，他人若在这段
  时间内从该 IP 以尚未关联的名字进入，就可能把它关联到自己的账号。此类服务器请关闭会话（`session-enabled: false`），或尽早关联
  自己的名字。

## 配置文件

配置文件位于 `plugins/UltiTools/config/login.yml`

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
  lockout-type: IP          # 封禁类型：IP / UUID / BOTH

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

### 登录失败保护

- 连续失败达到阈值后触发封禁
- 支持按 IP、UUID 或两者同时封禁
- 封禁时长可配置（默认 15 分钟）
- 成功登录后清除失败记录

**By design (UltiKits/UltiLogin#45, #48):** the failure count follows what `security.lockout-type` locks.
`IP` counts and locks per IP address, so accounts behind one address (a household, a school, a proxy) share
one count and one player's wrong passwords can lock the others out. `UUID` counts and locks per account, so
one account's lock or wrong passwords never count against another account at the same address; use it when
accounts share addresses. `BOTH` keeps both counts: the address is locked when its count reaches the limit and
the account when its own count does. Config comments follow the server's `language`; the sample above shows
them in Chinese.

**设计如此（UltiKits/UltiLogin#45、#48）：** 失败次数按 `security.lockout-type` 所封禁的对象计数。
`IP` 按 IP 地址计数并封禁，因此同一地址（家庭、学校、代理）背后的账号共用一个计数，一名玩家输错密码可能把其他人一起锁住。
`UUID` 按账号计数并封禁，一个账号的封禁或输错密码不会算到同一地址上的其他账号头上；账号共用地址时请使用它。
`BOTH` 两种计数都保留：地址的计数达到上限时封禁该地址，账号自己的计数达到上限时封禁该账号。
配置文件中的注释跟随服务器的 `language`；上面的示例以中文显示。

### 会话保持

- 基于 IP + UUID 组合标识会话
- 会话有效期内无需重复登录
- 适用于短时间内重连的情况

## 依赖

- **UltiTools-API** 6.3.0 or later / 6.3.0 或更高版本 (required / 必需), on **Paper 1.21+** with **Java 21+**.
  This module declares `api-version: 630`, so an older framework refuses to load it with a warning that the
  UltiTools version is outdated. 本模块声明 `api-version: 630`，更早的框架会拒绝加载它，并警告 UltiTools 版本过旧。
- Install: put `UltiLogin-<version>.jar` into `plugins/UltiTools/plugins/` and restart the server (`/ul reload` only
  reloads configuration; it does not load a new module jar). 安装：将 JAR 放入 `plugins/UltiTools/plugins/` 并重启服务器
  （`/ul reload` 只重载配置，不会加载新的模块 JAR）。

## 版本历史

### v1.1.0
- 新增 GUI 数字键盘登录模式
- 新增登录失败保护机制（IP/UUID 封禁）
- 新增管理员命令（reset/forcelogin/unregister/info）
- 优化密码验证逻辑（支持 GUI/命令模式切换）
- 扩展语言文件支持

### v1.0.0
- 基础登录/注册功能
- 会话保持功能
- SHA-256 + Salt 密码加密
- 登录超时踢出
- 登录前操作限制

## 未来计划

- [ ] 邮箱绑定与密码找回（依赖 UltiTools-API 提供的 EmailService）
- [ ] 二次验证 (2FA)
- [ ] 登录日志记录
- [ ] Web API 接口
