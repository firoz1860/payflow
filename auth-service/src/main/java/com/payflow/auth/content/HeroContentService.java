package com.payflow.auth.content;

import com.payflow.auth.domain.SiteHero;
import com.payflow.auth.repository.SiteHeroRepository;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.event.Topics;
import com.payflow.common.outbox.OutboxRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

@Service
public class HeroContentService {
    private final SiteHeroRepository repository;
    private final OutboxRecorder outbox;
    public HeroContentService(SiteHeroRepository repository, OutboxRecorder outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }
    @Transactional(readOnly = true)
    public HeroContent current() { return entity().content(); }
    @Transactional
    public HeroContent update(HeroContent content, UUID actor) {
        SiteHero hero = entity();
        if (hero.content().version().longValue() != content.version().longValue()) {
            throw PayFlowException.conflict(ErrorCode.CONFLICT,
                    "Content changed since you opened it. Reload before saving.");
        }
        hero.apply(content);
        repository.saveAndFlush(hero);
        outbox.record("SiteContent", "hero", Topics.AUDIT_EVENT, 1,
                Map.of("actorType", "USER", "actorId", actor.toString(),
                        "action", "HERO_CONTENT_UPDATED", "entityType", "SiteContent", "entityId", "hero"));
        return hero.content();
    }
    private SiteHero entity() {
        return repository.findById(1).orElseThrow(() -> PayFlowException.notFound("Hero content not initialized"));
    }
}
