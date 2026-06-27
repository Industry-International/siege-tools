# KubeJS 使用指南 — 总览

本文档集合是服务器所有 KubeJS 脚本模块的使用说明，**每个模块独立成文**，通过超链接互相引用。

---

## 模块索引

| 模块 | 说明 | 对应代码路径 |
|------|------|-------------|
| [**模块管理器**](./模块管理器-使用教程.md) | 通过 `/module` 命令控制各模块启用/停用，数据包驱动 | `server_scripts/module_manager/` |
| [**职业系统**](./profession-使用教程.md) | 职业选择 GUI、武器配置、配件改装、背包系统、装备发放 | `server_scripts/profession/` |
| [**队伍选择器**](./team-使用教程.md) | 队伍选择 GUI（进攻方/防守方/观战），数据包协作 | `server_scripts/team/` |
| [**队伍复活券系统**](./team_revive-使用教程.md) | 复活券池管理、死亡消耗、队伍淘汰触发 | `server_scripts/team_revive/` |
| [**SBW 载具自动部署**](./sbw_vehicle-使用教程.md) | SBW 载具自动部署/重生、模板化 NBT 配置（能量、弹药、预装填） | `server_scripts/sbw_vehicle/` |
| [**指令使用**](./指令使用.md) | 管理员指令大全（`/module` `/profequip` `/kubejsadmin` `/team_revive` `/sbw_vehicle`） | 各模块内 |
| [**数据存储说明**](./数据存储.md) | 持久化数据存储机制、数据结构、读写接口 | `server.persistentData` |
| [**TACZ 枪械配件数据**](./tacz枪械配件数据-使用指南.md) | 枪械 GunId、配件槽位、弹药数据查询 | `tacz所有的枪械配件数据/` |
| [**启动脚本配置**](./startup_configs-使用教程.md) | 物品注册、海战平衡方块属性、TAOV 武器参数 | `startup_scripts/` |
| [**可调用接口参考**](./可调用接口参考.md) | 全局 API/变量/函数/配置总览，新模块开发速查 | 全模块 |

---

## 加载顺序说明

```
startup_scripts/          ← 游戏启动时加载（物品注册、全局配置）
    ├── src/item/         ← 注册自定义物品
    └── *.js              ← 方块属性覆写、全局变量

server_scripts/           ← 服务器启动时加载
    ├── module_manager/   ← 模块管理器（按文件名前缀控制顺序）
    ├── profession/       ← 职业系统（按文件名前缀控制顺序）
    ├── team/             ← 队伍选择器
    ├── team_revive/      ← 队伍复活券系统
    ├── sbw_vehicle/      ← SBW 载具自动部署系统
    └── taov_returned_repair_parts.js  ← TAOV 修复零件
```

> 详细加载顺序参见各模块文档。

---

## 语言文件

所有文本均遵循 i18n 规范，翻译键定义在：

- `assets/kubejs/lang/zh_cn.json` — 简体中文
- `assets/kubejs/lang/en_us.json` — 英语

修改语言文件后需按 **`F3+T`** 刷新资源包。

---

## 快速参考

| 操作 | 指令 |
|------|------|
| 重载脚本 | `/kubejs reload` |
| 刷新语言文件 | `F3+T` |
| 查看标签 | `/tag <玩家名> list` |
| 查看职业配置 | `/profequip list` |
| 查看复活券状态 | `/team_revive status` |
| 查看载具状态 | `/sbw_vehicle status` |
| 强制重新部署载具 | `/sbw_vehicle redeploy` |

---

> 各模块的详细使用说明请点击上方链接查看对应文档。
