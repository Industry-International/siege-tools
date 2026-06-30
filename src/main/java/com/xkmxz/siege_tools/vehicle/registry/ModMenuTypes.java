package com.xkmxz.siege_tools.vehicle.registry;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.xkmxz.siege_tools.siege_tools;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, siege_tools.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> AMMO_STATION =
            MENUS.register("ammo_station", () -> new MenuType<>(
                    (id, inv) -> null, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> VEHICLE_DEPLOYER =
            MENUS.register("vehicle_deployer", () -> new MenuType<>(
                    (id, inv) -> null, FeatureFlags.DEFAULT_FLAGS));
}
