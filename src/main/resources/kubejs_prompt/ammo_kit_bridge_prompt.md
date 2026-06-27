# KubeJS 侧修改提示词（修正版）

## 背景

Java 模组 `siege_tools` 新增了物品 `siege_tools:ammo_kit`（弹药补给包）。

**Java 侧已实现**：物品、实体、物理、扫描、队伍判定、弹药发放逻辑。
**KubeJS 侧需要做**：新建一个独立文件，构建弹药配置映射表，注册到 Java 的 `SiegeToolsAPI`。

> ⚠ **注意**：以下所有操作均为**新建文件**，不会修改任何已有的 KubeJS 文件。

---

## 你需要做的操作

### 新建文件：`server_scripts/profession/z_ammo_kit_bridge.js`

在 `server_scripts/profession/` 目录下新建 `z_ammo_kit_bridge.js`，内容如下：

```javascript
// ============================================================
// z_ammo_kit_bridge.js — 弹药补给包弹药配置桥接
// 本文件为独立文件，不修改任何已有脚本
// 加载顺序：在所有 prof_configs/*.js 之后加载（z_ 前缀 + 上级目录）
// ============================================================
// 功能：遍历 PROF_CONFIGS 和 VANILLA_WEAPON_AMMO，
//       将扁平化的弹药配置映射表注册到 Java 的 SiegeToolsAPI。
//       siege_tools:ammo_kit 物品通过此映射表查询弹药信息。
// ============================================================

// 防御检查：确保 PROF_CONFIGS 已构建完成
if (typeof PROF_CONFIGS === 'undefined') {
    console.warn('[AmmoKitBridge] PROF_CONFIGS 未定义，跳过弹药注册');
    return;
}

var ammoConfigMap = {};

// ── 1. 遍历所有职业的 TACZ 枪械配置 ──
//     从 PROF_CONFIGS[职业].guns.{类别}.{武器ID}.ammo 读取
var PROF_TAG_LIST = PROF_TAG_LIST || [];
for (var pi = 0; pi < PROF_TAG_LIST.length; pi++) {
    var prof = PROF_TAG_LIST[pi];
    var cfg = PROF_CONFIGS[prof];
    if (!cfg || !cfg.guns) continue;

    for (var cat in cfg.guns) {
        for (var wid in cfg.guns[cat]) {
            var gunCfg = cfg.guns[cat][wid];
            if (!gunCfg.ammo) continue;

            ammoConfigMap[wid] = {
                type: 'tacz',
                ammoId: gunCfg.ammo.ammoId || '',
                main: gunCfg.ammo.main || 0,
                level: gunCfg.ammo.level || 0,
                gunId: gunCfg.gunId || '',
            };
        }
    }
}

// ── 2. 添加非 TACZ 武器弹药 ──
//     从 VANILLA_WEAPON_AMMO 读取（由 z_tacz_config_build.js 构建）
if (typeof VANILLA_WEAPON_AMMO !== 'undefined') {
    for (var wid in VANILLA_WEAPON_AMMO) {
        var ammoCfg = VANILLA_WEAPON_AMMO[wid];
        ammoConfigMap[wid] = {
            type: 'vanilla',
            item: ammoCfg.item || '',
            count: ammoCfg.count || 0,
        };
    }
}

// ── 3. 注册到 Java 的 SiegeToolsAPI ──
try {
    var $SiegeToolsAPI = Java.loadClass('com.xkmxz.siege_tools.api.SiegeToolsAPI');
    $SiegeToolsAPI.clearAndRegister(JSON.stringify(ammoConfigMap));
    console.log('[AmmoKitBridge] 已注册 ' + Object.keys(ammoConfigMap).length + ' 个武器的弹药配置');
} catch (e) {
    console.warn('[AmmoKitBridge] 注册失败（siege_tools 模组可能未安装）: ' + e);
}
```

---

## 为什么这个方案是安全的

| 特性 | 说明 |
|------|------|
| ✅ **不修改任何现有文件** | 纯新建文件，零侵入 |
| ✅ **独立的职责** | 只负责"读已有数据 + 注册到 Java"，不碰任何业务逻辑 |
| ✅ **防御性编程** | `typeof PROF_CONFIGS === 'undefined'` 检查，确保数据就绪 |
| ✅ **失败安全** | `try/catch` 包裹 Java 调用，模组未安装也不影响 |
| ✅ **正确的加载顺序** | 文件在 `profession/` 目录（不是 `prof_configs/`），由于 `z_` 前缀，必然在 `prof_configs/z_tacz_config_build.js` **之后**加载，此时 `PROF_CONFIGS` 和 `VANILLA_WEAPON_AMMO` 已就绪 |
| ✅ **reload 安全** | 使用 `clearAndRegister()`，`/kubejs reload` 后清空旧数据重新注册 |

---

## 数据流

```
KubeJS 加载顺序：
  1. profession/config/a_tacz_config.js     ← 基础工具
  2. profession/prof_configs/b_*.js          ← 各职业配置
  3. profession/prof_configs/z_tacz_config_build.js  ← 构建查表函数
  4. profession/z_ammo_kit_bridge.js         ← ★ 本文件：读数据 + 注册到 Java
  5. profession/profequip_cmd.js             ← 装备发放（不受影响）
  ...

z_ammo_kit_bridge.js 执行：
  PROF_CONFIGS 已就绪 ──→ 遍历构建 ammoConfigMap
  VANILLA_WEAPON_AMMO 已就绪 ──→ 添加非 TACZ 弹药
                              ↓
  Java.loadClass('SiegeToolsAPI').clearAndRegister(JSON)
                              ↓
  Java 侧 Map<String, AmmoConfig> 就绪
                              ↓
  AmmoKitItem 右键玩家 → 从 player.persistentData 读取武器 ID
  AmmoKitEntity 扫描    → 查 Map 获得弹药配置 → 发放弹药
```

---

## 调试方法

1. 启动服务器后，检查日志中是否有：
   - `[AmmoKitBridge] 已注册 N 个武器的弹药配置`（成功）
   - 或 `[AmmoKitBridge] PROF_CONFIGS 未定义`（加载顺序问题）
   - 或 `[AmmoKitBridge] 注册失败`（模组未安装）

2. 手持 `siege_tools:ammo_kit` 右键队友测试

3. 潜行+右键投掷放置，等待几秒观察弹药是否补充

4. 配置调整：`siege-tools-common.toml` 中的 `placed.scanRange`、`placed.scanInterval`、`supply.*` 等

5. 修改本文件后执行 `/kubejs reload` 即可重新注册，无需重启
