---
name: 排障指南
description: siege_tools 模组开发与运行时的常见问题、根因分析与解决方案。覆盖编译崩溃、运行时崩溃、物品不显示、KubeJS 桥接失败、实体异常等。
---

# siege_tools 排障指南

## 一、编译阶段问题

### 1.1 BUILD FAILED — compilation errors

**现象**：`./gradlew build` 输出 `BUILD FAILED`，控制台打印 Java 编译错误。

**排查步骤**：
1. 只看 `error:` 行，忽略 `warning:`（警告通常不影响编译）
2. 中文环境下的 Gradle 输出乱码时，用以下命令过滤：
   ```bash
   ./gradlew build 2>&1 | grep -E "error:|找不到符号|无法解析|不是语句"
   ```
3. 常见的编译错误类型：

| 错误信息 | 最常见原因 | 修复方向 |
|---------|-----------|---------|
| `找不到符号` / `cannot find symbol` | 类/方法不存在或拼写错误 | 检查方法名和类路径 |
| `不兼容的类型` / `incompatible types` | 类型参数不匹配 | 检查泛型/返回值/参数类型 |
| `getOrCreateTag()` 不存在 | Minecraft 1.21 移除了此方法 | 改用 `DataComponents.CUSTOM_DATA` |
| `ResourceLocation.of()` 不存在 | 方法签名变更 | 改用 `ResourceLocation.parse()` 或 `ResourceLocation.fromNamespaceAndPath()` |
| `isOnGround()` 不存在 | 方法名变更 | 改用 `onGround()` |

### 1.2 BUILD FAILED — 配置缓存损坏

**现象**：修改代码后 `./gradlew build` 仍然 `UP-TO-DATE` 或 `FROM-CACHE`，改动未生效。

**原因**：Gradle configuration cache 过期。

**修复**：
```bash
./gradlew clean build
```
或删除缓存目录：
```bash
rm -rf build .gradle
./gradlew build
```

---

## 二、运行时崩溃 / 模组加载失败

### 2.1 FATAL — MalformedURLException: no protocol: 无

**现象**：启动时立即崩溃，crash-report 中显示：
```
Caused by: java.net.MalformedURLException: no protocol: 无
```

**原因**：`neoforge.mods.toml` 中包含了非法 URL 值：
```toml
updateJSONURL = "无"     # ← "无" 不是合法 URL
displayURL = "无"        # ← 同上
```

**修复**：注释掉或删除这两行：
```toml
#updateJSONURL = ""
#displayURL = ""
```

### 2.2 FATAL — Detected config file conflict on xxx-common.toml

**现象**：
```
Detected config file conflict on siege_tools-common.toml from siege_tools (already registered by siege_tools)
```

**原因**：一个模组注册了两个 `ModConfig.Type.COMMON` 配置，NeoForge 为两者生成相同的文件名 `siege_tools-common.toml`。

**修复**：合并所有 COMMON 配置到一个 `ModConfigSpec` 中：

```java
// ❌ 错误：两个 COMMON 配置冲突
modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
modContainer.registerConfig(ModConfig.Type.COMMON, AmmoKitConfig.SPEC); // ← 冲突

// ✅ 正确：合并到一个 COMMON 配置中
modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
```

如需分离配置，使用不同的类型（文件名不同）：
```java
modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);   // → siege_tools-common.toml
modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC); // → siege_tools-server.toml
```

### 2.3 FATAL — Cowardly refusing to send event to a broken mod state

**现象**：启动日志中出现大量 `Cowardly refusing to send event ... to a broken mod state`。

**原因**：这是**结果**而非原因——说明某个模组在前置加载阶段已经崩溃（如 2.1 或 2.2），运行阶段被跳过。

**排查**：往前翻日志，找到真正的 FATAL 错误行（通常是第一个错误）。常见的关键词：
- `Error during pre-loading phase`
- `Detected config file conflict`
- `MalformedURLException`

### 2.4 模组加载成功但功能不正常

**现象**：游戏启动无崩溃，但模组功能不生效（如物品不显示、实体不出现）。

**检查清单**：

| 检查项 | 方法 |
|-------|------|
| 日志中模组是否成功加载 | 搜索 `siege_tools` 或 `SiegeTools` |
| 创造模式物品栏是否有物品 | 检查自定义标签页和 `addCreative` 事件 |
| 实体渲染器是否注册 | 搜索 `EntityRenderers.register` |
| KubeJS 桥接是否成功 | 搜索 `[AmmoKitBridge]` 或 `[SiegeToolsAPI]` |
| 配置值是否正确 | 检查 `siege-tools-common.toml` |

---

## 三、物品相关

### 3.1 物品不在创造模式物品栏中

**现象**：`/give` 可以获得物品，但创造模式物品栏中找不到。

**可能原因与排查**：

| 原因 | 特征 | 修复 |
|------|------|------|
| `stacksTo(0)` | 配置值在构造时为 0 | 硬编码默认值，见下方 |
| 创造标签页未注册 | 自定义标签页完全不存在 | 检查 `CREATIVE_MODE_TABS.register` 和 `displayItems` |
| `addCreative` 事件条件不对 | 事件监听器未触发 | 检查 `event.getTabKey()` 的比较逻辑 |

**关键**：`Item.Properties` 在模组构造阶段设置，此时 `ModConfig` 尚未加载。不要使用 `Config.*` 值初始化 `Item.Properties`：

```java
// ❌ 错误：Config.ammoKitMaxStackSize 此时 = 0
public AmmoKitItem() {
    super(new Item.Properties().stacksTo(Config.ammoKitMaxStackSize));
}

// ✅ 正确：使用硬编码值（可参考 MedicalKitItem 的 bipush 16）
public AmmoKitItem() {
    super(new Item.Properties().stacksTo(16));
}
```

`Item.Properties` 中所有值在构造后不可修改，因此不能用运行时配置控制。

### 3.2 物品贴图不显示（紫黑方块）

**现象**：游戏中物品显示为紫黑方块（missing texture）。

**排查**：

1. 确认贴图文件路径正确：
   ```
   src/main/resources/assets/siege_tools/textures/item/ammo_kit.png
   ```

2. 确认物品模型 JSON 路径正确：
   ```
   src/main/resources/assets/siege_tools/models/item/ammo_kit.json
   ```
   ```json
   {
     "parent": "minecraft:item/generated",
     "textures": {
       "layer0": "siege_tools:item/ammo_kit"
     }
   }
   ```

3. 确认贴图文件是有效的 PNG 格式且尺寸合适（通常是 16×16 或 32×32）

### 3.3 物品名称不显示（显示为 `item.siege_tools.ammo_kit`）

**现象**：物品显示原始翻译键而非中文/英文名称。

**原因**：语言文件未加载或键名不匹配。

**修复**：
1. 确认语言文件路径：`assets/siege_tools/lang/en_us.json` 和 `zh_cn.json`
2. 确认键名匹配注册 ID：`item.siege_tools.ammo_kit`
3. 语言文件修改后按 `F3+T` 刷新资源包，或重启客户端

---

## 四、KubeJS 桥接问题

### 4.1 [AmmoKitBridge] PROF_CONFIGS 未定义

**日志输出**：
```
[AmmoKitBridge] PROF_CONFIGS 未定义，跳过弹药注册
```

**原因**：`z_ammo_kit_bridge.js` 加载时 `PROF_CONFIGS` 尚未构建完成。

**排查**：
1. 检查文件名前缀 `z_` — 确保它在 `prof_configs/z_tacz_config_build.js` **之后**加载
2. 检查文件所在目录 — 应直接在 `profession/` 下而非 `profession/prof_configs/` 下
3. 加载顺序规则：同级目录按字母序；上级目录的 `z_` 文件在子目录的 `p_` 文件之后加载

### 4.2 [AmmoKitBridge] 注册失败（siege_tools 模组可能未安装）

**日志输出**：
```
[AmmoKitBridge] 注册失败（siege_tools 模组可能未安装）: ...
```

**原因**：`Java.loadClass()` 找不到 `SiegeToolsAPI` 类。

**排查**：
1. 确认 `siege_tools` 模组已安装且加载成功
2. 检查日志中是否有 2.1/2.2/2.3 类 FATAL 错误
3. 检查完整的类名：`com.xkmxz.siege_tools.api.SiegeToolsAPI`

### 4.3 [SiegeToolsAPI] 已注册 0 个武器的弹药配置

**日志输出**：
```
[SiegeToolsAPI] 已注册 0 个武器的弹药配置
```

**原因**：`z_ammo_kit_bridge.js` 遍历完成但未找到任何武器配置。

**排查**：
1. 检查 `PROF_CONFIGS` 是否为空对象
2. 检查职业配置中是否确实有 `guns.{类别}.{武器ID}.ammo` 数据
3. 检查 `VANILLA_WEAPON_AMMO` 是否存在

### 4.4 `/kubejs reload` 后数据未更新

**原因**：Java 侧的静态 Map 未清空。

**修复**：确保 KubeJS 调用的是 `clearAndRegister()` 而非 `registerAmmoConfigs()`：
```javascript
// ✅ 正确：先清空再注册，支持 reload
$SiegeToolsAPI.clearAndRegister(JSON.stringify(ammoConfigMap));

// ❌ 错误：仅在 Map 中追加，reload 后残留旧数据
$SiegeToolsAPI.registerAmmoConfigs(JSON.stringify(ammoConfigMap));
```

---

## 五、实体（Entity）问题

### 5.1 实体完全不可见

**现象**：投掷弹药箱后，看不见实体模型。

**原因**：实体渲染器未注册。

**修复**：在客户端初始化中注册渲染器：
```java
// 在 ClientModEvents.onClientSetup 中：
EntityRenderers.register(siege_tools.AMMO_KIT_ENTITY.get(), AmmoKitRenderer::new);
```

### 5.2 实体投掷后立即消失

**现象**：投掷动画正常，但实体在空中就消失了。

**原因**：`tick()` 中的生命周期条件导致提前消失。

**排查**：
```java
// 检查是否有 maxLifetime 设得太短
if (Config.ammoKitPlacedMaxLifetime > 0 && aliveTicks > Config.ammoKitPlacedMaxLifetime) {
    this.discard();  // ← 确认此值是否合理
}
```

### 5.3 实体落地后不扫描玩家

**现象**：实体落地，粒子效果正常，但在范围内的玩家未被补给。

**排查顺序**：

1. **落地判定** — 确认 `onGround()` 返回 true
   ```java
   if (this.onGround()) { ... }
   ```

2. **队伍过滤** — 确认投放者的 `ownerTeam` 已被设置且与玩家匹配
   ```java
   String playerTeam = targetPlayer.getPersistentData().getString("team");
   if (ownerTeam.isEmpty() || !ownerTeam.equals(playerTeam)) continue;
   ```

3. **API 初始化** — 确认 `SiegeToolsAPI.isInitialized()` 返回 true
   ```java
   if (!SiegeToolsAPI.isInitialized()) return;  // 弹药映射表未注册
   ```

4. **弹药判定** — 确认 `isPlayerFullySupplied` 返回 false（即确实需要弹药）
   ```java
   if (SiegeToolsAPI.isPlayerFullySupplied(targetPlayer)) continue;
   ```

### 5.4 拾取弹药箱返回空物品

**现象**：潜行右键点击已放置的弹药箱，只听到声音但没捡到物品。

**原因**：`ItemHandlerHelper.giveItemToPlayer` 在物品注册未完成时可能获取空物品。

**排查**：确认 `AmmoKitItem` 的注册引用正确：
```java
ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(
    com.xkmxz.siege_tools.siege_tools.AMMO_KIT_ITEM.get()));  // ← 确认此引用
```

---

## 六、SiegeToolsAPI 内部问题

### 6.1 右键队友无反应

**现象**：手持 `siege_tools:ammo_kit` 右键队友，没有提示音也没有消息。

**排查顺序**：

1. 确认 `interactLivingEntity` 被调用（日志打断点）
2. 确认目标实体是 `ServerPlayer` 类型
3. 确认队伍信息存在且匹配
4. 确认 `SiegeToolsAPI.isInitialized() == true`
5. 确认弹药映射表中有目标玩家的武器
6. 确认 `giveAmmoToPlayer` 返回了 true

**常见遗漏**：`SiegeToolsAPI` 的 `isInitialized()` 返回 false，表示 KubeJS 未注册弹药配置。

### 6.2 弹药发放了但背包里没有

**原因**：`ItemHandlerHelper.giveItemToPlayer` 在背包满时会将物品扔到玩家脚下。

**排查**：检查玩家周围地面是否有掉落的弹药物品。

### 6.3 弹药已满的判断不正确

**现象**：玩家已经有一盒弹药，但系统仍判定"弹药不足"并重复发放。

**原因**：`hasEnoughTaczAmmo` 或 `hasEnoughVanillaAmmo` 逻辑不完善。

**排查**：检查背包中弹药盒的 `CustomData` 是否正确读取：
```java
CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
if (customData != null) {
    CompoundTag tag = customData.copyTag();
    int ammoCount = tag.getInt("AmmoCount");
    String gunId = tag.getString("GunId");
}
```

---

## 七、配置问题

### 7.1 配置文件未生成

**现象**：启动后 `defaultconfigs/` 或 `run/` 下没有 `siege_tools-common.toml`。

**原因**：`ModConfig` 未正确注册。

**修复**：在模组构造器中注册：
```java
modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
```

### 7.2 修改配置后不生效

**现象**：修改 `siege_tools-common.toml` 中的值并重启游戏，但行为未改变。

**排查**：
1. 确认修改的是正确的配置文件（服务端/客户端分离）
2. 确认配置值在 `ModConfigEvent` 中被重新读取
3. 确认运行时使用的是 `Config.ammoKit*` 值而非硬编码

配置文件路径：
- 开发环境：`run/defaultconfigs/siege_tools-common.toml`
- 生产环境：`<游戏目录>/defaultconfigs/siege_tools-common.toml`

### 7.3 配置键名冲突

**现象**：两个配置项共用一个键名。

**修复**：使用带命名空间前缀的键名：
```java
// 使用 ammo_kit. 前缀隔离弹药补给包配置
.defineInRange("ammo_kit.placed.scanRange", 6, 1, 32);
.defineInRange("ammo_kit.placed.scanInterval", 40, 10, 200);
.define("ammo_kit.supply.primary", true);
```

---

## 八、调试工具与技巧

### 8.1 快速验证物品注册

```mcfunction
# 检查物品是否存在
/give @p siege_tools:ammo_kit

# 查看创造物品栏标签页
# 在 Siege Tools 标签页或 Combat 标签页中查找
```

### 8.2 快速验证 KubeJS 桥接

```mcfunction
# 重新加载 KubeJS 脚本（无需重启）
/kubejs reload

# 检查日志
# 搜索 [AmmoKitBridge] 和 [SiegeToolsAPI]
```

### 8.3 日志快速定位

```bash
# 开发环境日志位置
run/logs/latest.log          # 最新运行日志
run/logs/debug.log           # 详细调试日志
run/crash-reports/           # 崩溃报告

# 查看 siegetools 相关日志
grep -i "siege_tools\|ammo_kit\|SiegeToolsAPI\|AmmoKitBridge" run/logs/latest.log

# 查看致命错误
grep -i "FATAL\|Error during pre-loading\|Detected config" run/logs/latest.log
```

### 8.4 实体调试

```mcfunction
# 在实体附近显示调试信息
# 使用 Minecraft 的 F3+B 显示实体碰撞箱

# 手动 summon 实体
/summon siege_tools:ammo_kit ~ ~ ~
```

### 8.5 完整自检清单

在排查一个功能性问题时，按顺序检查：

```
[ ] 1. 编译通过（./gradlew build）
[ ] 2. 模组启动无 FATAL 错误
[ ] 3. /give 获取物品正常
[ ] 4. 创造模式物品栏显示正常
[ ] 5. 物品贴图显示正常
[ ] 6. 语言文件读取正常
[ ] 7. KubeJS 桥接日志显示注册成功（[AmmoKitBridge] 已注册 N 个武器）
[ ] 8. 右键队友响应正常
[ ] 9. 投掷放置实体正常
[ ] 10. 实体落地后扫描范围正常
[ ] 11. 弹药发放正确
[ ] 12. 补满后自动消失正常
```
