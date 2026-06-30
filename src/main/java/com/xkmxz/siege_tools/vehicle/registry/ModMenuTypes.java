package com.xkmxz.siege_tools.vehicle.registry;

import com.xkmxz.siege_tools.siege_tools;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * MenuType 注册（保留以备将来使用）。
 * 当前使用 KubeJS 的 LDLib2 UI（KJSBlockUIMenuType），不需要自定义 MenuType。
 */
public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, siege_tools.MODID);
}
