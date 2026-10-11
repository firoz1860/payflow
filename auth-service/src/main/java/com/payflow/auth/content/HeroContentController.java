package com.payflow.auth.content;

import com.payflow.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class HeroContentController {
    private final HeroContentService service;
    public HeroContentController(HeroContentService service) { this.service = service; }
    @GetMapping("/site-content/hero")
    public ResponseEntity<HeroContent> publicContent() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.current());
    }
    @GetMapping("/admin/site-content/hero")
    @PreAuthorize("hasAuthority('platform:admin')")
    public HeroContent adminContent() { return service.current(); }
    @PutMapping("/admin/site-content/hero")
    @PreAuthorize("hasAuthority('platform:admin')")
    public HeroContent update(@Valid @RequestBody HeroContent content,
                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.update(content, actor.userId());
    }
}
