# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- A player who has not logged in can no longer equip or remove an armor stand's items, interact with
  an entity at a precise point on its body, or swap their main hand and off hand. All three were
  still allowed: the login protection already refused ordinary entity interaction, but Bukkit
  delivers each of these through a separate event, so the existing guards never saw them.
  Armor-stand equipping is the one that was reported and reproduced — an unauthenticated player
  placed a block onto an armor stand and their held stack went down by one.
- Writing a sign is now refused before login. Another plugin can open a sign editor for a player who
  has not logged in yet, which follows no interaction of the player's own, so nothing else refused
  it. Opening the editor is refused as well as the write.
- Also refused before login, though no way to reach them was demonstrated and none may exist:
  dragging items inside an inventory, writing a book, middle-click item pick, swapping the held item
  with an equipment slot, and recipe-book clicks. These are refused explicitly rather than left
  resting on the expectation that other guards make them unreachable (UltiKits/UltiLogin#24).
- 未登录的玩家现在无法再为盔甲架穿脱物品、对实体身体的精确位置进行交互，也无法交换主手与副手的物品。此前这三种
  操作均可执行：登录保护已经拦截了普通的实体交互，但 Bukkit 通过各自独立的事件分发上述操作，因此原有的拦截从未
  收到它们。其中被报告并复现的是盔甲架穿戴——未登录玩家成功把一个方块装备到盔甲架上，其手持物品数量随之减少一个。
- 登录前书写告示牌现在会被拒绝。其他插件可以为尚未登录的玩家打开告示牌编辑界面，这一路径不经过该玩家自身的任何
  交互，因此此前没有任何拦截。除写入之外，打开编辑界面本身也会被拒绝。
- 以下操作在登录前同样会被拒绝，但并未证实存在可触发的路径，也可能本就不存在：在物品栏内拖拽物品、书写成书、
  中键取物、将手持物品与装备栏互换，以及配方书点击。此处选择明确拒绝，而不是继续依赖"其他拦截使其无法触发"这一
  预期（UltiKits/UltiLogin#24）。
- After `/upm uninstall UltiLogin`, this module's commands (`/login`, `/register`,
  `/changepassword`, `/logadmin`, `/recover`, `/regs`, `/panel`) are now really removed and its
  `LoginProtectionListener` stops firing. Previously this module replaced the framework's unload
  method with one that only logged a line, so both stayed active until the server restarted
  (UltiKits/UltiLogin#29).
- 执行 `/upm uninstall UltiLogin` 后，本模块的命令（`/login`、`/register`、`/changepassword`、
  `/logadmin`、`/recover`、`/regs`、`/panel`）现在会被真正移除，其 `LoginProtectionListener` 监听器也不再
  触发。此前本模块用一个只打印日志的方法替换了框架的卸载方法，因此两者都会一直保持生效，直到服务器重启
  （UltiKits/UltiLogin#29）。

### Removed

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
