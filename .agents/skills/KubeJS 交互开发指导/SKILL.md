---
name: KubeJS 交互开发指导
description: Java NeoForge 模组与 KubeJS 7 脚本系统之间的交互机制、API 调用方式、数据共享方案和开发约定。适用于所有需要让 Java 模组代码与 KubeJS 脚本联动的开发任务。
---

# KubeJS 交互开发指导

## 1. 交互概述

本项目同时运行两个层：

```
┌─────────────────────────────────────┐
│  Java NeoForge Mod (siege_tools)    │  ← src/main/java/
│  • 原生模组代码                     │
│  • 高性能、可直接调用 Minecraft API │
├─────────────────────────────────────┤
│  KubeJS 7 Scripts                   │  ← server_scripts/ + startup_scripts/
│  • 快速迭代的业务逻辑               │
│  • 职业系统、队伍、复活券、载具等   │
└─────────────────────────────────────┘
```

**KubeJS 模块完整文档见：** `./kubejs使用指南/README.md`

---

## 2. Java → KubeJS 调用方式

### 2.1 通过 KubeJS 事件总线通信

Java 模组可以触发 KubeJS 事件，让 KubeJS 脚本响应：

```java
// Java 端触发 KubeJS 事件
import dev.latvian.mods.kubejs.event.EventJS;
import dev.latvian.mods.kubejs.event.KubeEvent;

// 方式1：触发已有 KubeJS 事件（如 ServerEvents）
// 这可以直接通过 NeoForge 事件系统，
// KubeJS 在内部监听了大多数 NeoForge 事件

// 方式2：通过 KubeJS 的 KubeEvent 发送自定义通知
// KubeJS 7 提供了 KubeJSPlugin 机制，Java 模组可以
// 注册自定义事件让 KubeJS 脚本监听
```

### 2.2 通过 persistentData 共享数据

Java 模组写入 `server.persistentData`，KubeJS 脚本读取（反之亦然）。

**Java 端写入：**
```java
// 获取 KubeJS 的 PersistentData
// 通过 KubeJS 的 API 或直接操作 NBT 文件

// 推荐方式：通过 KubeJS 的 WorldPersistentData
// 在 NeoForge 事件中获取 ServerLevel
ServerLevel level = event.getLevel();
// 然后通过 KubeJS 桥接访问
```

**KubeJS 端读写（参见 `kubejs使用指南/数据存储.md`）：**
```javascript
// 读取
server.persistentData.getString('my_key')

// 写入
server.persistentData.putString('my_key', JSON.stringify(data))
```

### 2.3 通过 KubeJS 公开的 Java 类引用

KubeJS 7 在 Rhino 环境中可以通过 `Java.loadClass()` 加载任何 Java 类。Java 模组可以在代码中暴露静态方法，供 KubeJS 直接调用。

**Java 模组暴露 API：**
```java
package com.xkmxz.siege_tools.api;

// 定义一个 API 类，供 KubeJS 调用
public class SiegeToolsAPI {
    public static String doSomething(String input) {
        return "processed: " + input;
    }
    
    public static int calculateScore(int kills, int deaths) {
        return kills * 10 - deaths * 5;
    }
}
```

**KubeJS 调用 Java API：**
```javascript
// 在 KubeJS 脚本中加载 Java 类并调用
var $SiegeToolsAPI = Java.loadClass('com.xkmxz.siege_tools.api.SiegeToolsAPI')
var result = $SiegeToolsAPI.doSomething('test')
var score = $SiegeToolsAPI.calculateScore(10, 2)
```

### 2.4 通过自定义网络包 / 指令桥接

Java 模组注册自定义指令，KubeJS 通过 `/` 命令调用：

**Java 端注册指令**（通过 NeoForge 的 `RegisterCommandsEvent`）：
```java
@SubscribeEvent
public static void onRegisterCommands(RegisterCommandsEvent event) {
    var dispatcher = event.getDispatcher();
    // 注册指令...
}
```

**KubeJS 端调用指令**：
```javascript
// 在 KubeJS 中执行 Java 注册的指令
server.runCommandSilent('siege_tools:my_command arg1 arg2')
```

---

## 3. KubeJS → Java 调用方式

### 3.1 Java.loadClass()（最常用）

KubeJS 脚本通过 `Java.loadClass()` 加载任意 Java 类并调用其静态方法：

```javascript
// 加载 Java 工具类
var $UUID = Java.loadClass('java.util.UUID')
var $Component = Java.loadClass('net.minecraft.network.chat.Component')
var $ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')

// 调用静态方法
var uuid = $UUID.randomUUID().toString()
var component = $Component.literal('§a文本')

// 调用构造器
var $ItemStack = Java.loadClass('net.minecraft.world.item.ItemStack')
var $Items = Java.loadClass('net.minecraft.world.item.Items')
var stack = new $ItemStack($Items.DIAMOND, 1)
```

**注意事项：**
- `Java.loadClass()` 返回的是 Java 类的包装，不能直接作为泛型 `Class<T>` 参数传递
- 对于 `level.getEntitiesOfClass(Class, AABB)` 等需要泛型的方法，建议在 KubeJS 中遍历 + 手动过滤
- 已 `const` 声明的类不可用 `const`/`var`/`let` 重复声明（参见 `kubejs使用指南/可调用接口参考.md` 中的 Java 类引用表）

### 3.2 监听 Java 模组触发的事件

如果 Java 模组注册了自定义 KubeJS 事件，KubeJS 脚本可以：

```javascript
// 监听 Java 模组发出的事件（如果有注册）
// 格式：onEvent('siege_tools:event_name', handler)
```

### 3.3 通过 persistentData 共享数据（双向）

KubeJS 写入的数据，Java 端可以通过读取 `saves/<世界>/kubejs/persistent_data.nbt` 来获取，或者在 NeoForge 事件中通过 KubeJS API 桥接。

---

## 4. 数据共享与同步

### 4.1 共享数据路径对比

| 存储位置 | Java 可读写 | KubeJS 可读写 | 持久化 | 适用场景 |
|---------|:----------:|:------------:|:------:|---------|
| `server.persistentData` | ⚠ 间接 | ✅ | ✅ | 配置、运行时状态 |
| `player.persistentData` | ✅ | ✅ | ✅ | 玩家个人数据 |
| `block.entity.persistentData` | ✅ | ✅ | ✅ | 方块实体数据 |
| `level.dat` (原版) | ✅ | ⚠ 间接 | ✅ | 数据包协作 |
| 内存变量/全局对象 | ⚠ | ✅ | ❌ | 运行时缓存 |

### 4.2 Java 端读写 player.persistentData

```java
// 读
CompoundTag playerData = player.getPersistentData();
String value = playerData.getString("key");

// 写
CompoundTag playerData = player.getPersistentData();
playerData.putString("key", "value");
```

### 4.3 KubeJS 端读写 player.persistentData

```javascript
// 读
var value = player.persistentData.getString('key')

// 写
player.persistentData.putString('key', 'value')
```

> ⚠ 持久化数据键名冲突表参见 `kubejs使用指南/可调用接口参考.md` 中的「持久化数据键总表」

---

## 5. 开发约定

### 5.1 API 设计原则

1. **Java 负责底层能力** — 高性能计算、Minecraft 原生 API 调用、网络包、复杂逻辑
2. **KubeJS 负责业务编排** — 事件响应、条件判断、配置驱动、快速迭代
3. **接口清晰** — Java 暴露的 API 尽量为静态方法，输入输出明确
4. **版本兼容** — Java API 避免频繁变动，KubeJS 侧适配

### 5.2 数据共享约定

1. **明确所有权** — 每个持久化数据键名归一个模块所有，避免冲突
2. **JSON 序列化** — 复杂数据以 JSON 字符串形式存储于 `persistentData`
3. **容错处理** — 读取时处理空值和解析异常

### 5.3 模块加载顺序

```
Java Mod (NeoForge)        ← 游戏启动时加载
    ↓
KubeJS startup_scripts/    ← 游戏启动时加载（物品注册）
    ↓
KubeJS server_scripts/     ← 服务端加载时运行
```

Java 模组必须在 KubeJS 脚本之前加载完成，因此 Java 提供的 API 在 `startup_scripts` 和 `server_scripts` 中都可用。

### 5.4 调试

- **KubeJS 日志**: `logs/kubejs/`，含 JS 运行时错误
- **Java 模组日志**: 控制台 / `logs/latest.log`，含 Java 端日志
- **测试指令**: `/kubejs reload` 重载 KubeJS 脚本（无需重启服务端）

---

## 6. 典型交互场景

### 场景1：Java 模组提供工具函数给 KubeJS

```java
// Java 端
public class SiegeToolsAPI {
    public static boolean isInRegion(double x, double z, double[] region) {
        return x >= region[0] && x <= region[2] && z >= region[1] && z <= region[3];
    }
}
```

```javascript
// KubeJS 端
var $SiegeToolsAPI = Java.loadClass('com.xkmxz.siege_tools.api.SiegeToolsAPI')
var inRegion = $SiegeToolsAPI.isInRegion(player.x, player.z, [100, 200, 300, 400])
```

### 场景2：KubeJS 配置驱动 Java 逻辑

KubeJS 脚本在 `persistentData` 中写入配置，Java 模组在运行事件中读取并响应。

### 场景3：Java 注册物品，KubeJS 处理交互

Java 模组通过 `DeferredRegister` 注册方块和物品，KubeJS 通过 `BlockEvents.rightClicked` 或 `ItemEvents` 处理玩家的交互逻辑。

---

## 7. 参考文档

| 文档 | 说明 |
|------|------|
| `./kubejs使用指南/README.md` | KubeJS 模块总览 |
| `./kubejs使用指南/可调用接口参考.md` | KubeJS 全局 API/变量/函数参考 |
| `./kubejs使用指南/数据存储.md` | persistentData 存储机制 |
| `./kubejs使用指南/指令使用.md` | 管理指令参考 |
| `.agents/skills/基础工程规范/SKILL.md` | Java NeoForge 开发规范 |
