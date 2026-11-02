
package com.urlshortener.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitFilter implements Filter {
    private final RedisTemplate<String, String> redisTemplate;
    private final int requestsPerMinute;

    public RateLimitFilter(RedisTemplate<String, String> redisTemplate,
                           @Value("${app.rate-limit.requests-per-minute:60}") int rpm) {
        this.redisTemplate = redisTemplate;
        this.requestsPerMinute = rpm;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        if (req.getRequestURI().startsWith("/api/v1/shorten")) {
            String ip = req.getRemoteAddr();
            String key = "rate_limit:" + ip;
            try {
                Long count = redisTemplate.opsForValue().increment(key);
                if (count != null && count == 1) {
                    redisTemplate.expire(key, 1, TimeUnit.MINUTES);
                }
                if (count != null && count > requestsPerMinute) {
                    HttpServletResponse res = (HttpServletResponse) response;
                    res.setStatus(429);
                    res.getWriter().write("{\"error\":\"Rate limit exceeded\",\"limit\":" + requestsPerMinute + "}");
                    return;
                }
            } catch (Exception ignored) {
                // If Redis down, allow request (fail open)
            }
        }
        chain.doFilter(request, response);
    }
}
