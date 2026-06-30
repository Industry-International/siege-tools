package com.xkmxz.siege_tools.vehicle.registry;

import com.xkmxz.siege_tools.siege_tools;
import com.xkmxz.siege_tools.vehicle.gui.AmmoStationMenu;
import com.xkmxz.siege_tools.vehicle.gui.DeployerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * MenuType 注册。
 */
public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, siege_tools.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<AmmoStationMenu>> AMMO_STATION =
            MENUS.register("ammo_station", () -> new MenuType<>(
                    AmmoStationMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<DeployerMenu>> VEHICLE_DEPLOYER =
            MENUS.register("vehicle_deployer", () -> new MenuType<>(
                    DeployerMenu::new, FeatureFlags.DEFAULT_FLAGS));
}
