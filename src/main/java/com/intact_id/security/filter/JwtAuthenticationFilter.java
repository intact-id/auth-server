package com.intact_id.security.filter;

import com.intact_id.db.model.user.User;
import com.intact_id.db.repository.user.UserRepository;
import com.intact_id.service.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // Check if Authorization header exists and starts with Bearer
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract JWT token
        jwt = authHeader.substring(7);

        try {
            // Extract username from token
            username = jwtService.extractUsername(jwt);

            // If username exists and no authentication is set
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Load user from database
                User user = userRepository.findByUsername(username).orElse(null);

                // Validate token
                if (user != null && jwtService.validateToken(jwt, username)) {
                    // Create authorities from user roles and permissions
                    List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                            .flatMap(role -> {
                                // Add role as authority
                                var roleAuthority = new SimpleGrantedAuthority("ROLE_" + role.getName());
                                // Add permissions as authorities
                                var permissionAuthorities = role.getPermissions().stream()
                                        .map(permission -> new SimpleGrantedAuthority(permission.getName()))
                                        .collect(Collectors.toList());
                                permissionAuthorities.add(roleAuthority);
                                return permissionAuthorities.stream();
                            })
                            .collect(Collectors.toList());

                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            authorities);

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set authentication in security context
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("JWT authentication successful for user: {}", username);
                }
            }
        } catch (Exception e) {
            log.error("JWT authentication failed", e);
        }

        filterChain.doFilter(request, response);
    }
}
