package system.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import system.common.BusinessException;

import java.util.Map;

@Component
public class AccessScope {
    public UserPrincipal user() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserPrincipal principal))
            throw new BusinessException(401, "请先登录");
        return principal;
    }

    public String sql(String alias, Map<String, Object> parameters) {
        UserPrincipal user = user();
        String prefix = alias == null || alias.isEmpty() ? "" : alias + ".";
        if (user.administrator()) return "1=1";
        parameters.put("_scopeStore", user.storeId());
        String sql = prefix + "store_id=#{p._scopeStore}";
        if (user.brokerId() == null) return "1=0";
        parameters.put("_scopeBroker", user.brokerId());
        sql += " AND " + prefix + "broker_id=#{p._scopeBroker}";
        return "(" + sql + ")";
    }

    public long store(Long requested) {
        UserPrincipal user = user();
        if (!user.administrator() && requested != null && !requested.equals(user.storeId()))
            throw new BusinessException(403, "经纪人只能操作自己门店的业务");
        Long result = user.administrator() ? requested : user.storeId();
        if (result == null) throw new BusinessException(400, "必须指定门店");
        return result;
    }

    public Long broker(Long requested) {
        UserPrincipal user = user();
        if (!user.administrator() && user.brokerId() == null)
            throw new BusinessException(403, "账号尚未绑定经纪人，请联系管理员");
        if (!user.administrator() && requested != null && !requested.equals(user.brokerId()))
            throw new BusinessException(403, "经纪人不能把业务指定给其他人，请联系管理员交接");
        return user.administrator() ? requested : user.brokerId();
    }
}
