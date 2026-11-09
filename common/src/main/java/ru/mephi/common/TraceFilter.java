package ru.mephi.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
public class TraceFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String trace = TraceIds.safe(request.getHeader("X-Trace-Id"));
        try (TraceContext ignored = TraceContext.open(trace, null)) {
            response.setHeader("X-Trace-Id", trace);
            try {
                chain.doFilter(request, response);
            } finally {
                LoggerFactory.getLogger(TraceFilter.class)
                        .info("event=http_request method={} path={} status={}", request.getMethod(),
                                request.getRequestURI(), response.getStatus());
            }
        }
    }
}
