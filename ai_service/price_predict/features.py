"""Shared feature names and strict source-value parsers; prices never enter X."""
import ast
import math
import re

BASE_NUMERIC = ["building_area_sqm", "rooms", "halls", "bathrooms", "total_floors",
                "longitude", "latitude", "has_elevator"] + ["orientation_" + d for d in ("E", "S", "W", "N", "NE", "NW", "SE", "SW", "UNKNOWN")]
BASE_CATEGORICAL = ["district", "business_area", "community_name", "floor_level",
                    "decoration", "building_type", "layout_structure", "construction_structure", "ownership_type"]
DATE_FEATURES = ["listing_year", "listing_month"]
MISSING = {"", "暂无数据", "暂无", "未知", "nan", "None", "--"}
DIRECTIONS = {"东": "E", "南": "S", "西": "W", "北": "N", "东北": "NE", "西北": "NW", "东南": "SE", "西南": "SW"}

def text(value):
    value = str(value or "").strip()
    return "" if value in MISSING else value

def number(value, unit=""):
    value = text(value)
    if unit:
        value = value.removesuffix(unit).strip()
    if not re.fullmatch(r"[+-]?\d+(?:\.\d+)?", value):
        return float("nan")
    result = float(value)
    return result if math.isfinite(result) else float("nan")

def community(value):
    value = text(value)
    if value.startswith("["):
        try:
            parsed = ast.literal_eval(value)
        except (ValueError, SyntaxError):
            return ""
        if not isinstance(parsed, list) or len(parsed) != 1 or not isinstance(parsed[0], str):
            return ""
        value = text(parsed[0])
    # Typography only: preserve phase, north/south qualifiers and substantive names.
    return value.translate(str.maketrans({"（": "(", "）": ")"}))

def normalize_row(row):
    district, _, business_area = text(row.get("区域位置")).partition("-")
    district = district.removesuffix("区")
    layout = text(row.get("房屋户型"))
    floors = text(row.get("所在楼层"))
    elevator = text(row.get("配备电梯"))
    result = {
        "district": district or "UNKNOWN", "business_area": business_area or "UNKNOWN",
        "community_name": community(row.get("小区名称")),
        "building_area_sqm": number(row.get("建筑面积"), "㎡"),
        "longitude": number(row.get("经度")), "latitude": number(row.get("纬度")),
        "has_elevator": {"有": 1.0, "无": 0.0}.get(elevator, float("nan")),
        "floor_level": next((v for k, v in [("低楼层", "LOW"), ("中楼层", "MIDDLE"), ("高楼层", "HIGH")] if k in floors), "UNKNOWN"),
        "total_floors": float(re.search(r"共\s*(\d+)\s*层", floors).group(1)) if re.search(r"共\s*(\d+)\s*层", floors) else float("nan"),
        "decoration": {"毛坯": "ROUGH", "简装": "SIMPLE", "精装": "FINE", "其他": "OTHER"}.get(text(row.get("装修情况")), "UNKNOWN"),
        "building_type": {"板楼": "SLAB", "塔楼": "TOWER", "板塔结合": "SLAB_TOWER", "平房": "BUNGALOW"}.get(text(row.get("建筑类型")), "UNKNOWN"),
        "layout_structure": {"平层": "FLAT", "跃层": "DUPLEX", "复式": "DUPLEX", "错层": "SPLIT_LEVEL", "Loft": "LOFT", "loft": "LOFT"}.get(text(row.get("户型结构")), "UNKNOWN"),
        "construction_structure": {"钢混结构": "STEEL_CONCRETE", "砖混结构": "BRICK_CONCRETE", "混合结构": "MIXED", "框架结构": "FRAME", "钢结构": "STEEL", "砖木结构": "BRICK_WOOD"}.get(text(row.get("建筑结构")), "UNKNOWN"),
        "ownership_type": {"商品房": "COMMERCIAL", "已购公房": "PUBLIC_PURCHASED", "回迁房": "RELOCATION", "私产": "PRIVATE"}.get(text(row.get("交易权属")), "UNKNOWN"),
    }
    for name, suffix in [("rooms", "室"), ("halls", "厅"), ("bathrooms", "卫")]:
        match = re.search(r"(\d+)" + suffix, layout)
        result[name] = float(match.group(1)) if match else float("nan")
    direction_text = text(row.get("房屋朝向"))
    parts = direction_text.split()
    valid = bool(parts) and all(p in DIRECTIONS for p in parts)
    directions = {DIRECTIONS[p] for p in parts} if valid else {"UNKNOWN"}
    for direction in ("E", "S", "W", "N", "NE", "NW", "SE", "SW", "UNKNOWN"):
        result["orientation_" + direction] = float(direction in directions)
    for field, bounds in [("longitude", (-180, 180)), ("latitude", (-90, 90))]:
        if not bounds[0] <= result[field] <= bounds[1]:
            result[field] = float("nan")
    if math.isnan(result["longitude"]) or math.isnan(result["latitude"]):
        result["longitude"] = result["latitude"] = float("nan")
    return result
