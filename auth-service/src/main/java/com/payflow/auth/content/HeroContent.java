package com.payflow.auth.content;

import jakarta.validation.constraints.*;
import java.util.List;

public record HeroContent(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 100) String badge,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 140) String accent,
        @NotBlank @Size(max = 600) String description,
        @NotBlank @Size(max = 300) String supportingText,
        @NotBlank @Size(max = 60) String primaryLabel,
        @NotBlank @Size(max = 60) String secondaryLabel,
        @NotNull @Size(min = 1, max = 4) List<@NotBlank @Size(max = 80)
                @Pattern(regexp = "[^\\r\\n]+") String> trustPoints) {
}
