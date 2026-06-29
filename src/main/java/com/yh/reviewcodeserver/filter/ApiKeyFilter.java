package com.yh.reviewcodeserver.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    @Value("${action.review.api.key}")
    private String reviewApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (!(("Bearer " + reviewApiKey).equals(authorization))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("인증되지 않은 접근입니다.");
            return;
        }

        try {
            // 요청 body에서 repository 꺼내기 전에
            // Header에서 repository를 받는 방법이 더 간단해요
            String repository = request.getHeader("X-Repository");
            MDC.put("repository", repository != null ? repository : "unknown");

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear(); // ← 반드시 정리! 안 하면 Thread 재사용 시 이전 값이 남음
        }

    }
}
