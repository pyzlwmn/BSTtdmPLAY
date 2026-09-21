# 团队竞技（tdm）· FPSMatch 玩法追加模组

给 **我的世界 1.20.1 + Forge** 服务器用的团队竞技玩法，基于 [FPSMatch](https://github.com/PhasetransCrystal/FPSMatch) 框架开发。

- 玩法名（game type）：`tdm`
- mod id：`bstcsdmplay`
- 包名：`com.github.pyzlwmn.bstcsdmplay.TeamCompetition`

## 依赖

| 项 | 版本 |
|---|---|
| Minecraft | `1.20.1` |
| Forge | `47.4.10+` |
| **FPSMatch** | `1.26.9-SNAPSHOT`（`net.ptcrys:FPSMatch`，必须同版本客户端/服务端） |
| Kotlin for Forge | `4.11.0`（必需） |
| Modern UI | `1.20.1-3.12.0.1`（仅客户端需要） |
| TaCZ（枪械） | `1.1.7-hotfix`（可选，装了才有枪械/改装） |

> 本项目**不包含** FPSMatch 源码。构建时需要自己 clone FPSMatch 到工程根目录（composite build），或者直接依赖官方 Maven 快照。




## 功能

**对局**
- 两队对抗（红/蓝）；目标击杀数(30)、时限(5min)
- 开局前倒计时（期间免疫伤害），结束后显示 MVP/SVP 结算面板
- 开局方式：全员 `/ready` 才能开（有人没准备就开不了）／OP 可 `debug start`
- 死亡后自动回本队出生点、秒回重生、重生保护
- 离队/退出地图 → 自动传回出生点

**HUD / 界面**
- 顶部比分条（双方头像组 + 目标 + 剩余时间）、击杀提示（带武器图标，最多 5 条）
- Tab 面板：比分/剩余时间/头像/击杀/死亡/助攻/伤害，居中且超屏自动缩小

**背包装备（loadout）**
- 槽位固定：1 步枪 / 2 手枪 / 3 投掷物 / 4 道具 / 5 近战
- 配置文件 `config/bstcsdmplay/loadout.json`（每个槽位写哪些枪、弹药/备弹/开火模式、配件池、默认配件）
- **多背包**：每人可建多个背包（自定义名字，默认上限 5），每个背包独立保存每槽的枪与配件
- 皮肤联动：使用配套kubejs脚本，后期发布
- 按键 **`;`** 打开编辑界面（可在按键设置里改），也支持 `/tdmloadout`

**配件**
- 「配件编辑」页：大号 3D 模型预览（直接调用 TaCZ 的模型渲染器），按槽位类型（瞄具/枪口/枪托/握把/激光/弹匣）选择，只列**这把枪真能装**的配件
- 「改装」：打开 **TaCZ 原版改装面板** 关闭面板自动把改装结果存进当前背包该枪位，开局直接发这把成品



## 授权

**GPL-3.0**（见 `LICENSE`）。

本项目是 [FPSMatch](https://github.com/PhasetransCrystal/FPSMatch)（GPL-3.0）与 [BlockOffensive](https://github.com/PhasetransCrystal/BlockOffensive)（GPL-3.0）的衍生作品，按 GPL-3.0 要求继续以 GPL-3.0 开源；部分玩法结构参考了 BlockOffensive 的实现。

## 引用/参考/致谢

- [FPSMatch](https://github.com/PhasetransCrystal/FPSMatch) — 对局框架（地图/队伍/能力/HUD）
- [BlockOffensive](https://github.com/PhasetransCrystal/BlockOffensive) — CS 玩法参考
- [TaCZ (Timeless and Classics Zero)](https://github.com/MCModder-Zero/TimelessAndClassicsZero) — 枪械
- FPSMatch作者提供技术支持

## 作者

- 作者：pyzlwmn4031
