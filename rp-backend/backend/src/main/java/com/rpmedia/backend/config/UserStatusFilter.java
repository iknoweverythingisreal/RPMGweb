package com.rpmedia.backend.config;

import com.rpmedia.backend.repository.UserRepository;
import com.rpmedia.backend.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class UserStatusFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public UserStatusFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. Skip public or safe paths
        // /api/auth/** : login/register
        // /api/users/me : check own status
        if (path.startsWith("/api/auth") || path.startsWith("/api/users/me")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Check Authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {

            String email = null;
            if (auth.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) auth.getPrincipal();
                // In AuthController, we used user.getEmail() as subject
                email = jwt.getSubject();
            } else if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
                email = ((org.springframework.security.core.userdetails.UserDetails) auth.getPrincipal()).getUsername();
            } else if (auth.getPrincipal() instanceof String) {
                email = (String) auth.getPrincipal();
            }

            if (email != null) {
                // Check DB for latest status
                var userOpt = userRepository.findByEmail(email);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter()
                                .write("{\"error\": \"Account not active. Current status: " + user.getStatus() + "\"}");
                        return;
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
