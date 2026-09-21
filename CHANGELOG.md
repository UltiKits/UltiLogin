# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- A player who has not logged in can no longer equip or remove an armor stand's items, interact with
  an entity at a precise point on its body, drag items inside an inventory, or swap their main hand
  and off hand. All four were still allowed: the login protection already refused ordinary entity
  interaction and ordinary inventory clicks, but Bukkit delivers each of these four through a
  separate event, so the existing guards never saw them. Armor-stand equipping is the one that was
  reported and reproduced — an unauthenticated player placed a block onto an armor stand and their
  held stack went down by one. Writing a book and writing a sign are now refused before login as
  well; both are expected to have been unreachable already, and are refused explicitly rather than
  left resting on that expectation (UltiKits/UltiLogin#24).
- 未登录的玩家现在无法再为盔甲架穿脱物品、对实体身体的精确位置进行交互、在物品栏内拖拽物品，也无法交换主手与
  副手的物品。此前这四种操作均可执行：登录保护已经拦截了普通的实体交互和普通的物品栏点击，但 Bukkit 通过四个
  独立的事件分发上述操作，因此原有的拦截从未收到它们。其中被报告并复现的是盔甲架穿戴——未登录玩家成功把一个
  方块装备到盔甲架上，其手持物品数量随之减少一个。此外，登录前书写成书与书写告示牌现在也会被拒绝：这两者原本
  预期就已无法触发，此处选择明确拒绝，而不是继续依赖该预期（UltiKits/UltiLogin#24）。

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
