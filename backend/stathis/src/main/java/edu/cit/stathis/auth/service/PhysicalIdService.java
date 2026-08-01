package edu.cit.stathis.auth.service;

import edu.cit.stathis.auth.entity.User;
import edu.cit.stathis.auth.repository.UserRepository;
import edu.cit.stathis.common.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Service
public class PhysicalIdService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Gets the physical ID of the currently authenticated user
     * @return the physical ID of the current user
     * @throws IllegalStateException if no user is authenticated or user not found
     */
    public String getCurrentUserPhysicalId() {
        String email = resolveCurrentUserEmail();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("User not found"));
        return user.getPhysicalId();
    }

    private String resolveCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
            && authentication.getName() != null
            && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (!token.isEmpty()) {
                    String email = jwtUtil.extractUsername(token);
                    if (email != null && !email.isBlank()) {
                        return email;
                    }
                }
            }
        }

        throw new IllegalStateException("No authenticated user found");
    }

    /**
     * Gets the UUID of the currently authenticated user
     * @return the UUID ID of the current user
     * @throws IllegalStateException if no user is authenticated or user not found
     */
    public UUID getCurrentUserUUID() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("User not found"));
        return user.getUserId();
    }

    /**
     * Gets the physical ID of a user by their email
     * @param email the email of the user
     * @return the physical ID of the user
     * @throws IllegalStateException if user not found
     */
    public String getPhysicalIdByEmail(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("User not found"));
        return user.getPhysicalId();
    }

    /**
     * Gets the physical ID of a user by their UUID
     * @param userId the UUID of the user
     * @return the physical ID of the user
     * @throws IllegalStateException if user not found
     */
    public String getPhysicalIdByUserId(UUID userId) {
        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("User not found"));
        return user.getPhysicalId();
    }
} 