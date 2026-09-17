# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- Unloading this module (`/upm uninstall UltiLogin`, server shutdown, or a newer copy of the module
  replacing an older one at load) now unregisters its commands (`/login`, `/register`,
  `/changepassword`, `/logadmin`, `/recover`, `/regs`, `/panel`). Previously this module replaced the
  framework's unload method with one that only logged a line, so command unregistration was skipped on
  all three paths, and `/upm uninstall` and a newer copy replacing an older one also skipped
  unregistering its `LoginProtectionListener` (UltiKits/UltiLogin#29).
- 卸载本模块（`/upm uninstall UltiLogin`、关闭服务器，或加载时由本模块的新副本替换旧副本）现在会注销其命令
  （`/login`、`/register`、`/changepassword`、`/logadmin`、`/recover`、`/regs`、`/panel`）。此前本模块用一个
  只打印日志的方法替换了框架的卸载方法，因此三条路径都跳过了命令注销，`/upm uninstall` 和新副本替换旧副本时
  还跳过了 `LoginProtectionListener` 监听器的注销（UltiKits/UltiLogin#29）。

### Removed

- The module's own `UltiLogin 已禁用！` ("UltiLogin disabled!") console line on unload and its own
  `UltiLogin 配置已重载！` ("UltiLogin configuration reloaded!") console line on `/ul reload UltiLogin`
  (both printed in Chinese under either `language` setting), and the never-read `login_disabled` and
  `login_reloaded` language keys that described them. UltiTools 6.3.0 logs one reload line per module (`Module 'UltiLogin' reloaded.`). Reloading
  still re-reads `login.yml` into the running module, exactly as before: that was already done by the
  framework's reload method, which this module's override called first (UltiKits/UltiLogin#29).
- 移除本模块卸载时输出的"UltiLogin 已禁用！"控制台行、`/ul reload UltiLogin` 时输出的
  "UltiLogin 配置已重载！"控制台行，以及未被读取的 `login_disabled`、`login_reloaded` 语言键。
  UltiTools 6.3.0 会为每个模块输出一行重载日志（`Module 'UltiLogin' reloaded.`）。重载仍会像以前一样把
  `login.yml` 重新读入运行中的模块：此前本模块的重载方法已先调用框架的重载方法完成这一步
  （UltiKits/UltiLogin#29）。
