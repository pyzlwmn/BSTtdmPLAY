# 团队竞技（TDM）—— 基于 FPSMatch 的玩法追加模组

为 **我的世界 1.20.1 + Forge** 打造的团队竞技玩法，基于 [FPSMatch](https://github.com/PhasetransCrystal/FPSMatch) 框架开发。

- 玩法类型（game type）：`tdm`
- mod id：`filtdm`
- 主包：`com.github.pyzlwmn.filtdm`

## 依赖

| 项 | 版本 |
|---|---|
| Minecraft | `1.20.1` |
| Forge | `47.4.10+` |
| **FPSMatch** | `1.26.9-SNAPSHOT`（`net.ptcrys:FPSMatch`，客户端/服务端需同版本） |
| Kotlin for Forge | `4.11.0`（必需） |
| Modern UI | `1.20.1-3.12.0.1`（仅客户端需要） |
| TaCZ（枪械） | `1.1.7-hotfix`（可选，装了才有枪械/配件） |

> 本项目**不内置** FPSMatch 源码。编译时需要自己 clone FPSMatch 到工程根目录做 composite build，或直接走官方 Maven 仓库。

## 玩法

**对局**

- 多队对抗，按击杀数（默认 30）或时间（5 分钟）判定胜负
- 开局前有倒计时，期间免疫伤害；结束后展示 MVP/SVP 与结算
- 两种开始方式：全员 `/ready` 就能开局（不用管理员），或 OP 用 `debug start`
- 对局结束后自动关闭计分板、清空状态、重置玩家
- 死亡 / 退出地图 → 自动返回出生点

**HUD / 界面**

- 屏幕上方常驻比分板（双方头像数 + 目标 + 剩余时间），击杀提示带武器图标，停留 5 秒
- Tab 计分板：比分 / 剩余时间 / 头像 / 击杀 / 死亡 / 助攻 / 伤害，人数多了自动缩小

**背包装配（loadout）**

- 槽位固定：`1` 主武器 / `2` 副武器 / `3` 投掷物 / `4` 医疗 / `5` 战术
- 配置文件 `config/filtdm/loadout.json`，每个槽位写有哪些枪、弹药、配件、射击模式等，热重载，默认见示例
- **多背包**：每名玩家可建多个背包，各自独立，默认最多 5 个，每个背包单独配每把枪的配件
- 皮肤系统可配合外部脚本挂到服务端
- 按 **`;`** 打开编辑界面（仅准备阶段可用），也支持 `/tdmloadout`

**配件**

- 完整的配件编辑页：右侧 3D 枪械预览，直接调用 TaCZ 的模型渲染，可旋转、查看枪口 / 枪托 / 握把 / 激光 / 扩容弹匣等，只有**已安装枪械的槽位**可选
- 配件池走 **TaCZ 原生配件表**，关闭了自动择装配池，进游戏前配置好枪械即可直接反成品

## 本次更新

**死亡与复活**

- 死亡后不再瞬间复活，改为**固定视角延迟复活**：先播放 2 秒镜头演出，再定格 3 秒，共 5 秒后回到出生点
- 死亡期间锁定视角与操作，避免死亡瞬间视角乱飞
- 入队、每轮重置、死亡复活三处都会**回满血**（生命值 + 饱食度 + 清除药水效果 + 灭火）

**弹药系统**

- 弹匣容量改为读取**枪械实际可装弹数**，包含扩容配件加成（如沙漠之鹰基础 7 发，扩容后 8 / 10 / 12 发）
- 备弹统一为**弹匣容量 × 5**，随扩容自动变化
- 修掉发枪时弹药没写入的问题，现在不论走存档还原还是按配置生成，弹药都会正确填充
- 与皮肤系统完全兼容，只写弹药数据，不动皮肤标识

**背包与对局**

- 对局中可以**切换背包**，但不能编辑或改装（编辑仅限准备阶段）
- 加入队伍时自动**清空背包并切换到冒险模式**；每轮重置、死亡复活同样执行一遍
- 移除了切换背包、改装、对局状态等所有**聊天栏提示文本**，界面更干净

**工程**

- 包名统一重构为 `filtdm`，旧包已完全移除
- 清理了全工程源码注释，代码体积更小

## 授权

**GPL-3.0**（见 `LICENSE`）。

本项目基于 [FPSMatch](https://github.com/PhasetransCrystal/FPSMatch)（GPL-3.0）与 [BlockOffensive](https://github.com/PhasetransCrystal/BlockOffensive)（GPL-3.0）开发，属衍生作品，遵循 GPL-3.0 要求以 GPL-3.0 开源。玩法结构参考了 BlockOffensive 的实现。

## 链接 / 参考 / 致谢

- [FPSMatch](https://github.com/PhasetransCrystal/FPSMatch) —— 对局框架（地图 / 队伍 / 计分 / HUD）
- [BlockOffensive](https://github.com/PhasetransCrystal/BlockOffensive) —— CS 玩法参考
- [TaCZ (Timeless and Classics Zero)](https://github.com/MCModder-Zero/TimelessAndClassicsZero) —— 枪械
- FPSMatch 作者提供的技术支持

## 作者

- pyzlwmn4031
