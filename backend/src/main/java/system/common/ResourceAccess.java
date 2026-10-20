package system.common;

import static system.common.DataStore.*;

import org.springframework.stereotype.Component;

import system.security.AccessScope;

import java.util.Map;

@Component
public class ResourceAccess {
    private final DataStore db;
    private final AccessScope scope;

    public ResourceAccess(DataStore db, AccessScope scope) {
        this.db = db;
        this.scope = scope;
    }

    public Map<String, Object> get(String table, long id, boolean lock) {
        if (!java.util.Set.of(
                        "house",
                        "owner",
                        "customer",
                        "house_show",
                        "house_deal",
                        "owner_entrustment")
                .contains(table)) throw new IllegalArgumentException("Invalid resource");
        Checks.require(id > 0, "ID 必须为正数");
        Map<String, Object> parameters = args("id", id);
        String condition = scope.sql("r", parameters);
        return db.one(
                "SELECT r.* FROM "
                        + table
                        + " r WHERE r.id=#{p.id} AND r.deleted=0 AND "
                        + condition
                        + (lock ? " FOR UPDATE" : ""),
                parameters);
    }

    public void store(long id) {
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM store WHERE id=#{p.id} AND"
                                        + " enabled=1",
                                args("id", id))
                        == 1,
                "门店不存在或已停用");
    }

    public void broker(long storeId, Long id) {
        if (id == null) return;
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM broker b WHERE b.id=#{p.id} AND"
                                    + " b.store_id=#{p.store} AND (b.user_id IS NULL OR EXISTS"
                                    + " (SELECT 1 FROM sys_user u JOIN sys_user_role ur ON"
                                    + " ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE"
                                    + " u.id=b.user_id AND u.broker_id=b.id AND u.enabled=1 AND"
                                    + " r.code='BROKER' AND r.enabled=1))",
                                args("id", id, "store", storeId))
                        == 1,
                "经纪人须属于该门店且账号为启用的经纪人身份，或为待绑定的原始档案");
    }

    public void sameStore(Map<String, Object> first, Map<String, Object> second) {
        Checks.require(
                first.get("storeId") != null
                        && second.get("storeId") != null
                        && id(first, "storeId") == id(second, "storeId"),
                "关联记录必须属于同一门店");
    }

    public Map<String, Object> forPage(
            String table, String filter, Map<String, Object> parameters, PageQuery page) {
        String condition = "deleted=0 AND " + scope.sql("", parameters) + filter;
        parameters.put("offset", page.offset());
        parameters.put("size", page.size());
        return args(
                "total",
                db.count(
                        "SELECT COUNT(*) AS value FROM " + table + " WHERE " + condition,
                        parameters),
                "page",
                page.page(),
                "size",
                page.size(),
                "records",
                db.list(
                        "SELECT * FROM "
                                + table
                                + " WHERE "
                                + condition
                                + " ORDER BY id DESC LIMIT #{p.size} OFFSET #{p.offset}",
                        parameters));
    }
}
