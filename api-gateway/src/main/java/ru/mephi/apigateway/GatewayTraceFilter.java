package ru.mephi.apigateway;

import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import ru.mephi.common.TraceIds;
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayTraceFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String trace = TraceIds.safe(exchange.getRequest().getHeaders().getFirst("X-Trace-Id"));
        exchange.getAttributes().put("traceId", trace);
        exchange.getResponse().getHeaders().set("X-Trace-Id", trace);
        var forwarded = exchange.mutate().request(r -> r.headers(h -> h.set("X-Trace-Id", trace))).build();
        return chain.filter(forwarded).doFinally(signal
                -> LoggerFactory.getLogger(GatewayTraceFilter.class)
                        .info("event=gateway_request traceId={} method={} path={} status={}", trace,
                                exchange.getRequest().getMethod(), exchange.getRequest().getPath(),
                                exchange.getResponse().getStatusCode()));
    }
}
