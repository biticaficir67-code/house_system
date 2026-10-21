package system.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import system.common.ApiResponse;
import system.security.AccessScope;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AccessScope scope;
    private final RegistrationService registration;

    public AuthController(AuthService auth, AccessScope scope, RegistrationService registration) {
        this.auth = auth;
        this.scope = scope;
        this.registration = registration;
    }

    @PostMapping("/register")
    public ApiResponse<?> register(@Valid @RequestBody AuthModels.Register b) {
        return ApiResponse.success(registration.register(b));
    }

    @PostMapping("/application-session")
    public ApiResponse<?> applicationSession(
            @Valid @RequestBody AuthModels.Login b, HttpServletRequest request) {
        return ApiResponse.success(registration.session(b, request.getRemoteAddr()));
    }

    @GetMapping("/application")
    public ApiResponse<?> application(@RequestHeader("X-Application-Token") String token) {
        return ApiResponse.success(registration.application(token));
    }

    @PutMapping("/application")
    public ApiResponse<?> reapply(
            @RequestHeader("X-Application-Token") String token,
            @Valid @RequestBody AuthModels.Reapply b) {
        return ApiResponse.success(registration.reapply(token, b));
    }

    @GetMapping("/profile")
    public ApiResponse<?> profile() {
        return ApiResponse.success(registration.profile());
    }

    @PutMapping("/profile")
    public ApiResponse<?> profile(@Valid @RequestBody AuthModels.Profile b) {
        return ApiResponse.success(registration.profile(b));
    }

    @PostMapping("/bootstrap")
    public ApiResponse<?> bootstrap(
            @RequestHeader(value = "X-Bootstrap-Key", required = false) String key,
            @Valid @RequestBody AuthModels.Bootstrap body) {
        return ApiResponse.success(auth.bootstrap(key, body));
    }

    @PostMapping("/login")
    public ApiResponse<?> login(
            @Valid @RequestBody AuthModels.Login body, HttpServletRequest request) {
        return ApiResponse.success(auth.login(body, request.getRemoteAddr()));
    }

    @GetMapping("/me")
    public ApiResponse<?> me() {
        return ApiResponse.success(scope.user());
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(@RequestHeader("Authorization") String header) {
        auth.logout(header.substring(7));
        return ApiResponse.success(null);
    }

    @PutMapping("/password")
    public ApiResponse<?> password(@Valid @RequestBody AuthModels.ChangePassword body) {
        auth.changePassword(scope.user().id(), body);
        return ApiResponse.success(null);
    }

    @GetMapping("/menus")
    public ApiResponse<?> menus() {
        var user = scope.user();
        return ApiResponse.success(
                java.util.List.of(
                                menu(
                                        "workbench",
                                        user.administrator() ? "管理工作台" : "我的工作台",
                                        "system:read"),
                                menu("houses", "房源管理", "house:read"),
                                menu("owners", "业主管理", "owner:read"),
                                menu("customers", "客户管理", "customer:read"),
                                menu("showings", "预约带看", "showing:read"),
                                menu("deals", "成交记录", "deal:read"),
                                menu(
                                        "reports",
                                        user.administrator() ? "全局报表" : "个人报表",
                                        "report:read"),
                                menu("system", "系统管理", "system:manage"))
                        .stream()
                        .filter(menu -> user.permissions().contains(menu.get("permission")))
                        .toList());
    }

    private java.util.Map<String, String> menu(String key, String name, String permission) {
        return java.util.Map.of("key", key, "name", name, "permission", permission);
    }
}
