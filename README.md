# FILTdm团队竞技—— 基于 FPSMatch 的玩法追加模组

为 **我的世界 1.20.1 + Forge** 打造的团队竞技玩法，基于 [FPSMatch](https://github.com/PhasetransCrystal/FPSMatch) 框架开发。

- 玩法类型（game type）
- - 5 v 5 团队竞技 ： `tdm`
- - 2 v 2 搭档模式 ： `solo`
- - 1 v 1 单挑 ： `pair`
- mod id：`filtdm`

## 依赖

| 项 | 版本 |
|---|---|
| Minecraft | `1.20.1` |
| Forge | `47.4.10+` |
| **FPSMatch** | `1.26.9-SNAPSHOT`（`net.ptcrys:FPSMatch`，客户端/服务端需同版本） |
| Kotlin for Forge | `4.11.0`（必需） |
| Modern UI | `1.20.1-3.12.0.1`（仅客户端需要） |
| TaCZ（枪械） | `1.1.7-hotfix` |


## 玩法

**对局**

- 两队对抗，按击杀数（默认 30）或时间（5 分钟）判定胜负
- 开局前有倒计时，期间免疫伤害；结束后展示 MVP/SVP 与结算
- 两种开始方式：对局满人 或 OP 用 `debug start`
- 死亡 / 退出地图 → 自动返回出生点

**HUD**

- 屏幕上方常驻比分板（双方头像数 + 目标 + 剩余时间），击杀提示带武器图标
- Tab 计分板：比分 / 剩余时间 / 头像 / 击杀 / 死亡 / 助攻 / 伤害

**背包装配（loadout）**

- 槽位固定：`1` 主武器 / `2` 副武器 / `3` 道具 / `4` 道具 / `5` 战术
- 配置文件 `config/filtdm/loadout.json`，每个槽位写有哪些枪、弹药、配件、射击模式等，热重载，默认见示例
- **多背包**：每名玩家可建多个背包，各自独立，最多 5 个
- 默认按 **`;`** 打开编辑界面（仅准备阶段可用）

**配件**

- 完整的配件编辑页：右侧 3D 枪械预览，可旋转、查看枪口 / 枪托 / 握把 / 激光 / 扩容弹匣等，只有**已安装枪械的槽位**可选
- 配件池走 **TaCZ 原生配件表**，关闭了自动择装配池

**背包与对局**

- 一名玩家最多5个背包，可配置5个槽位
- 可自定义枪械改装

## 本次更新





**工程**

- 包名统一重构为 `filtdm`，旧包已完全移除

**更新**
- 添加玩法单挑、搭档模式，重排ui
- 皮肤系统：皮肤系统可配合外部脚本挂到服务端（本mod使用kubejs，需要教程私信作者）
- 加入队伍时自动**清空背包并切换到冒险模式**；每轮重置、死亡复活同样执行一遍
- 添加 ***死亡动画*** 与 ***尸体***
- 入队、每轮重置、死亡复活三处都会**回满血**（生命值 + 饱食度 + 清除药水效果 + 灭火）
- 配件编辑添加枪械数据面板

## 授权

**GPL-3.0**（见 `LICENSE`）。

本项目基于 [FPSMatch](https://github.com/PhasetransCrystal/FPSMatch)（GPL-3.0）与 [BlockOffensive](https://github.com/PhasetransCrystal/BlockOffensive)（GPL-3.0）开发，属衍生作品，遵循 GPL-3.0 要求以 GPL-3.0 开源。代码实现参考了 BlockOffensive 的实现。

## 链接 / 参考 / 致谢

- [FPSMatch](https://github.com/PhasetransCrystal/FPSMatch) —— 对局框架（地图 / 队伍 / 计分 / HUD）
- [BlockOffensive](https://github.com/PhasetransCrystal/BlockOffensive) —— 玩法实现参考
- [TaCZ (Timeless and Classics Zero)](https://github.com/MCModder-Zero/TimelessAndClassicsZero) —— 枪械
- FPSMatch 作者提供的技术支持

## 作者

- pyzlwmn4031
