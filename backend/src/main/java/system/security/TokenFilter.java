package system.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import system.auth.AuthService;
import system.common.ApiResponse;

import java.io.IOException;

public class TokenFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final ObjectMapper json;

    public TokenFilter(AuthService auth, ObjectMapper json) {
        this.auth = auth;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                UserPrincipal user = auth.authenticate(header.substring(7));
                var authorities =
                        user.permissions().stream().map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                new UsernamePasswordAuthenticationToken(user, null, authorities));
            } catch (system.common.BusinessException exception) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                json.writeValue(
                        response.getOutputStream(), ApiResponse.failure(401, "登录已失效，请重新登录"));
                return;
            } catch (org.springframework.dao.DataAccessException exception) {
                response.setStatus(503);
                response.setContentType("application/json;charset=UTF-8");
                json.writeValue(
                        response.getOutputStream(),
                        ApiResponse.failure(503, "数据库暂不可用，请检查连接及增量表结构"));
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
