---
name: TaCZ 配件与调试
description: TaCZ 枪械配件的 NBT 结构、配件逻辑、GUN_TACZ_CONFIG 读取方式，以及调试输出技巧。适用于处理枪械配件相关的代码编写和问题排查。
---

# TaCZ 配件与调试

## 1. 配件 NBT 结构

### 1.1 配件注册方式
- 所有配件使用同一个注册名：`tacz:attachment`
- 通过 NBT 中的 `AttachmentId` 区分不同配件

**示例**（扩容弹夹）：
```
tacz:attachment
AttachmentId: "tacz:extended_mag_3"
custom_data={AttachmentId: "tacz:extended_mag_3"}
```

### 1.2 完整 NBT 示例
加装了扩容弹夹后的 AK47 NBT 结构：
```
GunId: "tacz:ak47"
custom_data={
  AttachmentEXTENDED_MAG: {
    components: {
      "minecraft:custom_data":{
        AttachmentId: "tacz:extended_mag_3"
      }
    },
    count: 1,
    id: "tacz:attachment"
  },
  HasBulletInBarrel: 1b,
  GunId: "tacz:ak47",
  GunFireMode: "AUTO",
  GunCurrentAmmoCount: 40
}
```

## 2. 配件逻辑

### 2.1 枪械配置中的配件
- TaCZ 枪械配置中包含配件配置
- 以表的形式存储
- 每把枪都单独配置

### 2.2 访问配置
```javascript
GUN_TACZ_CONFIG[cleanId(weaponId)]
```
- `cleanId` 作用：消去 `""` 等特殊字符，得到纯净的 id 字符串

## 3. 调试输出

### 3.1 客户端调试
```javascript
player.tell(Component.string('§e[DEBUG] ' + variable));
```

### 3.2 调试建议
- 阅读日志并尝试分析问题
- 先向用户提问，得到允许后再尝试修复问题
- 日志路径：`../logs/kubejs/`

## 4. 特别说明

- TaCZ 的相关物品**除非特殊要求，否则一律不需要额外的 i18n**

## 5. 文档维护

- 根据代码变动以及做出的修改，更新 `使用指南` 中每个模块的使用指南
- 每个模块的使用指南必须是独立的 `.md` 文件
