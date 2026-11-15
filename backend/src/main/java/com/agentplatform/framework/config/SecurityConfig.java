package com.agentplatform.framework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import com.agentplatform.framework.security.JwtBlacklistValidator;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 总配置。
 *
 * <p>位于技术框架层，负责：BCrypt 密码编码、HS256 JWT 签发/解析（可叠加 OIDC）、
 * 无状态过滤器链、方法级 {@code @PreAuthorize} 授权、CORS 白名单。
 * 关键约束：认证完全无状态（不建 Session），所有非白名单接口必须携带 Bearer JWT；
 * 注销后的 Token 由 Redis 黑名单校验器拦截。</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 密码编码器。
     *
     * <p>使用 Spring Security 自带的 BCrypt（默认强度 10，自带随机盐），
     * 数据库只存储哈希，登录时通过 {@code matches} 校验，禁止明文或可逆加密。</p>
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    /**
     * JWT 签名密钥。
     *
     * <p>由配置项 {@code app.security.jwt.secret} 提供（默认值仅供本地开发），
     * 强制要求至少 32 字节以满足 HS256 的安全强度，否则启动失败。</p>
     *
     * @param secret 共享密钥字符串
     * @return HMAC-SHA256 密钥
     * @throws IllegalStateException 密钥不足 32 字节时抛出
     */
    @Bean
    SecretKey jwtSecret(@Value("${app.security.jwt.secret:agent-platform-local-dev-secret-change-me-32b}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("JWT secret must be at least 32 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /**
     * JWT 编码器（签发）。
     *
     * <p>基于共享密钥的对称签名，由 {@code AuthService} 在登录时使用。</p>
     *
     * @param secret 签名密钥
     * @return Nimbus JWT 编码器
     */
    @Bean
    JwtEncoder jwtEncoder(SecretKey secret) {
        return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(secret));
    }

    /**
     * JWT 解码器（校验）。
     *
     * <p>默认使用本地 HS256 密钥校验；若配置了 {@code app.security.oidc.issuer-uri}，
     * 则先按 OIDC issuer 校验（RS256 等），失败后回退到本地密钥，
     * 从而同时支持「平台账号登录」与「外部 OIDC 登录」两种 Token。
     * 本地分支额外挂载 {@link JwtBlacklistValidator} 实现注销失效。</p>
     *
     * @param secret           本地签名密钥
     * @param issuerUri        OIDC issuer 地址，可为空
     * @param blacklistValidator Redis 黑名单校验器
     * @return 可同时校验本地与 OIDC Token 的解码器
     */
    @Bean
    JwtDecoder jwtDecoder(SecretKey secret,
                          @Value("${app.security.oidc.issuer-uri:}") String issuerUri,
                          JwtBlacklistValidator blacklistValidator) {
        NimbusJwtDecoder local = NimbusJwtDecoder.withSecretKey(secret).macAlgorithm(MacAlgorithm.HS256).build();
        // 默认校验（过期时间等）+ 黑名单校验：注销后的 jti 进入 Redis 后即判定失效
        local.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), blacklistValidator));
        if (issuerUri == null || issuerUri.isBlank()) return local;
        JwtDecoder oidc = JwtDecoders.fromIssuerLocation(issuerUri);
        // 双解码策略：优先按 OIDC 校验，失败则回退本地；两种 Token 互不影响
        return token -> {
            try { return oidc.decode(token); } catch (JwtException ignored) { return local.decode(token); }
        };
    }

    /**
     * HTTP 安全过滤器链。
     *
     * <p>无状态 JWT 资源服务器模式：关闭 CSRF 与表单登录，
     * 仅放行健康检查、OpenAPI 文档、登录/配置/系统信息等白名单（含 CORS 预检 OPTIONS），
     * 其余请求一律要求认证。</p>
     *
     * @param http Spring Security 构建器
     * @return 构建完成的过滤器链
     * @throws Exception 构建失败时抛出
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            // 纯 Token 认证不需要 CSRF；跨域由 corsConfigurationSource 控制
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health/**", "/actuator/info",
                            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                            "/api/v1/auth/login", "/api/v1/auth/config", "/api/v1/system/info").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * JWT 到 Spring Security 认证对象的转换器。
     *
     * <p>把 scope 声明、自定义 {@code roles} 声明以及 OIDC 的
     * {@code realm_access.roles}（Keycloak 风格）统一转换为 {@code ROLE_*} 权限，
     * 供 {@code @PreAuthorize("hasRole(...)")} 使用。</p>
     *
     * @return JWT 认证转换器
     */
    private Converter<Jwt, AbstractAuthenticationToken> jwtConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new java.util.ArrayList<>(scopes.convert(jwt));
            // 平台自签 Token：roles 声明中的角色统一补 ROLE_ 前缀
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(
                    role.startsWith("ROLE_") ? role : "ROLE_" + role)));
            // OIDC Token 兼容：Keycloak 把角色放在 realm_access.roles
            java.util.Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof java.util.Collection<?> realmRoles) {
                realmRoles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            return authorities;
        });
        return converter;
    }

    /**
     * CORS 配置。
     *
     * <p>允许来源由 {@code app.cors.allowed-origins} 逗号分隔配置（默认本地 5173），
     * 允许携带凭证（Cookie/Authorization），并暴露 {@code Content-Disposition} 供前端读取下载文件名。</p>
     *
     * @param origins 允许的来源列表，逗号分隔
     * @return 基于 URL 匹配的 CORS 配置源
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
