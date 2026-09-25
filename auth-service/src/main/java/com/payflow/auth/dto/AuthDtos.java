package com.payflow.auth.dto;
import com.payflow.auth.domain.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
public final class AuthDtos {
    private AuthDtos() {
    }
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 128)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                    message = "must contain lower case, upper case and a digit")
            String password,
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Size(max = 200) String businessName
    ) {
    }
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }
    public record RefreshRequest(@NotBlank String refreshToken) {
    }
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            UserResponse user
    ) {
    }
    public record UserResponse(
            UUID id,
            String email,
            String fullName,
            UUID merchantId,
            String status,
            boolean emailVerified,
            Set<String> roles,
            Set<String> permissions,
            Instant createdAt
    ) {
    }
    public record PasswordResetInitiateRequest(@NotBlank @Email String email) {
    }
    public record PasswordResetCompleteRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 128)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                    message = "must contain lower case, upper case and a digit")
            String newPassword
    ) {
    }
    public record AssignRoleRequest(@NotNull UUID userId, @NotNull RoleName role) {
    }
    public record MessageResponse(String message) {
    }
}
