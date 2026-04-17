package com.jobra.authservice.config;

import com.jobra.authservice.security.JwtFilter;
import com.jobra.authservice.service.AuditService;
import com.jobra.authservice.service.CustomOAuth2UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    /** Max length for {@code reason} query param on OAuth failure redirect (avoid huge URLs). */
    private static final int OAUTH_FAILURE_HINT_MAX_LEN = 500;

    private final OAuth2SuccessHandler OAuth2SuccessHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final JwtFilter jwtFilter;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final AuditService auditService;
    /** Same base as {@link OAuth2SuccessHandler} — OAuth failure must redirect to the SPA, not {@code /login} on the API host. */
    private final String frontendBaseUrl;

    public SecurityConfig(OAuth2SuccessHandler OAuth2SuccessHandler,
                          CustomOAuth2UserService customOAuth2UserService,
                          JwtFilter jwtFilter,
                          ClientRegistrationRepository clientRegistrationRepository,
                          AuditService auditService,
                          @Value("${app.frontend-url:http://localhost:3000}") String frontendBaseUrl) {
        this.OAuth2SuccessHandler = OAuth2SuccessHandler;
        this.customOAuth2UserService = customOAuth2UserService;
        this.jwtFilter = jwtFilter;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.auditService = auditService;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/$", "");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        http
                // CORS headers are added only at the API gateway (browser → :8080). CORS is disabled here so
                // Access-Control-Allow-Origin is not duplicated. If an OPTIONS preflight ever hits this service
                // directly, permit it; the gateway should answer preflight before proxying.
                .cors(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Ant matchers: Spring Security 6 MVC matchers can miss nested paths like /api/auth/internal/role
                        .requestMatchers(new AntPathRequestMatcher("/api/auth/internal/**")).permitAll()
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/verify-otp",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/resend-otp",
                                "/api/auth/password-reset/request",
                                "/api/auth/password-reset/confirm",
                                "/api/auth/session",
                                "/api/auth/users/by-email",
                                "/api/auth/mfa/enroll/start",
                                "/api/auth/mfa/enroll/confirm",
                                "/api/auth/oauth/mfa/verify",
                                "/oauth2/**",
                                "/login/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeAccessDenied)
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(authorizationRequestResolver())
                        )
                        .failureHandler((request, response, exception) -> {
                            log.warn("OAuth2 login failed: {}", oauthFailureSummary(exception), exception);
                            String hint = oauthFailureHintForRedirect(exception);
                            String url = frontendBaseUrl + "/login?error=oauth";
                            if (hint != null && !hint.isBlank()) {
                                url += "&reason=" + URLEncoder.encode(hint, StandardCharsets.UTF_8);
                            }
                            response.sendRedirect(url);
                        })
                        .userInfoEndpoint(user -> user
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(OAuth2SuccessHandler)
                );

        return http.build();
    }

    private void writeUnauthorized(jakarta.servlet.http.HttpServletRequest request,
                                   jakarta.servlet.http.HttpServletResponse response,
                                   org.springframework.security.core.AuthenticationException authException)
            throws java.io.IOException {
        auditService.logAccessDenied("anonymous", request.getRequestURI(), request.getMethod(), "UNAUTHORIZED");
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Unauthorized\",\"status\":401}");
    }

    private void writeAccessDenied(HttpServletRequest request,
                                   jakarta.servlet.http.HttpServletResponse response,
                                   org.springframework.security.access.AccessDeniedException accessDeniedException)
            throws java.io.IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String principal = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
        auditService.logAccessDenied(principal, request.getRequestURI(), request.getMethod(), "ACCESS_DENIED");
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Access denied\",\"status\":403}");
    }

    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient() {
        return new RestClientAuthorizationCodeTokenResponseClient();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    private OAuth2AuthorizationRequestResolver authorizationRequestResolver() {
        DefaultOAuth2AuthorizationRequestResolver defaultResolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository,
                        OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI
                );

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return withSelectAccountPrompt(defaultResolver.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return withSelectAccountPrompt(defaultResolver.resolve(request, clientRegistrationId));
            }
        };
    }

    private OAuth2AuthorizationRequest withSelectAccountPrompt(OAuth2AuthorizationRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> additionalParameters = new LinkedHashMap<>(request.getAdditionalParameters());
        additionalParameters.put("prompt", "select_account");
        return OAuth2AuthorizationRequest.from(request)
                .additionalParameters(additionalParameters)
                .build();
    }

    /** One-line summary for logs (may include nested OAuth2 error). */
    private static String oauthFailureSummary(Throwable ex) {
        if (ex instanceof OAuth2AuthenticationException oae) {
            OAuth2Error err = oae.getError();
            if (err != null) {
                String d = err.getDescription();
                if (d != null && !d.isBlank()) {
                    return err.getErrorCode() + ": " + d;
                }
                return err.getErrorCode();
            }
        }
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    /**
     * Short text safe to put in a query string so the SPA can show why OAuth failed.
     * Strips control characters and caps length — not for secrets.
     */
    private static String oauthFailureHintForRedirect(Throwable ex) {
        String raw;
        if (ex instanceof OAuth2AuthenticationException oae) {
            OAuth2Error err = oae.getError();
            if (err != null) {
                String code = err.getErrorCode() != null ? err.getErrorCode() : "error";
                String desc = err.getDescription();
                raw = (desc != null && !desc.isBlank()) ? code + ": " + desc : code;
            } else {
                raw = ex.getMessage() != null ? ex.getMessage() : "OAuth2AuthenticationException";
            }
        } else {
            raw = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        }
        raw = raw.replaceAll("[\\r\\n\\t]", " ").trim();
        if (raw.length() > OAUTH_FAILURE_HINT_MAX_LEN) {
            raw = raw.substring(0, OAUTH_FAILURE_HINT_MAX_LEN - 1) + "…";
        }
        return raw;
    }

}
