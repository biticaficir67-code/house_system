package system.system;

import static system.common.DataStore.*;

import org.springframework.stereotype.Component;

import system.common.Checks;
import system.common.DataStore;

import java.util.*;
import java.util.stream.Collectors;

/** Write the team's broker fields, keeping its two existing name columns consistent. */
@Component
public class BrokerProfiles {
    private final DataStore db;

    public BrokerProfiles(DataStore db) {
        this.db = db;
    }

    private Set<String> columns() {
        return db
                .list(
                        "SELECT column_name AS field_name FROM information_schema.columns"
                                + " WHERE table_schema=DATABASE() AND table_name='broker'",
                        args())
                .stream()
                .map(row -> row.get("fieldName").toString().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private Map<String, Object> names(Map<String, Object> values, Set<String> columns) {
        var result = new LinkedHashMap<>(values);
        if (columns.contains("name") && values.containsKey("broker_name"))
            result.put("name", values.get("broker_name"));
        if (!columns.contains("broker_name")) result.remove("broker_name");
        return result;
    }

    public String nameColumn() {
        return columns().contains("name") ? "name" : "broker_name";
    }

    public long create(Map<String, Object> values, String username, String passwordHash) {
        Set<String> columns = columns();
        var result = names(values, columns);
        if (columns.contains("username")) {
            Checks.require(username != null && username.length() <= 50, "登录名最多50位");
            result.put("username", username);
        }
        if (columns.contains("password")) {
            Checks.require(passwordHash != null && passwordHash.startsWith("$2"), "经纪人档案缺少账号密码摘要");
            result.put("password", passwordHash);
        }
        if (columns.contains("audit_status")) result.putIfAbsent("audit_status", 1);
        if (columns.contains("account_status")) result.putIfAbsent("account_status", 1);
        return db.insert("broker", result);
    }

    public void update(long id, Map<String, Object> values) {
        db.update("broker", id, names(values, columns()));
    }
}
