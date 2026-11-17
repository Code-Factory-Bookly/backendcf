package com.bookly.backendcf.schedule.infrastructure.persistence;

import com.bookly.backendcf.schedule.application.AgendaBlocks;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAgendaBlocks implements AgendaBlocks {

    private final AgendaBlockRepository repository;

    public JpaAgendaBlocks(AgendaBlockRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockedPeriod> findOverlapping(UUID professionalId, LocalDateTime from, LocalDateTime to) {
        return repository.findOverlapping(professionalId, from, to).stream()
                .map(block -> new BlockedPeriod(block.getStartTime(), block.getEndTime()))
                .toList();
    }
}
