package com.xkmxz.siege_tools.vehicle.registry;

import com.xkmxz.siege_tools.siege_tools;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * MenuType 注册（用于 GUI）。
 * 将在 Phase 4 实现具体 Menu 类后完善。
 */
public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, siege_tools.MODID);

    // Phase 4 实现具体 Menu 后注册实际类型
    // 当前暂不注册占位，避免 null factory 崩溃
}
