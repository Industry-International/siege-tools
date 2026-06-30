#!/usr/bin/env python3
"""
_registry.json 自动更新脚本。

扫描 sbw_vehicle_db/ 下所有分类目录中的 .json 文件，
自动更新 _registry.json 中每个分类的 files 列表。

用法：
    python3 scripts/update_registry.py
"""

import json
import os
import glob

DB_ROOT = "src/main/resources/data/siege_tools/sbw_vehicle_db"
REGISTRY = os.path.join(DB_ROOT, "_registry.json")


def main():
    # 读取现有 registry
    with open(REGISTRY, "r", encoding="utf-8") as f:
        registry = json.load(f)

    categories = registry.get("categories", {})
    changed = False

    # 扫描每个分类目录
    for cat_name in sorted(os.listdir(DB_ROOT)):
        cat_dir = os.path.join(DB_ROOT, cat_name)
        if not os.path.isdir(cat_dir) or cat_name.startswith("."):
            continue

        # 收集该目录下所有 .json 文件（排除 _ 开头的）
        files = sorted(
            f for f in os.listdir(cat_dir)
            if f.endswith(".json") and not f.startswith("_")
        )

        if cat_name not in categories:
            print(f"  [新增分类] {cat_name}（{len(files)} 个文件）")
            categories[cat_name] = {
                "displayName": cat_name,
                "description": "",
                "files": files
            }
            changed = True
            continue

        old_files = categories[cat_name].get("files", [])
        if old_files != files:
            print(f"  [更新] {cat_name}: {len(old_files)} → {len(files)} 个文件")
            categories[cat_name]["files"] = files
            changed = True

    # 写回
    if changed:
        with open(REGISTRY, "w", encoding="utf-8") as f:
            json.dump(registry, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print(f"\n完成！_registry.json 已更新")
    else:
        print(f"\n无变化，_registry.json 已是最新")


if __name__ == "__main__":
    main()
