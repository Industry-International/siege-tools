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
 * KubeJS 插件 — 在 KubeJS 脚本加载完成后，从 Rhino scope 读取 PROF_CONFIGS 和
 * VANILLA_WEAPON_AMMO，构建弹药配置映射表。
 *
 * 不依赖 global.siege_tools_ammo_configs，直接读取已有的脚本全局变量。
 */
public class SiegeToolsKubeJSPlugin implements KubeJSPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType != ScriptType.SERVER) return;

        LOGGER.info("[SiegeToolsPlugin] KubeJS 服务端脚本加载完成，直接读取 PROF_CONFIGS...");

        Context cx = manager.contextFactory.enter();
        try {
            // 获取 Rhino 顶层的全局作用域
            Scriptable topScope = ((dev.latvian.mods.kubejs.script.KubeJSContext) cx).topLevelScope;

            // 读取 PROF_CONFIGS
            Object profConfigsObj = ScriptableObject.getProperty(topScope, "PROF_CONFIGS", cx);
            if (!(profConfigsObj instanceof Scriptable profConfigs)) {
                LOGGER.warn("[SiegeToolsPlugin] PROF_CONFIGS 未找到或类型错误");
                return;
            }

            // 读取 PROF_TAG_LIST
            Object tagListObj = ScriptableObject.getProperty(topScope, "PROF_TAG_LIST", cx);
            String[] profTags;
            if (tagListObj instanceof Scriptable tagList) {
                Object[] ids = tagList.getIds(cx);
                profTags = new String[ids.length];
                for (int i = 0; i < ids.length; i++) {
                    if (ids[i] instanceof String s) profTags[i] = s;
                    else if (ids[i] instanceof Number n) {
                        Object v = ScriptableObject.getProperty(tagList, n.intValue(), cx);
                        profTags[i] = v instanceof String s ? s : String.valueOf(v);
                    }
                }
            } else {
                LOGGER.warn("[SiegeToolsPlugin] PROF_TAG_LIST 未找到");
                return;
            }

            // 读取 VANILLA_WEAPON_AMMO
            Object vanillaAmmoObj = ScriptableObject.getProperty(topScope, "VANILLA_WEAPON_AMMO", cx);

            // 构建弹药配置 Map（序列化为 JSON 传给 clearAndRegister）
            Map<String, Map<String, Object>> configMap = new HashMap<>();

            // 遍历所有职业
            for (String prof : profTags) {
                Object profObj = ScriptableObject.getProperty(profConfigs, prof, cx);
                if (!(profObj instanceof Scriptable profCfg)) continue;

                Object gunsObj = ScriptableObject.getProperty(profCfg, "guns", cx);
                if (!(gunsObj instanceof Scriptable guns)) continue;

                // 遍历 primary / secondary
                for (Object catId : guns.getIds(cx)) {
                    if (!(catId instanceof String cat)) continue;
                    Object catObj = ScriptableObject.getProperty(guns, cat, cx);
                    if (!(catObj instanceof Scriptable catGuns)) continue;

                    for (Object widObj : catGuns.getIds(cx)) {
                        if (!(widObj instanceof String wid)) continue;
                        Object gunObj = ScriptableObject.getProperty(catGuns, wid, cx);
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
                }
            }

            // 添加非 TACZ 武器弹药
            if (vanillaAmmoObj instanceof Scriptable vanillaAmmo) {
                for (Object widObj : vanillaAmmo.getIds(cx)) {
                    if (!(widObj instanceof String wid)) continue;
                    Object ammoObj = ScriptableObject.getProperty(vanillaAmmo, wid, cx);
                    if (!(ammoObj instanceof Scriptable ammo)) continue;

                    Map<String, Object> entry = new HashMap<>();
                    entry.put("type", "vanilla");
                    entry.put("item", getStr(ammo, "item", cx));
                    entry.put("count", getInt(ammo, "count", 0, cx));
                    configMap.put(wid, entry);
                }
            }

            if (configMap.isEmpty()) {
                LOGGER.warn("[SiegeToolsPlugin] 未找到任何弹药配置");
                return;
            }

            String json = GSON.toJson(configMap);
            LOGGER.info("[SiegeToolsPlugin] 从 PROF_CONFIGS 读取到 {} 个武器配置", configMap.size());
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
