package com.payflow.auth.controller;
import com.payflow.auth.dto.AuthDtos;
import com.payflow.auth.security.AuthenticatedUser;
import com.payflow.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    @PostMapping("/register")
    @Operation(summary = "Register a user and trigger email verification")
    public ResponseEntity<AuthDtos.UserResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }
    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for an access + refresh token pair")
    public ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                        HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, http.getHeader("User-Agent"), clientIp(http)));
    }
    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token for a new token pair")
    public ResponseEntity<AuthDtos.TokenResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest request,
                                                          HttpServletRequest http) {
        return ResponseEntity.ok(
                authService.refresh(request.refreshToken(), http.getHeader("User-Agent"), clientIp(http)));
    }
    @PostMapping("/logout")
    @Operation(summary = "Revoke the presented refresh token family")
    public ResponseEntity<AuthDtos.MessageResponse> logout(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(new AuthDtos.MessageResponse("Signed out"));
    }
    @PostMapping("/logout-all")
    @Operation(summary = "Revoke every session for the current user")
    public ResponseEntity<AuthDtos.MessageResponse> logoutAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        authService.logoutEverywhere(principal.userId());
        return ResponseEntity.ok(new AuthDtos.MessageResponse("All sessions revoked"));
    }
    @GetMapping("/me")
    public ResponseEntity<AuthDtos.UserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(authService.currentUser(principal.userId()));
    }
    @PostMapping("/verify-email/{token}")
    public ResponseEntity<AuthDtos.MessageResponse> verifyEmail(@PathVariable String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new AuthDtos.MessageResponse("Email verified"));
    }
    @PostMapping("/password-reset/initiate")
    @Operation(summary = "Always returns 202 - never reveals whether the address exists")
    public ResponseEntity<AuthDtos.MessageResponse> initiateReset(
            @Valid @RequestBody AuthDtos.PasswordResetInitiateRequest request) {
        authService.initiatePasswordReset(request.email());
        return ResponseEntity.accepted().body(new AuthDtos.MessageResponse(
                "If an account exists for that address, a reset link has been sent"));
    }
    @PostMapping("/password-reset/complete")
    public ResponseEntity<AuthDtos.MessageResponse> completeReset(
            @Valid @RequestBody AuthDtos.PasswordResetCompleteRequest request) {
        authService.completePasswordReset(request.token(), request.newPassword());
        return ResponseEntity.ok(new AuthDtos.MessageResponse("Password updated, all sessions revoked"));
    }
    @PostMapping("/roles/assign")
    @PreAuthorize("hasAuthority('team:manage') or hasAuthority('platform:admin')")
    public ResponseEntity<AuthDtos.UserResponse> assignRole(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AuthDtos.AssignRoleRequest request) {
        return ResponseEntity.ok(authService.assignRole(principal.userId(), request.userId(), request.role()));
    }
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
