---
name: 基础工程规范
description: 在 1.21.1-NeoForge + KubeJS 7 环境下开发时的基础环境信息、文档阅读规则和代码规范。适用于所有与该项目相关的编码任务。
---

# 基础工程规范

本项目运行在 **1.21.1-NeoForge_21.1.233 + KubeJS 7** 环境下。本 Skill 提供开发时必须遵守的基础规范。

## 1. 基础环境

- **当前编程环境**: 1.21.1-NeoForge_21.1.233，KubeJS 7
- **已安装 ProbeJS**：可进行代码提示及读取游戏内物品
- **日志路径**: `../logs/kubejs/`（`logs` 文件夹在项目根目录的上级）

## 2. 文档阅读规则

### 2.1 文档链接均为首页
- 提供的链接都是**文档首页**，需要先获取首页的**导航标签（tabs/links）**，然后根据标签找到对应的子页面，再跳转到子页面阅读详细内容。
- 不要只读首页就认为已掌握内容，必须深入相关子页面。

### 2.2 LDLib2 文档
- **LDLib2 是本项目编写自定义 GUI 的首选方案**（参见 `LDLib2 开发指引` skill）
- 离线文档：`给AI阅读的文档/ldlib/`（`agent_guide_2.html` + `ldlib2.html`）
- 官方首页：<https://low-drag-mc.github.io/LowDragMC-Doc/ldlib2/>
- 访问方式：先读首页获取导航结构，再按需跳转子页面（如 `ui/preliminary/data_bindings/`、`ui/components/text-field/`、`ui/agent_guide/` 等）

### 2.3 KubeJS 文档
- 离线文档（已生成）：`./documentation/index.html`（需要时阅读）
- 百科参考帖子：
  - <https://www.mcmod.cn/post/6142.html>
  - <https://www.mcmod.cn/post/5624.html>

### 2.4 已声明变量/常量记录
- 路径：`./给AI阅读的文档/const.txt`
- 每次完成任务后必须更新该文件
- 注意区分**全局变量**和**局部变量**

### 2.5 ProbeJS 提示文件
- 路径：`./给AI阅读的文档/.probe/`
- 包含了丰富的类型定义（`*.d.ts`），在需要确认 Java 类的方法、构造器、字段时优先查阅
- 仅在必要时阅读，不要无意义扫描

### 2.6 踩坑记录（必查！）
- 路径：`./.agents/skills/1.21.1-KubeJS7 踩坑记录/SKILL.md`
- **每次编写涉及实体/NBT/Level API 的代码前，必须查阅此文档**
- 该文档记录了在 1.21.1 + KubeJS 7 环境下已验证的 API 不兼容问题
- 如果遇到新的运行时报错，先查该文档是否已有记录：
  - 有 → 按记录修复
  - 有但花样不同 → 追加新条目
  - 无 → 新增条目

## 3. TaCZ（枪械）相关规则

- **未涉及 TaCZ 时不需要阅读以下内容**
- TaCZ 的枪包路径：`../tacz/`
- 所有枪械本质上是同一个物品 `tacz:modern_kinetic_gun`，通过 NBT 的 `GunId` 区分（如 `"tacz:ak47"`）
- 示例 NBT：
  ```
  GunId: "tacz:ak47", GunFireMode: "AUTO", GunCurrentAmmoCount: 30, HasBulletInBarrel: 1b
  ```
- 更多值自行查阅枪包格式（阅读前请先理解 `tacz 1.1.7 枪包格式`）
- TaCZ 官方文档：<https://tacwiki.mcma.club/zh/gunpack/60_recipe.html>
- TaCZ 相关物品**除非特殊要求，否则不需要额外 i18n**

## 4. 代码规范

### 4.1 i18n（国际化）
- 生成的代码必须满足 i18n 规范
- 同时生成语言文件，需包含 `en_us` 和 `zh_cn`

### 4.2 兼容性
- 所有代码确保在 1.21.1-NeoForge 环境下兼容 KubeJS 7 的 API

### 4.3 日志阅读
- 阅读日志并尝试分析问题，然后向用户提问，得到允许后再尝试修复问题
- 使用 `player.tell(Component.string('§e[DEBUG] ...'));` 进行调试输出
