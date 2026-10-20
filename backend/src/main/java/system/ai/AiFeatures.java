package system.ai;

import static system.common.DataStore.*;

import system.auth.AuthService;
import system.common.*;

import java.util.*;

public final class AiFeatures {
    private AiFeatures() {}

    public static Map<String, Object> text(Map<String, Object> house) {
        return args(
                "coreSelling",
                Objects.toString(house.get("coreSelling"), ""),
                "communityIntro",
                Objects.toString(house.get("communityIntro"), ""),
                "surrounding",
                Objects.toString(house.get("surrounding"), ""),
                "district",
                Objects.toString(house.get("district"), ""));
    }

    public static Map<String, Object> price(Map<String, Object> house) {
        Map<String, Object> features = new LinkedHashMap<>();
        String[] keys = {
            "city",
            "district",
            "community_name",
            "business_area",
            "building_area_sqm",
            "rooms",
            "halls",
            "bathrooms",
            "floor_level",
            "total_floors",
            "orientation",
            "decoration",
            "has_elevator",
            "property_usage",
            "longitude",
            "latitude",
            "building_type",
            "layout_structure",
            "construction_structure",
            "ownership_type",
            "internal_area_sqm",
            "listing_date"
        };
        for (String key : keys) {
            String[] words = key.split("_");
            StringBuilder camel = new StringBuilder(words[0]);
            for (int i = 1; i < words.length; i++)
                camel.append(Character.toUpperCase(words[i].charAt(0)))
                        .append(words[i].substring(1));
            Object value = house.get(camel.toString());
            if ("has_elevator".equals(key) && value != null) value = truth(value);
            if ("listing_date".equals(key) && value != null) value = value.toString();
            features.put(key, value);
        }
        return features;
    }

    public static String hash(DataStore db, Object input) {
        return AuthService.hash(db.json(input));
    }

    public static void validatePrice(Map<String, Object> input) {
        for (String key :
                List.of(
                        "city",
                        "district",
                        "community_name",
                        "building_area_sqm",
                        "rooms",
                        "halls",
                        "bathrooms",
                        "floor_level",
                        "total_floors",
                        "orientation",
                        "decoration",
                        "property_usage"))
            Checks.require(input.get(key) != null, "估价前请补充字段：" + key);
        for (String key : List.of("building_area_sqm", "rooms", "total_floors"))
            Checks.require(
                    input.get(key) instanceof Number n && n.doubleValue() > 0,
                    "估价前请核对正数值字段：" + key);
        for (String key : List.of("halls", "bathrooms"))
            Checks.require(
                    input.get(key) instanceof Number n && n.doubleValue() >= 0,
                    "估价前请核对非负数字段：" + key);
        Checks.require(
                input.get("orientation") instanceof List<?> directions && !directions.isEmpty(),
                "估价前请补充朝向，未知可填写 UNKNOWN");
    }
}
