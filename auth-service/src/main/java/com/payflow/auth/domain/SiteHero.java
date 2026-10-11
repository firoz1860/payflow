package com.payflow.auth.domain;
import com.payflow.auth.content.HeroContent;

import jakarta.persistence.*;
import java.util.Arrays;

@Entity
@Table(name = "site_hero")
public class SiteHero {
    @Id private Integer id;
    @Version private long version;
    @Column(nullable = false, length = 100) private String badge;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, length = 140) private String accent;
    @Column(nullable = false, length = 600) private String description;
    @Column(name = "supporting_text", nullable = false, length = 300) private String supportingText;
    @Column(name = "primary_label", nullable = false, length = 60) private String primaryLabel;
    @Column(name = "secondary_label", nullable = false, length = 60) private String secondaryLabel;
    @Column(name = "trust_points", nullable = false, columnDefinition = "text") private String trustPoints;

    protected SiteHero() {}
    public HeroContent content() {
        return new HeroContent(version, badge, title, accent, description, supportingText,
                primaryLabel, secondaryLabel, Arrays.asList(trustPoints.split("\\n")));
    }
    public void apply(HeroContent content) {
        badge = content.badge().trim();
        title = content.title().trim();
        accent = content.accent().trim();
        description = content.description().trim();
        supportingText = content.supportingText().trim();
        primaryLabel = content.primaryLabel().trim();
        secondaryLabel = content.secondaryLabel().trim();
        trustPoints = String.join("\n", content.trustPoints().stream().map(String::trim).toList());
    }
}
