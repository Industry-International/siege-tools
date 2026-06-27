# KubeJS 侧修改提示词

## 背景

Java 模组 `siege_tools` 新增了物品 `siege_tools:ammo_kit`（弹药补给包），功能：
1. 右键点击同队玩家 → 直接为其补充弹药（主/副/特殊武器可分别开关）
2. 潜行+右键投掷 → 放置一个弹药箱实体，持续为范围内同队玩家补充弹药

**Java 侧已实现**：物品、实体、物理、扫描、队伍判定、弹药发放逻辑。
**KubeJS 侧需要做**：构建弹药配置映射表，注册到 Java 的 `SiegeToolsAPI`。

---

## 你需要做的修改

### 修改文件：`server_scripts/profession/prof_configs/z_tacz_config_build.js`

在该文件的**末尾**（在所有配置构建完成后），添加以下代码：

```javascript
// ============================================================
// ★ 弹药补给包（siege_tools:ammo_kit）— 弹药配置注册
// ============================================================
// 构建武器ID → 弹药配置 的扁平映射表
// 供给 Java 侧的 SiegeToolsAPI 使用
// ============================================================

var ammoConfigMap = {};

// 1. 遍历所有职业的 TACZ 枪械配置
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

// 2. 添加非 TACZ 武器弹药（从 VANILLA_WEAPON_AMMO 或 nonTaczAmmo 中读取）
//    注意：VANILLA_WEAPON_AMMO 是在本文件中构建的
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

// 3. 注册到 Java 的 SiegeToolsAPI
try {
  var $SiegeToolsAPI = Java.loadClass('com.xkmxz.siege_tools.api.SiegeToolsAPI');
  $SiegeToolsAPI.clearAndRegister(JSON.stringify(ammoConfigMap));
  console.log('[SiegeTools-AmmoKit] 已注册 ' + Object.keys(ammoConfigMap).length + ' 个武器的弹药配置');
} catch (e) {
  console.warn('[SiegeTools-AmmoKit] 注册失败（SiegeTools 模组可能未安装）: ' + e);
}
```

---

### 注意事项

1. **加载时机**：这段代码必须放在 `z_tacz_config_build.js` 的末尾，以确保 `VANILLA_WEAPON_AMMO` 和 `PROF_CONFIGS` 已经构建完成。

2. **文件顺序**：`z_tacz_config_build.js` 本身是最后加载的 prof_configs 文件，所以在这个位置添加是安全的。

3. **依赖检查**：代码中用了 `try/catch` 包裹 `Java.loadClass()`，如果 SiegeTools 模组未安装，不会导致 KubeJS 报错。

4. **reload 支持**：使用 `clearAndRegister()` 而不是 `registerAmmoConfigs()`，这样 `/kubejs reload` 后可以清空旧配置并重新注册。

5. **confirm 已注册**：启动后可在日志中搜索 `[SiegeTools-AmmoKit]` 确认注册成功。

---

## 数据流总结

```
KubeJS 启动
  │
  ├─ PROF_CONFIGS 构建完成
  ├─ VANILLA_WEAPON_AMMO 构建完成
  │
  └─ z_tacz_config_build.js 末尾：
      遍历所有配置 → 构建 ammoConfigMap
      → Java.loadClass('SiegeToolsAPI').clearAndRegister(JSON)
      → Java 侧 Map<String, AmmoConfig> 就绪
                                │
      AmmoKitItem 右键玩家 → 从 player.persistentData 读取武器 ID
      AmmoKitEntity 扫描    → 查 Map 获得弹药配置 → 发放弹药
```

---

## 调试

- 启动后检查日志中的 `[SiegeToolsAPI] 已注册 N 个武器的弹药配置`
- 如果日志显示 0 个，检查 `z_tacz_config_build.js` 中的配置遍历是否正确
- 手持 `siege_tools:ammo_kit` 右键队友测试
- 潜行+右键投掷放置，等待几秒观察弹药是否补充
- 配置相关：`siege-tools-common.toml` 中的 `placed.scanRange`、`placed.scanInterval`、`supply.*` 等
