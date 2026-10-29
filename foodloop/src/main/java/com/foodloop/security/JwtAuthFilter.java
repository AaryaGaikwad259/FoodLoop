package com.foodloop.security;

import com.foodloop.entity.User;
import com.foodloop.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Runs once per request: reads "Authorization: Bearer <token>", validates it and, if valid,
 * puts the logged-in user into the SecurityContext. The role is read from the DATABASE (not the token),
 * so a role change takes effect immediately.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override

    protected boolean shouldNotFilter(HttpServletRequest request) {
    return "GET".equalsIgnoreCase(request.getMethod())
            && "/api/recommendations".equals(request.getServletPath());
}
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7).trim();
            try {
                String email = jwtService.extractEmail(token);
                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid/expired token: leave the request unauthenticated. Protected URLs will answer 401.
                logger.debug("Rejected JWT: " + ex.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
