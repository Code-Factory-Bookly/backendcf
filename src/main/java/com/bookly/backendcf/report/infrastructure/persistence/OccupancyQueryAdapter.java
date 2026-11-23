package com.bookly.backendcf.report.infrastructure.persistence;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.professional.domain.model.Professional;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.report.application.OccupancyQuery;
import com.bookly.backendcf.report.domain.model.BlockedPeriod;
import com.bookly.backendcf.report.domain.model.BookedPeriod;
import com.bookly.backendcf.schedule.infrastructure.persistence.AgendaBlockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import com.bookly.backendcf.report.domain.model.WeeklyWindow;
import com.bookly.backendcf.schedule.infrastructure.persistence.WeeklySlotRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Adaptador que conecta el puerto del reporte con los repositorios de los demás módulos. */
@Component
public class OccupancyQueryAdapter implements OccupancyQuery {

    // #220: misma definición que la disponibilidad y la creación de reservas.
    private static final List<BookingStatus> OCCUPYING_STATUSES = BookingStatus.OCCUPYING;

    private final ProfessionalRepository professionalRepository;
    private final WeeklySlotRepository weeklySlotRepository;
    private final BookingRepository bookingRepository;
    private final UserAccountRepository userAccountRepository;
    private final AgendaBlockRepository agendaBlockRepository;

    public OccupancyQueryAdapter(
            ProfessionalRepository professionalRepository,
            WeeklySlotRepository weeklySlotRepository,
            BookingRepository bookingRepository,
            UserAccountRepository userAccountRepository) {
        this(professionalRepository, weeklySlotRepository, bookingRepository, userAccountRepository, null);
    }

    @Autowired
    public OccupancyQueryAdapter(
            ProfessionalRepository professionalRepository,
            WeeklySlotRepository weeklySlotRepository,
            BookingRepository bookingRepository,
            UserAccountRepository userAccountRepository,
            AgendaBlockRepository agendaBlockRepository) {
        this.agendaBlockRepository = agendaBlockRepository;
        this.professionalRepository = professionalRepository;
        this.weeklySlotRepository = weeklySlotRepository;
        this.bookingRepository = bookingRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public List<UUID> findProfessionalIds() {
        return professionalRepository.findAll().stream()
                .map(Professional::getId)
                .toList();
    }

    @Override
    public List<WeeklyWindow> findWeeklyWindows() {
        return weeklySlotRepository.findAll().stream()
                .map(slot -> new WeeklyWindow(
                        slot.getProfessionalId(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()))
                .toList();
    }

    @Override
    public List<BookedPeriod> findOccupyingBookings(LocalDateTime from, LocalDateTime to) {
        return bookingRepository.findByStatusesInRange(OCCUPYING_STATUSES, from, to).stream()
                .map(booking -> new BookedPeriod(
                        booking.getProfessionalId(), booking.getStartTime(), booking.getEndTime()))
                .toList();
    }

    @Override
    public List<BlockedPeriod> findAgendaBlocks(LocalDateTime from, LocalDateTime to) {
        if (agendaBlockRepository == null) {
            return List.of();
        }
        return agendaBlockRepository.findAllOverlapping(from, to).stream()
                .map(block -> new BlockedPeriod(block.getProfessionalId(), block.getStartTime(), block.getEndTime()))
                .toList();
    }

    @Override
    public Map<UUID, String> professionalNames(Collection<UUID> professionalIds) {
        if (professionalIds.isEmpty()) {
            return Map.of();
        }
        return userAccountRepository.findAllById(professionalIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getFullName));
    }
}