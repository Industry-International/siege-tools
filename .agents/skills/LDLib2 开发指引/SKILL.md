---
name: LDLib2 开发指引
description: 使用 LDLib2（LowDragLib2）编写 GUI 界面时的开发指引。LDLib2 是当前项目编写自定义 GUI 的首选方案。
---

# LDLib2 开发指引

## 1. 定位：GUI 首选方案

**LDLib2 是本项目编写自定义 GUI 的首选方案**。除非用户明确要求使用其他 UI 框架（如晴雪UI），否则所有 GUI 开发默认使用 LDLib2。

LDLib2 是一个 Minecraft 模组 GUI 库，提供丰富的 UI 组件和数据绑定机制，适用于 KubeJS 7 + NeoForge 1.21.1 环境。

## 2. 文档位置

已有离线文档（HTML），编写 GUI 前应优先阅读：

```
给AI阅读的文档/ldlib/
├── agent_guide_2.html    — Agent 使用指南（组件用法、事件绑定）
└── ldlib2.html            — LDLib2 完整文档
```

官方首页：<https://low-drag-mc.github.io/LowDragMC-Doc/ldlib2/>

## 3. 核心组件速查

| 组件 | 说明 | 使用示例 |
|------|------|---------|
| `UIElement` | 容器，用于布局和放置子组件 | `new UIElement().lss('width', 270).lss('padding', 6)` |
| `Label` | 文本标签 | `new Label().setText(Component.literal('§7文本'))` |
| `TextField` | 文本输入框 | `new TextField().setNumbersOnlyInt(0, 999).setText('64').lss('width', 55)` |
| `Button` | 按钮 | `new Button().setText(Component.literal('§a保存')).setOnServerClick(fn)` |
| `TabView` | 标签页容器 | `new TabView().addTab(tab, page)` |
| `Tab` | 单个标签页 | `new Tab().setText('标签名')` |
| `InventorySlots` | 玩家物品栏 | `new InventorySlots()` |
| `TextField` | 数字输入框 | `.setNumbersOnlyInt(min, max)` 限制整数范围 |

## 4. 样式设置（lss）

所有 UI 组件可通过 `.lss(property, value)` 设置样式：

```javascript
// 常用样式
element.lss('width', 270)           // 固定宽度
element.lss('width', '100%')        // 百分比宽度
element.lss('padding', 6)           // 内边距（数字）
element.lss('padding', '3 10')      // 内边距（字符串：上下 左右）
element.lss('overflow', 'hidden')   // 溢出隐藏

// Label 文字对齐
label.textStyle(function(style) { style.textAlignHorizontal('center') })
```

## 5. UI 注册与打开

```javascript
// startup_scripts 中注册 UI
LDLib2UI.block('kubejs:my_ui_id', event => {
  var player = event.player
  var root = new UIElement()
  // ... 构建 UI ...
  event.modularUI = ModularUI.of(UI.of(root), player)
})

// server_scripts 中打开 UI（右键方块等事件中）
LDLib2UIFactory.openBlockUI(player, blockPos, 'kubejs:my_ui_id')
```

## 6. 数据绑定

LDLib2 提供 `DataBindingBuilder` 实现客户端→服务端（C2S）数据同步：

```javascript
var $DataBindingBuilder = Java.loadClass('com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder')
var $SyncStrategy = Java.loadClass('com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy')

var fieldVals = {}
function bindField(field, name) {
  fieldVals[name] = field.getText()  // 初始值
  try {
    var binding = $DataBindingBuilder.string(
      function() { return field.getText() },
      function(val) { fieldVals[name] = val }  // C2S 更新
    ).s2cStrategy($SyncStrategy.NONE)
     .c2sStrategy($SyncStrategy.ALWAYS)
     .name(name).build()
    field.bind(binding)
  } catch (e) {
    console.log('[UI] 绑定失败(' + name + '): ' + e)
  }
}
```

**注意**：C2S DataBinding 在 KubeJS 7 Rhino 下可能不完全可靠。`setOnServerClick` 回调中读取 `fieldVals[name]` 时，应备选回退到 `field.getText()`。

## 7. 按钮事件

```javascript
var btn = new Button()
btn.setText(Component.literal('§a✔ 保存'))
btn.lss('padding', '3 10')
btn.setOnServerClick(function(clickEvent) {
  // 此回调在服务端执行
  var server = player.getServer()
  // ... 读取 fieldVals，写入 persistentData 等 ...
  player.displayClientMessage(Component.literal('§a完成'), false)
})
```

## 8. 常用布局模式

### 带标签的行
```javascript
var row = new UIElement()
row.addChild(new Label().setText(Component.literal('§7标签:')))
row.addChild(textField)
row.addChild(new Label().setText(Component.literal(' 单位')))
```

### TabView 分页
```javascript
var tabView = new TabView()

var page1 = new UIElement()
// ... 添加内容 ...
var tab1 = new Tab(); tab1.setText('页面1')
tabView.addTab(tab1, page1)

// 更多页面...

root.addChild(tabView)
```

### 分隔线
```javascript
var sep = new Label().setText(Component.literal('§8━━━━━━━━━━━━━━━━━━━━━━━━'))
sep.lss('width', '100%')
sep.lss('overflow', 'hidden')
```

## 9. 典型示例

完整示例见：
```
startup_scripts/src/blocks/ammo_crate/gui.js  — 弹药补给站配置 GUI
```

该示例展示了：TabView 分页、TextField 绑定、Button 保存/重置、玩家物品栏等完整实现。

## 10. 注意事项

1. **UI 在 startup_scripts 中注册**，`LDLib2UI.block()` 必须在 startup 阶段调用
2. **右键打开在 server_scripts 中处理**，通过 `BlockEvents.rightClicked` 调用 `LDLib2UIFactory.openBlockUI()`
3. **跨作用域通信**：KubeJS 7 中 startup 和 server 是独立作用域。复杂操作（如执行补给）通过写入 NBT 标记（如 `PendingReplenish`），由 server 侧的 `BlockEvents.blockEntityTick` 检测执行
4. **GUI 缓存**：使用 `global.ammoStationGuiCache`（`HashMap`）在右键事件和 UI 构建事件之间传递数据（方块位置、当前配置等）
5. **安全解析**：`safeParseField(customVal, field)` 函数用于从 `fieldVals` 或 `field.getText()` 安全解析整数值
