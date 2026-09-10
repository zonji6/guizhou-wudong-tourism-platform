package com.guizhou.wudong.v3;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guizhou.wudong.api.ApiEnvelope;
import com.guizhou.wudong.api.ApiRequestException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Configuration
@Profile("!legacy-v2")
public class V3SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt",
                Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictJsonCustomizer() {
        return builder -> builder.featuresToEnable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @Bean
    CorsConfigurationSource v3CorsConfigurationSource(
            @Value("${wudong.public-web-origin:http://127.0.0.1:5174}") String origin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key",
                "X-Wudong-Contract", "X-Wudong-CSRF"));
        configuration.setExposedHeaders(List.of("Retry-After", "X-Request-Id", "X-Wudong-Contract"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain v3SecurityFilterChain(HttpSecurity http, V3IdentityService identityService,
                                               ObjectMapper objectMapper,
                                               @Value("${wudong.internal.ai-to-java-credential-file:}") String aiCredentialFile,
                                               @Value("${wudong.internal.java-to-ai-credential-file:}") String javaCredentialFile)
            throws Exception {
        V3ProtocolFilter protocol = new V3ProtocolFilter(objectMapper);
        V3AuthenticationFilter authentication = new V3AuthenticationFilter(identityService, objectMapper,
                aiCredentialFile, javaCredentialFile);
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**", "/api/admin/auth/csrf", "/api/admin/auth/login",
                                "/api/admin/auth/refresh", "/api/anonymous/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/food-merchants/**",
                                "/api/foods/**", "/api/stays/**", "/api/room-types/**", "/api/places/**",
                                "/api/posts/**", "/api/knowledge-documents/**").permitAll()
                        .requestMatchers("/api/me/**", "/api/order-quotes/**", "/api/product-orders",
                                "/api/food-orders", "/api/stay-bookings").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/posts").hasRole("USER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/internal/**").hasRole("SERVICE")
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, objectMapper, 401, "AUTH_REQUIRED", "请先登录"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, objectMapper, 403, "FORBIDDEN", "当前身份无权访问此接口")))
                .addFilterBefore(protocol, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(authentication, V3ProtocolFilter.class);
        return http.build();
    }

    private static final class V3ProtocolFilter extends OncePerRequestFilter {
        private static final Set<String> LEGACY_PATHS = Set.of(
                "/api/services", "/api/bookings", "/internal/agent/pending-bookings");
        private final ObjectMapper objectMapper;

        private V3ProtocolFilter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String path = request.getRequestURI();
            if (!(path.startsWith("/api/") || path.startsWith("/internal/"))) {
                chain.doFilter(request, response);
                return;
            }
            response.setHeader("X-Wudong-Contract", V3Support.CONTRACT);
            response.setHeader("X-Request-Id", UUID.randomUUID().toString());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            if ("OPTIONS".equals(request.getMethod())) {
                chain.doFilter(request, response);
                return;
            }
            if (!V3Support.CONTRACT.equals(request.getHeader("X-Wudong-Contract"))
                    || request.getHeader("X-Visitor-Id") != null || request.getHeader("X-Admin-Token") != null) {
                writeError(response, objectMapper, 400, "CONTRACT_INCOMPATIBLE", "客户端协议版本不兼容");
                return;
            }
            if (LEGACY_PATHS.stream().anyMatch(path::startsWith)) {
                writeError(response, objectMapper, 410, "LEGACY_ENDPOINT_DISABLED", "旧接口已停用");
                return;
            }
            if (Set.of("POST", "PUT", "PATCH").contains(request.getMethod())) {
                String contentType = request.getContentType();
                if (contentType == null || !contentType.toLowerCase().startsWith(MediaType.APPLICATION_JSON_VALUE)) {
                    writeError(response, objectMapper, 400, "VALIDATION_FAILED", "JSON 请求必须使用 application/json");
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }

    private static final class V3AuthenticationFilter extends OncePerRequestFilter {
        private static final Pattern INTERNAL_ITEM = Pattern.compile(
                "^/internal/agent/(itineraries|food-drafts|stay-drafts|knowledge-build-inputs)/[0-9a-f-]{36}$");
        private static final Set<String> INTERNAL_GET = Set.of(
                "/internal/agent/products/search", "/internal/agent/foods/search",
                "/internal/agent/stays/search", "/internal/agent/places/search",
                "/internal/agent/route-guides/search", "/internal/agent/knowledge/search",
                "/internal/agent/orders/status", "/internal/agent/knowledge/active-builds");
        private static final Set<String> INTERNAL_POST = Set.of(
                "/internal/agent/anonymous/interactions", "/internal/agent/knowledge/eligibility",
                "/internal/agent/run-summaries");
        private final V3IdentityService identityService;
        private final ObjectMapper objectMapper;
        private final String aiCredentialFile;
        private final String javaCredentialFile;

        private V3AuthenticationFilter(V3IdentityService identityService, ObjectMapper objectMapper,
                                       String aiCredentialFile, String javaCredentialFile) {
            this.identityService = identityService;
            this.objectMapper = objectMapper;
            this.aiCredentialFile = aiCredentialFile;
            this.javaCredentialFile = javaCredentialFile;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            try {
                String path = request.getRequestURI();
                if (path.startsWith("/internal/")) {
                    authenticateInternal(request);
                } else {
                    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
                    if (authorization != null) {
                        if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) {
                            throw new ApiRequestException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                                    "AUTH_REQUIRED", "登录凭据格式无效");
                        }
                        V3Support.V3Principal principal = identityService.authenticate(authorization.substring(7));
                        SecurityContextHolder.getContext().setAuthentication(
                                UsernamePasswordAuthenticationToken.authenticated(principal, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.purpose()))));
                    }
                }
                chain.doFilter(request, response);
            } catch (ApiRequestException exception) {
                writeError(response, objectMapper, exception.status().value(), exception.code(),
                        exception.getMessage(), exception.details());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        private void authenticateInternal(HttpServletRequest request) {
            String path = request.getRequestURI();
            boolean allowed = ("GET".equals(request.getMethod())
                    && (INTERNAL_GET.contains(path) || INTERNAL_ITEM.matcher(path).matches()))
                    || ("POST".equals(request.getMethod()) && INTERNAL_POST.contains(path));
            if (!allowed || !isLoopback(request.getRemoteAddr())) {
                throw new ApiRequestException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "INTERNAL_FORBIDDEN", "内部接口访问被拒绝");
            }
            String expected = readCredential(aiCredentialFile);
            String outbound = readCredential(javaCredentialFile);
            if (expected == null || outbound == null || constantEquals(expected, outbound)) {
                throw new ApiRequestException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "INTERNAL_AUTH_UNAVAILABLE", "内部认证尚未正确配置");
            }
            String actual = request.getHeader("X-Wudong-Service-Credential");
            if (!constantEquals(expected, actual)) {
                throw new ApiRequestException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "INTERNAL_FORBIDDEN", "内部接口访问被拒绝");
            }
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated("wudong-ai-local", null,
                            List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
        }

        private static String readCredential(String file) {
            if (file == null || file.isBlank()) {
                return null;
            }
            try {
                String value = Files.readString(Path.of(file), StandardCharsets.US_ASCII);
                if (value.endsWith("\r\n")) {
                    value = value.substring(0, value.length() - 2);
                } else if (value.endsWith("\n")) {
                    value = value.substring(0, value.length() - 1);
                }
                return value.matches("^[A-Za-z0-9_-]{43}$") ? value : null;
            } catch (Exception exception) {
                return null;
            }
        }

        private static boolean constantEquals(String left, String right) {
            byte[] a = left == null ? new byte[0] : left.getBytes(StandardCharsets.US_ASCII);
            byte[] b = right == null ? new byte[0] : right.getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(a, b);
        }

        private static boolean isLoopback(String remoteAddress) {
            try {
                return InetAddress.getByName(remoteAddress).isLoopbackAddress();
            } catch (Exception exception) {
                return false;
            }
        }
    }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper, int status,
                                   String code, String message) throws IOException {
        writeError(response, mapper, status, code, message, null);
    }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper, int status,
                                   String code, String message, Object details) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(mapper.writeValueAsString(ApiEnvelope.fail(code, message, details)));
    }
}
