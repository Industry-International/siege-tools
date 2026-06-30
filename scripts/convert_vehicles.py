#!/usr/bin/env python3
"""
载具数据文件格式转换脚本：旧格式 → 新格式 v1

用法：python3 scripts/convert_vehicles.py
将在原位置生成新格式文件（覆盖原文件前会备份到 .backup/）

旧格式 → 新格式映射：
  maxHealth → stats.maxHealth
  baseName → 去掉（可推导）
  category → 去掉（从目录名推断）
  ammoSlots → defaultAmmo
  nbtTemplate → spawnNbt（去掉 Inventory.Items 和 WeaponState）
  weapons 中去掉空武器（无ammoTypes）、去掉 displayKey/hasOverride
"""

import json
import os
import shutil

DB_ROOT = "src/main/resources/data/siege_tools/sbw_vehicle_db"
BACKUP_DIR = os.path.join(DB_ROOT, ".backup")


def is_vehicle_json(filename):
    return filename.endswith(".json") and not filename.startswith("_")


def convert_vehicle(data):
    """转换单辆车的数据"""
    new = {}
    new["formatVersion"] = data.get("version", 1)
    new["vehicleId"] = data["vehicleId"]
    if "mod" in data:
        new["mod"] = data["mod"]

    # display metadata
    if "displayType" in data and data["displayType"] is not None:
        new["displayType"] = data["displayType"]
    if "hudType" in data and data["hudType"] is not None:
        new["hudType"] = data["hudType"]

    # stats group
    stats = {}
    for field in ["maxHealth", "maxEnergy", "mass", "upStep", "seatCount", "containerType", "engineType", "hasDecoy"]:
        if field in data and data[field] is not None:
            # convert field names
            if field == "maxHealth":
                stats["maxHealth"] = data[field]
            elif field == "maxEnergy":
                stats["maxEnergy"] = data[field]
            elif field == "containerType":
                stats["containerType"] = data[field]
            elif field == "engineType":
                stats["engineType"] = data[field]
            elif field == "hasDecoy":
                stats["hasDecoy"] = data[field]
            else:
                stats[field] = data[field]
    if stats:
        new["stats"] = stats

    # parts
    if "parts" in data and data["parts"]:
        new["parts"] = data["parts"]

    # weapons — filter out empty ones, remove displayKey/hasOverride
    if "weapons" in data and data["weapons"]:
        cleaned = []
        for w in data["weapons"]:
            # skip weapons with no ammo types
            if not w.get("ammoTypes") or len(w["ammoTypes"]) == 0:
                continue
            nw = {"key": w["key"]}
            if w.get("displayKey") and w["displayKey"] != w["key"]:
                nw["displayKey"] = w["displayKey"]
            nw["ammoTypes"] = w["ammoTypes"]
            nw["magazine"] = w.get("magazine", 1)
            if w.get("rpm") is not None:
                nw["rpm"] = w["rpm"]
            if w.get("damage") is not None:
                nw["damage"] = w["damage"]
            cleaned.append(nw)
        if cleaned:
            new["weapons"] = cleaned

    # defaultAmmo (was ammoSlots)
    if "ammoSlots" in data and data["ammoSlots"]:
        new["defaultAmmo"] = data["ammoSlots"]

    # spawnNbt (was nbtTemplate, minus Inventory and WeaponState)
    if "nbtTemplate" in data and data["nbtTemplate"]:
        nbt = dict(data["nbtTemplate"])
        nbt.pop("WeaponState", None)
        nbt.pop("Inventory", None)
        if nbt:
            new["spawnNbt"] = nbt

    return new


def main():
    os.makedirs(BACKUP_DIR, exist_ok=True)

    total = 0
    for cat in sorted(os.listdir(DB_ROOT)):
        cat_path = os.path.join(DB_ROOT, cat)
        if not os.path.isdir(cat_path) or cat.startswith("."):
            continue

        for filename in sorted(os.listdir(cat_path)):
            if not is_vehicle_json(filename):
                continue

            filepath = os.path.join(cat_path, filename)
            with open(filepath, "r", encoding="utf-8") as f:
                data = json.load(f)

            if "formatVersion" in data:
                print(f"  [跳过] {filename} 已为新格式")
                continue

            # backup original
            backup_path = os.path.join(BACKUP_DIR, f"{cat}__{filename}")
            shutil.copy2(filepath, backup_path)

            # convert and write
            new_data = convert_vehicle(data)
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(new_data, f, indent=2, ensure_ascii=False)
                f.write("\n")

            print(f"  [转换] {cat}/{filename} ({len(json.dumps(new_data))} bytes)")
            total += 1

    print(f"\n完成！共转换 {total} 个车辆文件。备份在 {BACKUP_DIR}/")


if __name__ == "__main__":
    main()
