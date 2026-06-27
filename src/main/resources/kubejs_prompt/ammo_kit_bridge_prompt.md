# KubeJS 侧修改提示词（v3 — KubeJS 插件直读）

## 新架构概述

**桥接脚本 `z_ammo_kit_bridge.js` 不再需要！** Java 侧通过 KubeJS 插件系统直接读取数据。

### 数据流

```
KubeJS 运行 → z_tacz_config_build.js 执行
             → 构建 ammoConfigMap（已有逻辑）
             → 写入 global.siege_tools_ammo_configs     ← 你加这行
                                        ↓
Java 插件 (SiegeToolsKubeJSPlugin) 在 afterScriptsLoaded() 中
             → 从 BuiltinKubeJSPlugin.GLOBAL 读取
             → 调用 SiegeToolsAPI.clearAndRegister()
             → AMMO_CONFIG_MAP 就绪
                                        ↓
玩家交互时:
  Java 通过 WithPersistentData 读取 player.persistentData
             → 获取 mainWeapon / offhandWeapon / team
  Java 查 AMMO_CONFIG_MAP → 获得弹药配置 → 发放弹药
```

---

## 你需要做的操作

### 1. 在 `z_tacz_config_build.js` 末尾加一行

找到 `profession/prof_configs/z_tacz_config_build.js`，在文件末尾（所有构建逻辑之后）添加：

```javascript
// ★ 弹药补给包：将扁平化的弹药配置写入 global，供 siege_tools 模组读取
var ammoConfigMap = {};
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
                offhand: gunCfg.ammo.offhand || 0,
                level: gunCfg.ammo.level || 0,
                gunId: gunCfg.gunId || '',
            };
        }
    }
}
// 添加非 TACZ 武器弹药（从 VANILLA_WEAPON_AMMO）
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
global.siege_tools_ammo_configs = ammoConfigMap;
// 注意：这里直接赋 JS 对象，Java 侧的 Gson 会自动解析
```

### 2. 删除桥接脚本

删除 `server_scripts/profession/z_ammo_kit_bridge.js`（不再需要）

### 3. 确认 persistentData 写入

确保以下 KubeJS 代码正常执行（这些应该已经有了）：

| 数据 | 写入位置 | 来源文件 |
|------|---------|---------|
| `player.persistentData.team` | `team_selector_gui.js:69` | `player.persistentData.team = team` |
| `player.persistentData.profession` | `profession_gui.js:292` | `player.persistentData.profession = prof.id` |
| `player.persistentData.mainWeapon` | `profession_gui.js:376` | `player.persistentData.mainWeapon = wp.id` |
| `player.persistentData.offhandWeapon` | `profession_gui.js:474` | `player.persistentData.offhandWeapon = wp.id` |
| `player.persistentData.specialWeapon` | `profession_gui.js:573` | `player.persistentData.specialWeapon = wp.id` |

---

## 调试方法

启动后看日志：

```
[SiegeToolsPlugin] KubeJS 服务端脚本加载完成，读取弹药配置...
[SiegeToolsPlugin] 从 global 读取到弹药配置 JSON, 长度=XXX
[SiegeToolsAPI] 已注册 N 个武器的弹药配置
```

如果看到 `global.siege_tools_ammo_configs 未找到` → 检查 `z_tacz_config_build.js` 中 `global.siege_tools_ammo_configs` 是否写入了。

投掷弹药箱后看：

```
[SiegeToolsAPI] KubeJS data dump: mainWeapon=[m4a1] ...
[SiegeToolsAPI] refillPlayerAmmo: main=[m4a1] ...
[SiegeToolsAPI] 发放弹药盒: GunId=tacz:m4a1, AmmoCount=210, AmmoLevel=2
```
