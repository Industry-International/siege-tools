---
name: 基础工程规范
description: siege_tools NeoForge 模组项目的开发环境、项目结构、构建方式、代码规范与工程约定。适用于所有涉及该项目的 Java 模组开发任务。
---

# siege_tools — Java NeoForge 模组开发规范

## 1. 项目概览

- **模组 ID**: `siege_tools`
- **模组名称**: Siege Tools（攻防战地图工具）
- **运行环境**: Minecraft 1.21.1 — NeoForge 21.1.233
- **开发语言**: Java 21
- **构建系统**: Gradle (ModDevPlugin 2.0.141)
- **项目类型**: 纯 Java NeoForge 模组（非 KubeJS 脚本模组）

### 1.1 项目定位

本项目是为 Minecraft 攻防战地图（Siege/CTF 类型 PvP 地图）开发的辅助模组，提供自定义物品、方块、机制等，增强地图的可玩性。

---

## 2. 开发环境

- **JDK 版本**: Java 21（必须，`java.toolchain.languageVersion = JavaLanguageVersion.of(21)`）
- **IDE 推荐**: IntelliJ IDEA（已配置 `.idea/`）
- **NeoForge 版本**: `neo_version` 定义在 `gradle.properties` 中
- **Gradle Wrapper**: 使用项目自带的 `gradlew` / `gradlew.bat`

### 2.1 首次运行

```bash
# 生成 IDE 运行配置
./gradlew idea

# 运行客户端
./gradlew runClient

# 运行服务端
./gradlew runServer
```

### 2.2 构建

```bash
./gradlew build
# 构建产物在 build/libs/ 目录
```

---

## 3. 项目结构

```
siege_tools/
├── src/main/
│   ├── java/com/xkmxz/siege_tools/     # 主源码
│   │   ├── siege_tools.java            # 主模组类 (@Mod)
│   │   └── Config.java                 # ModConfigSpec 配置
│   ├── resources/                       # 资源文件
│   │   ├── assets/siege_tools/lang/     # 语言文件 (en_us.json 等)
│   │   └── siege_tools.mixins.json      # Mixin 配置（如需）
│   └── templates/                       # 模板文件
│       └── META-INF/neoforge.mods.toml  # 模元数据模板
├── build.gradle                         # Gradle 构建脚本
├── gradle.properties                    # 版本属性
└── settings.gradle                      # Gradle 设置
```

### 3.1 包命名规范

- 根包：`com.xkmxz.siege_tools`
- 子包按功能划分，如：
  - `item` — 自定义物品
  - `block` — 自定义方块
  - `entity` — 实体
  - `network` — 网络包
  - `client` — 客户端代码

---

## 4. NeoForge 开发要点

### 4.1 DeferredRegister 注册

所有方块、物品、实体等均通过 `DeferredRegister` 注册：

```java
// 主模组类中定义
public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

// 注册方块
public static final DeferredBlock<Block> MY_BLOCK = BLOCKS.registerSimpleBlock("my_block",
    BlockBehaviour.Properties.of().mapColor(MapColor.STONE));

// 注册方块对应的物品
public static final DeferredItem<BlockItem> MY_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("my_block", MY_BLOCK);

// 注册普通物品
public static final DeferredItem<Item> MY_ITEM = ITEMS.registerSimpleItem("my_item",
    new Item.Properties());

// 构造函数中注册 DeferredRegister
BLOCKS.register(modEventBus);
ITEMS.register(modEventBus);
CREATIVE_MODE_TABS.register(modEventBus);
```

### 4.2 事件系统

```java
// 在模组类中直接注册监听
NeoForge.EVENT_BUS.register(this);

// 使用 @SubscribeEvent
@SubscribeEvent
public void onServerStarting(ServerStartingEvent event) {
    // 服务端启动逻辑
}

// 使用 @EventBusSubscriber 自动注册静态方法
@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD)
public static class MyEvents {
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // 通用初始化逻辑
    }
}
```

**事件总线区分**：
- `EventBusSubscriber.Bus.MOD` — 模组生命周期事件（注册、设置）
- `NeoForge.EVENT_BUS` — 游戏运行时事件（右键、生成、死亡等）

### 4.3 ModConfig

使用 `ModConfigSpec` 定义配置，支持服务端/客户端分离：

```java
// 定义
private static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
    .comment("A magic number")
    .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

// 注册
modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

// 读取（在 ModConfigEvent 中加载）
public static int magicNumber;
@SubscribeEvent
static void onLoad(final ModConfigEvent event) {
    magicNumber = MAGIC_NUMBER.get();
}
```

### 4.4 语言文件 (i18n)

在 `src/main/resources/assets/siege_tools/lang/` 下维护多语言文件：

- `en_us.json` — 英文（必须）
- `zh_cn.json` — 简体中文

格式：
```json
{
  "item.siege_tools.my_item": "My Item",
  "block.siege_tools.my_block": "My Block",
  "itemGroup.siege_tools": "Siege Tools"
}
```

### 4.5 Mixin

如需修改原版或模组类行为，使用 Mixin。配置文件在 `src/main/resources/siege_tools.mixins.json`。

---

## 5. 代码规范

### 5.1 Java 编码规范

- **缩进**: 4 空格（IDEA 默认）
- **命名**:
  - 类名: `PascalCase`（如 `SiegeTools`）
  - 方法/字段: `camelCase`（如 `onServerStarting`）
  - 常量: `UPPER_SNAKE_CASE`（如 `MODID`）
  - 包名: 全小写（如 `com.xkmxz.siege_tools`）
- **文件编码**: UTF-8
- **Line endings**: LF（Unix 风格）

### 5.2 日志

```java
private static final Logger LOGGER = LogUtils.getLogger();
LOGGER.info("消息");
LOGGER.warn("警告");
LOGGER.error("错误", exception);
```

### 5.3 兼容性

- 所有代码确保在 Minecraft 1.21.1 + NeoForge 环境下兼容
- 避免使用已标记 `@Deprecated` 的 API
- 服务端/客户端代码通过 `@OnlyIn(Dist.CLIENT)` 或 `DistExecutor` 区隔

---

## 6. 调试指南

### 6.1 运行配置

- `runClient` — 启动 Minecraft 客户端
- `runServer` — 启动专用服务器
- `runGameTestServer` — 运行 GameTest

### 6.2 日志

- 控制台直接输出日志，级别在 `build.gradle` 中配置为 `DEBUG`
- 完整日志文件在 `run/logs/` 目录下

### 6.3 热调试

- IDE Debug 模式启动 `runClient` 可断点调试
- 修改代码后使用 IDE 的"重新编译"或 Gradle 的 `classes` 任务，部分改动可通过 `/reload` 或重启生效

---

## 7. 开发约定

1. **注册优先**: 所有游戏内容（物品、方块、实体等）必须通过 NeoForge 的注册系统注册，不要直接 `new` 后使用
2. **语言文件同步**: 新增物品/方块时必须同步更新 `en_us.json` 和 `zh_cn.json`
3. **配置分离**: 游戏内可调节的参数应使用 `ModConfigSpec`，而非硬编码
4. **网络安全**: C2S/S2C 网络包必须校验合法性，防止恶意客户端篡改
5. **NBT 持久化**: 方块实体/物品的额外数据存储在 `CompoundTag` 中，读写后调用 `setChanged()`
