---
name: TaCZ JS 枪械开发
description: 使用 KubeJS 的 TaCZ JS 模组来添加、修改、移除枪械配方、枪械/配件数据，以及修改客户端行为。适用于所有涉及 TaCZ 枪械修改的开发任务。
---

# TaCZ JS 枪械开发

TaCZ 的大部分枪包使用 CC BY-NC-ND 4.0 授权，不允许修改后再分发。因此本项目使用 **TaCZ JS** 模组，通过 KubeJS 在不修改枪包的前提下修改枪械数据。

## 1. TaCZ JS 能做什么

- **添加、修改、移除配方**
- **移除枪械、配件、弹药**
- **修改客户端瞄准、射击、换弹、近战的行为**
- **修改枪械、配件的数据和配件的 TAG**

所有操作均在不修改原始枪包的前提下完成。

## 2. 关键路径与资源

- **TaCZ 官方文档**: <https://docs.aika.dev/taczjs/examples/recipes.html>
- **使用教程参考**: <https://www.mcmod.cn/post/5969.html>
- **TaCZ 枪包数据目录**: `./tacz所有的枪械配件数据/`
- **数据类型定义**: `./tacz所有的枪械配件数据/types/`
- **TaCZ 枪包路径（原始）**: `../tacz/`
- **代码使用示范**: `示范代码/TaCZ JS/example/`

## 3. 枪械与配件数据模型

### 3.1 枪械物品结构
所有枪械本质上是同一个物品 `tacz:modern_kinetic_gun`，通过 NBT 的 `GunId` 区分：

```
GunId: "tacz:ak47", GunFireMode: "AUTO", GunCurrentAmmoCount: 30, HasBulletInBarrel: 1b
```

### 3.2 配件物品结构
配件使用同一个注册名 `tacz:attachment`，通过 NBT 的 `AttachmentId` 区分：

```
AttachmentId: "tacz:extended_mag_3"
custom_data={AttachmentId: "tacz:extended_mag_3"}
```

配件 NBT 示例（加扩容弹夹后的 ak47）：
```
GunId: "tacz:ak47"
custom_data={AttachmentEXTENDED_MAG: {components: {"minecraft:custom_data":{
  AttachmentId: "tacz:extended_mag_3"}}, count: 1, id: "tacz:attachment"}, HasBulletInBarrel: 1b
, GunId: "tacz:ak47", GunFireMode: "AUTO", GunCurrentAmmoCount: 40 }
```

### 3.3 枪械配置中的配件
- 每把枪的配件配置以表的形式独立存储
- 通过 `GUN_TACZ_CONFIG[cleanId(weaponId)]` 访问
- `cleanId` 的作用是消去 `""` 等特殊字符，得到纯净的 id 字符串

## 4. 特别说明

- TaCZ 相关物品**除非特殊要求，否则不需要额外 i18n**
- TaCZ 枪包格式文档（深入阅读前需先理解）：<https://tacwiki.mcma.club/zh/gunpack/60_recipe.html>
