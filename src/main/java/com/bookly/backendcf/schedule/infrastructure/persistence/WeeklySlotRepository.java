package com.bookly.backendcf.schedule.infrastructure.persistence;

import com.bookly.backendcf.schedule.domain.model.WeeklySlot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklySlotRepository extends JpaRepository<WeeklySlot, UUID> {

    List<WeeklySlot> findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(UUID professionalId);
}
