package com.jobra.apigateway.filter;

import com.jobra.apigateway.config.GatewayRateLimitProperties;
import com.jobra.apigateway.limiter.ClientAddressResolver;
import com.jobra.apigateway.limiter.RateLimitPathClassifier;
import com.jobra.apigateway.limiter.ReactiveDistributedRateLimiter;
import com.jobra.apigateway.web.GatewayProblemJsonWriter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Distributed Redis rate limiting per client IP (and configurable per-path windows).
 */
@Component
public class RateLimitGatewayFilter implements GlobalFilter, Ordered {

    private final GatewayRateLimitProperties rateLimitProperties;
    private final ClientAddressResolver clientAddressResolver;
    private final RateLimitPathClassifier pathClassifier;
    private final ReactiveDistributedRateLimiter rateLimiter;
    private final GatewayProblemJsonWriter problemJsonWriter;

    public RateLimitGatewayFilter(GatewayRateLimitProperties rateLimitProperties,
                                  ClientAddressResolver clientAddressResolver,
                                  RateLimitPathClassifier pathClassifier,
                                  ReactiveDistributedRateLimiter rateLimiter,
                                  GatewayProblemJsonWriter problemJsonWriter) {
        this.rateLimitProperties = rateLimitProperties;
        this.clientAddressResolver = clientAddressResolver;
        this.pathClassifier = pathClassifier;
        this.rateLimiter = rateLimiter;
        this.problemJsonWriter = problemJsonWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!rateLimitProperties.isEnabled()) {
            return chain.filter(exchange);
        }
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String clientKey = clientAddressResolver.resolveClientKey(exchange.getRequest());
        String scope = pathClassifier.scopeForPath(path);
        int max = pathClassifier.maxForPath(path);

        return rateLimiter.tryAcquire(scope, clientKey, max)
                .flatMap(outcome -> {
                    if (outcome.permitted()) {
                        return chain.filter(exchange);
                    }
                    exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(outcome.retryAfterSeconds()));
                    return problemJsonWriter.write(
                            exchange,
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Too Many Requests",
                            "Rate limit exceeded for this client. Retry after the indicated interval.",
                            "RATE_LIMIT_EXCEEDED"
                    );
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
