package com.guizhou.wudong.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Configuration
@Profile("legacy-v2")
public class AdminAccessConfiguration {
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    );

    @Bean
    FilterRegistrationBean<Filter> adminTokenFilter(@Value("${admin.api-token:}") String configuredToken) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AdminTokenFilter(configuredToken));
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(-100);
        return registration;
    }

    private static final class AdminTokenFilter implements Filter {
        private final byte[] expected;

        private AdminTokenFilter(String configuredToken) {
            this.expected = configuredToken == null || configuredToken.isBlank()
                    ? new byte[0]
                    : configuredToken.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
                chain.doFilter(request, response);
                return;
            }
            if (expected.length == 0) {
                reject(httpRequest, httpResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "ADMIN_NOT_CONFIGURED",
                        "运营后台尚未配置访问令牌");
                return;
            }
            String actualToken = httpRequest.getHeader("X-Admin-Token");
            byte[] actual = actualToken == null ? new byte[0] : actualToken.getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, actual)) {
                reject(httpRequest, httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "ADMIN_TOKEN_INVALID",
                        "运营后台访问令牌无效");
                return;
            }
            chain.doFilter(request, response);
        }

        private void reject(HttpServletRequest request, HttpServletResponse response, int status, String code,
                            String message) throws IOException {
            String origin = request.getHeader("Origin");
            if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Vary", "Origin");
            }
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"" + message
                    + "\",\"code\":\"" + code + "\"}");
        }
    }
}
