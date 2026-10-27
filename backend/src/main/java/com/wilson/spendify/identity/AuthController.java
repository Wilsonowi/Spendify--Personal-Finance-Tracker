package com.wilson.spendify.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final IdentityService identityService;
    private final AuthenticationManager authenticationManager;

    public AuthController(IdentityService identityService, AuthenticationManager authenticationManager) {
        this.identityService = identityService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        AppUser user = identityService.register(request.email(), request.displayName(), request.password());
        authenticate(request.email(), request.password(), servletRequest);
        return UserView.from(user);
    }

    @PostMapping("/login")
    public UserView login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        Authentication auth = authenticate(request.email(), request.password(), servletRequest);
        SpendifyPrincipal principal = (SpendifyPrincipal) auth.getPrincipal();
        return new UserView(principal.id(), principal.email(), principal.displayName());
    }

    @GetMapping("/me")
    public UserView me(Authentication authentication) {
        SpendifyPrincipal principal = (SpendifyPrincipal) authentication.getPrincipal();
        return new UserView(principal.id(), principal.email(), principal.displayName());
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest request) {
        request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("loggedOut", true);
    }

    private Authentication authenticate(String email, String password, HttpServletRequest request) {
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return auth;
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 2, max = 80) String displayName,
            @NotBlank @Size(min = 12, max = 128) String password) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record UserView(UUID id, String email, String displayName) {
        static UserView from(AppUser user) {
            return new UserView(user.getId(), user.getEmail(), user.getDisplayName());
        }
    }
}
