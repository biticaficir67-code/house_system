package system.security;

import java.util.Set;

public record UserPrincipal(
        long id,
        String username,
        String displayName,
        Long storeId,
        Long brokerId,
        Set<String> roles,
        Set<String> permissions,
        String dataScope) {
    public boolean administrator() {
        return roles.contains("ADMIN");
    }
}
