package system.security;

import java.util.Set;

public final class Permissions {
    private Permissions() {}

    public static final Set<String> ROLES = Set.of("ADMIN", "BROKER");

    public static final Set<String> BROKER =
            Set.of(
                    "system:read",
                    "house:read",
                    "house:write",
                    "owner:read",
                    "owner:write",
                    "customer:read",
                    "customer:write",
                    "showing:read",
                    "showing:write",
                    "deal:read",
                    "deal:write",
                    "ai:use",
                    "report:read");

    public static final Set<String> ALL =
            Set.of(
                    "system:manage",
                    "system:read",
                    "house:read",
                    "house:write",
                    "house:verify",
                    "house:assign",
                    "owner:read",
                    "owner:write",
                    "customer:read",
                    "customer:write",
                    "showing:read",
                    "showing:write",
                    "deal:read",
                    "deal:write",
                    "deal:verify",
                    "ai:use",
                    "report:read");
}
