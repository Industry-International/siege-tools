package com.xkmxz.siege_tools.plugin;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.xkmxz.siege_tools.api.SiegeToolsAPI;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * KubeJS 插件 — 在 KubeJS 脚本加载完成后，从 Rhino scope 读取新 profession 模块的
 * GUN_TACZ_FLAT 和 VANILLA_WEAPON_AMMO，构建弹药配置映射表。
 *
 * 适配最新 server_scripts/profession 数据驱动架构：
 * - 旧变量 PROF_CONFIGS / PROF_TAG_LIST 已移除
 * - 改用兼容层全局 GUN_TACZ_FLAT（平铺的 weaponId → 武器数据 Map）
 * - VANILLA_WEAPON_AMMO 仍由兼容层提供
 *
 * GUN_TACZ_FLAT 结构：
 *   { weaponId: { gunId, GunFireMode, GunCurrentAmmoCount,
 *                 ammo: { ammoId, main, offhand, level },
 *                 attachments: {...} } }
 */
public class SiegeToolsKubeJSPlugin implements KubeJSPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType != ScriptType.SERVER) return;

        LOGGER.info("[SiegeToolsPlugin] KubeJS 服务端脚本加载完成，读取 GUN_TACZ_FLAT + VANILLA_WEAPON_AMMO ...");

        Context cx = manager.contextFactory.enter();
        try {
            Scriptable topScope = ((dev.latvian.mods.kubejs.script.KubeJSContext) cx).topLevelScope;

            // ========== 读取 TACZ 武器（从平铺 Map GUN_TACZ_FLAT） ==========
            Object gunFlatObj = ScriptableObject.getProperty(topScope, "GUN_TACZ_FLAT", cx);

            // ========== 读取非 TACZ 武器弹药 ==========
            Object vanillaAmmoObj = ScriptableObject.getProperty(topScope, "VANILLA_WEAPON_AMMO", cx);

            Map<String, Map<String, Object>> configMap = new HashMap<>();

            // 从 GUN_TACZ_FLAT 读取所有 TACZ 武器
            if (gunFlatObj instanceof Scriptable gunFlat) {
                for (Object widObj : gunFlat.getIds(cx)) {
                    if (!(widObj instanceof String wid)) continue;
                    Object gunObj = ScriptableObject.getProperty(gunFlat, wid, cx);
                    if (!(gunObj instanceof Scriptable gunCfg)) continue;

                    Object ammoObj = ScriptableObject.getProperty(gunCfg, "ammo", cx);
                    if (!(ammoObj instanceof Scriptable ammo)) continue;

                    Map<String, Object> entry = new HashMap<>();
                    entry.put("type", "tacz");
                    entry.put("ammoId", getStr(ammo, "ammoId", cx));
                    entry.put("main", getInt(ammo, "main", 0, cx));
                    entry.put("offhand", getInt(ammo, "offhand", 0, cx));
                    entry.put("level", getInt(ammo, "level", 0, cx));
                    entry.put("gunId", getStr(gunCfg, "gunId", cx));
                    configMap.put(wid, entry);
                }
                LOGGER.info("[SiegeToolsPlugin] 从 GUN_TACZ_FLAT 读取到 {} 个 TACZ 武器", configMap.size());
            } else {
                LOGGER.warn("[SiegeToolsPlugin] GUN_TACZ_FLAT 未找到或类型错误");
            }

            // 添加非 TACZ 武器弹药
            if (vanillaAmmoObj instanceof Scriptable vanillaAmmo) {
                int vanillaCount = 0;
                for (Object widObj : vanillaAmmo.getIds(cx)) {
                    if (!(widObj instanceof String wid)) continue;
                    Object ammoObj = ScriptableObject.getProperty(vanillaAmmo, wid, cx);
                    if (!(ammoObj instanceof Scriptable ammo)) continue;

                    Map<String, Object> entry = new HashMap<>();
                    entry.put("type", "vanilla");
                    entry.put("item", getStr(ammo, "item", cx));
                    entry.put("count", getInt(ammo, "count", 0, cx));
                    configMap.put(wid, entry);
                    vanillaCount++;
                }
                LOGGER.info("[SiegeToolsPlugin] 从 VANILLA_WEAPON_AMMO 读取到 {} 个非 TACZ 武器", vanillaCount);
            }

            if (configMap.isEmpty()) {
                LOGGER.warn("[SiegeToolsPlugin] 未找到任何弹药配置（GUN_TACZ_FLAT 或 VANILLA_WEAPON_AMMO 为空）");
                return;
            }

            String json = GSON.toJson(configMap);
            LOGGER.info("[SiegeToolsPlugin] 共注册 {} 个武器的弹药配置", configMap.size());
            SiegeToolsAPI.clearAndRegister(json);

        } catch (Exception e) {
            LOGGER.error("[SiegeToolsPlugin] 读取弹药配置失败: {}", e.getMessage(), e);
        }
    }

    private static String getStr(Scriptable obj, String key, Context cx) {
        Object v = ScriptableObject.getProperty(obj, key, cx);
        return v instanceof String s ? s : (v != null ? String.valueOf(v) : "");
    }

    private static int getInt(Scriptable obj, String key, int def, Context cx) {
        Object v = ScriptableObject.getProperty(obj, key, cx);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) try { return Integer.parseInt(s); } catch (Exception e) { /* ignore */ }
        return def;
    }
}
