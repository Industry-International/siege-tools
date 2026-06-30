---
name: LDLib2 开发指引
description: 使用 LDLib2（LowDragLib2）编写 GUI 界面时的开发指引。LDLib2 是当前项目编写自定义 GUI 的首选方案。
---

# LDLib2 开发指引（Java 版）

## 1. 定位：GUI 首选方案

**LDLib2 是本项目编写自定义 GUI 的首选方案**。除非用户明确要求使用其他 UI 框架（如晴雪UI），否则所有 GUI 开发默认使用 LDLib2。

LDLib2 是一个 Minecraft 模组 GUI 库，提供丰富的 UI 组件和数据绑定机制，适用于 NeoForge 1.21.1 环境。

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
| `UIElement` | 容器，用于布局和放置子组件 | `new UIElement().lss("width", 270).lss("padding", 6)` |
| `Label` | 文本标签 | `new Label().setText(Component.literal("§7文本"))` |
| `TextField` | 文本输入框 | `new TextField().setNumbersOnlyInt(0, 999).setText("64").lss("width", 55)` |
| `Button` | 按钮 | `new Button().setText(Component.literal("§a保存")).setOnServerClick(e -> { ... })` |
| `TabView` | 标签页容器 | `new TabView().addTab(tab, page)` |
| `Tab` | 单个标签页 | `new Tab().setText("标签名")` |
| `InventorySlots` | 玩家物品栏 | `new InventorySlots()` |
| `TextField` | 数字输入框 | `.setNumbersOnlyInt(min, max)` 限制整数范围 |

## 4. 样式设置（lss）

所有 UI 组件可通过 `.lss(property, value)` 设置样式：

```java
// 常用样式
element.lss("width", 270);           // 固定宽度
element.lss("width", "100%");        // 百分比宽度
element.lss("padding", 6);           // 内边距（数字）
element.lss("padding", "3 10");      // 内边距（字符串：上下 左右）
element.lss("overflow", "hidden");   // 溢出隐藏

// Label 文字对齐
label.textStyle(style -> style.textAlignHorizontal("center"));
```

## 5. UI 注册与打开

### 方式一：通过 BlockUIMenuType（方块关联 UI，推荐）

Java 端使用 LDLib2 自带的 `BlockUIMenuType` 注册和打开 UI，无需自定义 MenuType：

```java
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

// 方块右键事件中打开 UI（如 NeoForge 的 BlockEvents 或自定义交互）
// 注意：需要在服务器端调用
BlockUIMenuType.openUI((ServerPlayer) player, blockPos);
```

`BlockUIMenuType` 会自动从方块实体的 `IMenuProvider`（或方块本身实现 `MenuProvider`）获取 UI 实例。让方块实体实现 `MenuProvider` 接口：

```java
// 方块实体类中
public class MyBlockEntity extends BlockEntity implements MenuProvider {
    public MyBlockEntity(BlockPos pos, BlockState state) {
        super(MyBlockEntities.MY_BE.get(), pos, state);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        UIElement root = new UIElement();
        root.lss("width", 270).lss("padding", 6);
        root.addChild(new Label().setText(Component.literal("§7我的界面")));
        root.addChild(new InventorySlots());

        UI ui = UI.of(root);
        ModularUI modularUI = ModularUI.of(ui, player);
        return modularUI.getMenu() != null
                ? modularUI.getMenu()
                : new ModularUIContainerMenu(containerType, containerId, playerInv, modularUI);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("我的界面");
    }
}
```

### 方式二：通过 KJSBlockUIMenuType（与 KubeJS 联动）

如果 UI 需要在 KubeJS 侧注册，Java 侧仍可通过 `KJSBlockUIMenuType.openUI()` 打开：

```java
import com.lowdragmc.lowdraglib2.integration.kjs.ui.KJSBlockUIMenuType;

// 打开 KubeJS 注册的 UI
KJSBlockUIMenuType.openUI((ServerPlayer) player, blockPos, "kubejs:my_ui_id");
```

### 方式三：自定义 MenuType（全手动控制）

注册自定义 MenuType，完全控制 UI 构建和网络序列化：

```java
// mod 主类中注册
public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, MODID);

// 使用 LDLib2 的工厂
public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> MY_UI =
        MENUS.register("my_ui", () -> new MenuType<>(
                (id, inv, buf) -> BlockUIMenuType.create(id, inv, buf)));
```

## 6. 数据绑定

LDLib2 提供 `DataBindingBuilder` 实现客户端↔服务端数据同步：

```java
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SimpleBinding;

// 在 UI 构建方法中（如 createMenu / UI 构建代码段）
Map<String, String> fieldVals = new HashMap<>();

private SimpleBinding<String> bindField(TextField field, String name) {
    fieldVals.put(name, field.getText());  // 初始值
    try {
        SimpleBinding<String> binding = DataBindingBuilder.string(
                () -> field.getText(),                   // getter
                val -> fieldVals.put(name, val)          // C2S setter
        ).s2cStrategy(SyncStrategy.NONE)
         .c2sStrategy(SyncStrategy.ALWAYS)
         .name(name)
         .build();
        field.bind(binding);
        return binding;
    } catch (Exception e) {
        System.out.println("[UI] 绑定失败(" + name + "): " + e);
        return null;
    }
}
```

**注意**：C2S DataBinding 在复杂场景下可能不完全可靠。`setOnServerClick` 回调中读取 `fieldVals.get(name)` 时，应备选回退到 `field.getText()`。

## 7. 按钮事件

```java
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;

Button btn = new Button();
btn.setText(Component.literal("§a✔ 保存"));
btn.lss("padding", "3 10");
btn.setOnServerClick(e -> {
    // 此回调在服务端执行
    // 读取 fieldVals，写入 persistentData 等
    if (player instanceof ServerPlayer serverPlayer) {
        serverPlayer.displayClientMessage(Component.literal("§a完成"), false);
    }
});
```

`UIEventListener` 是一个函数式接口，可用 lambda `e -> { ... }` 实现。`e` 是 `UIEvent` 类型，包含事件源和上下文信息。

## 8. 常用布局模式

### 带标签的行

```java
UIElement row = new UIElement();
row.addChild(new Label().setText(Component.literal("§7标签:")));
row.addChild(textField);
row.addChild(new Label().setText(Component.literal(" 单位")));
```

### TabView 分页

```java
TabView tabView = new TabView();

UIElement page1 = new UIElement();
// ... 添加内容 ...
Tab tab1 = new Tab();
tab1.setText("页面1");
tabView.addTab(tab1, page1);

// 更多页面...

root.addChild(tabView);
```

### 分隔线

```java
Label sep = new Label();
sep.setText(Component.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━"));
sep.lss("width", "100%");
sep.lss("overflow", "hidden");
```

## 9. 典型示例

完整示例见：

```
startup_scripts/src/blocks/ammo_crate/gui.js  — 弹药补给站配置 GUI（KubeJS 版参考）
```

对应的 Java 实现参考 `SiegeToolsAPI.java` 中的弹药发放逻辑 + LDLib2 UI 构建方式。

## 10. 注意事项

1. **UI 在服务端构建**：`BlockUIMenuType` 会在双方都调用 UI 构建逻辑，`createMenu` 在服务端调用
2. **右键打开**：通过 `BlockUIMenuType.openUI()` 在方块右键事件中触发
3. **数据持久化**：UI 中编辑的数据通过 `setOnServerClick` 回调写入 `player.persistentData`（通过 `WithPersistentData` 接口的 `kjs$getPersistentData()` 方法）
4. **GUI 缓存**：如需要在事件间传递数据，使用 `Map<BlockPos, Object>` 缓存，注意在服务端和客户端之间区分
5. **安全解析**：从 TextField 或 `fieldVals` 读取数值时，始终检查 null 和空值，使用 `try-catch` 包裹解析逻辑

## 附录：常用导入参考

```java
// LDLib2 GUI 核心
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

// 工厂类
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;

// 数据绑定
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SimpleBinding;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;

// KubeJS 联动（可选）
import com.lowdragmc.lowdraglib2.integration.kjs.ui.KJSBlockUIMenuType;

// Minecraft
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
```
