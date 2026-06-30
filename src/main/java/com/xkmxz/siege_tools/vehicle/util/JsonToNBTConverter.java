package com.xkmxz.siege_tools.vehicle.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.*;

/**
 * JSON 对象 → CompoundTag 转换工具。
 * 替代 KubeJS tools/nbt.js 的 toNBT() 和 mergeDeployNBT()。
 */
public class JsonToNBTConverter {

    /**
     * 将 Gson JsonObject 递归转换为 CompoundTag。
     */
    public static CompoundTag toCompoundTag(JsonObject obj) {
        CompoundTag tag = new CompoundTag();
        if (obj == null) return tag;
        for (String key : obj.keySet()) {
            JsonElement val = obj.get(key);
            Tag nbt = toTag(val);
            if (nbt != null) {
                tag.put(key, nbt);
            }
        }
        return tag;
    }

    /**
     * 将任意 JsonElement 转换为对应的 NBT Tag。
     */
    public static Tag toTag(JsonElement elem) {
        if (elem == null || elem.isJsonNull()) return null;

        if (elem.isJsonObject()) {
            return toCompoundTag(elem.getAsJsonObject());
        }

        if (elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            // 推断 ListTag 元素类型：如果第一个元素是数字则尝试数值列表
            ListTag list = new ListTag();
            for (int i = 0; i < arr.size(); i++) {
                Tag item = toTag(arr.get(i));
                if (item != null) {
                    list.add(item);
                }
            }
            return list;
        }

        if (elem.isJsonPrimitive()) {
            JsonPrimitive prim = elem.getAsJsonPrimitive();
            if (prim.isString()) {
                return StringTag.valueOf(prim.getAsString());
            }
            if (prim.isBoolean()) {
                return ByteTag.valueOf(prim.getAsBoolean());
            }
            if (prim.isNumber()) {
                Number num = prim.getAsNumber();
                double doubleVal = num.doubleValue();
                // 检查是否整数
                if (doubleVal == Math.floor(doubleVal) && !Double.isInfinite(doubleVal)) {
                    long longVal = num.longValue();
                    // 按值域选择合适类型
                    if (longVal >= Byte.MIN_VALUE && longVal <= Byte.MAX_VALUE) {
                        return ByteTag.valueOf((byte) longVal);
                    } else if (longVal >= Short.MIN_VALUE && longVal <= Short.MAX_VALUE) {
                        return ShortTag.valueOf((short) longVal);
                    } else if (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE) {
                        return IntTag.valueOf((int) longVal);
                    } else {
                        return LongTag.valueOf(longVal);
                    }
                } else {
                    return DoubleTag.valueOf(doubleVal);
                }
            }
        }

        return null;
    }

    /**
     * 将 deployNBT（用户 JSON）合并到目标 CompoundTag 中。
     * 对应 KubeJS mergeDeployNBT() 逻辑。
     */
    public static void mergeDeployNBT(CompoundTag target, JsonObject source) {
        if (source == null) return;
        for (String key : source.keySet()) {
            Tag incoming = toTag(source.get(key));
            if (incoming == null) continue;
            if (target.contains(key) && target.get(key) instanceof CompoundTag existing && incoming instanceof CompoundTag incomingCompound) {
                // 递归合并子对象
                for (String subKey : incomingCompound.getAllKeys()) {
                    existing.put(subKey, incomingCompound.get(subKey));
                }
            } else {
                target.put(key, incoming);
            }
        }
    }
}
