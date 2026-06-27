package com.xkmxz.siege_tools.plugin;

import com.mojang.logging.LogUtils;
import com.xkmxz.siege_tools.api.SiegeToolsAPI;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.plugin.builtin.BuiltinKubeJSPlugin;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.slf4j.Logger;

/**
 * KubeJS 插件 — 在 KubeJS 脚本加载完成后，从 global 读取弹药配置数据。
 *
 * KubeJS 脚本侧需在 z_tacz_config_build.js 末尾将弹药配置写入 global：
 *   global.siege_tools_ammo_configs = JSON.stringify(ammoConfigMap);
 *
 * global 在 Java 侧由 BuiltinKubeJSPlugin.GLOBAL (HashMap) 承载，
 * 脚本的 global.xxx = value 等价于 GLOBAL.put("xxx", value)。
 */
public class SiegeToolsKubeJSPlugin implements KubeJSPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        // 只在服务端脚本加载完成后读取
        if (manager.scriptType != ScriptType.SERVER) return;

        LOGGER.info("[SiegeToolsPlugin] KubeJS 服务端脚本加载完成，读取弹药配置...");

        // 从 global 中读取 ammo 配置（脚本写入: global.siege_tools_ammo_configs = JSON.stringify(...)）
        Object raw = BuiltinKubeJSPlugin.GLOBAL.get("siege_tools_ammo_configs");

        if (raw instanceof String json && !json.isEmpty()) {
            LOGGER.info("[SiegeToolsPlugin] 从 global 读取到弹药配置 JSON, 长度={}", json.length());
            SiegeToolsAPI.clearAndRegister(json);
        } else {
            LOGGER.warn("[SiegeToolsPlugin] global.siege_tools_ammo_configs 未找到或格式不正确, raw={}", raw);
            if (raw != null) {
                LOGGER.warn("[SiegeToolsPlugin] 类型: {}", raw.getClass().getName());
            }
        }
    }
}
