package com.intact_id.security.filter;

import com.intact_id.db.model.Company;
import com.intact_id.db.repository.CompanyRepository;
import com.intact_id.security.authentication.CompanyAuthentication;
import com.intact_id.security.authentication.WidgetSessionContext;
import com.intact_id.service.kyc.KycSessionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KycWidgetAuthFilter extends OncePerRequestFilter {

    private static final String WIDGET_PATH_PREFIX = "/api/v1/kyc/widget/";
    static final String SESSION_TOKEN_HEADER = "X-Session-Token";

    private final KycSessionService kycSessionService;
    private final CompanyRepository companyRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(WIDGET_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String token = request.getHeader(SESSION_TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-Session-Token");
            return;
        }

        Claims claims;
        try {
            claims = kycSessionService.validateAndExtract(token);
        } catch (JwtException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired session token");
            return;
        }

        WidgetSessionContext context = kycSessionService.toContext(claims);
        Company company = companyRepository.findById(context.companyId()).orElse(null);
        if (company == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Company not found");
            return;
        }

        // Attach session metadata so the controller can read them
        request.setAttribute(WidgetSessionContext.ATTRIBUTE, context);
        request.setAttribute("widget.companyId", context.companyId());
        request.setAttribute("widget.environment", context.environment());
        request.setAttribute("widget.tier", context.tier());
        request.setAttribute("widget.externalReference", context.externalReference());

        CompanyAuthentication auth = new CompanyAuthentication(company);
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}
