package com.bookly.backendcf.availability.infrastructure.persistence;

import com.bookly.backendcf.availability.application.AvailabilityQuery;
import com.bookly.backendcf.availability.domain.model.BusyPeriod;
import com.bookly.backendcf.availability.domain.model.WorkingWindow;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import com.bookly.backendcf.catalog.domain.model.ServiceStatus;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.schedule.infrastructure.persistence.WeeklySlotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adaptador que conecta el puerto de disponibilidad con los repositorios de los demás módulos. */
@Component
public class AvailabilityQueryAdapter implements AvailabilityQuery {

    private final ProfessionalRepository professionalRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final WeeklySlotRepository weeklySlotRepository;
    private final BookingRepository bookingRepository;

    public AvailabilityQueryAdapter(
            ProfessionalRepository professionalRepository,
            ServiceOfferingRepository serviceRepository,
            WeeklySlotRepository weeklySlotRepository,
            BookingRepository bookingRepository) {
        this.professionalRepository = professionalRepository;
        this.serviceRepository = serviceRepository;
        this.weeklySlotRepository = weeklySlotRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public boolean professionalExists(UUID professionalId) {
        return professionalRepository.existsById(professionalId);
    }

    @Override
    public Optional<Integer> findActiveServiceDuration(UUID serviceId) {
        return serviceRepository.findById(serviceId)
                .filter(service -> service.getStatus() == ServiceStatus.ACTIVO)
                .map(ServiceOffering::getDurationMinutes);
    }

    @Override
    public List<WorkingWindow> findWorkingWindows(UUID professionalId) {
        return weeklySlotRepository.findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(professionalId).stream()
                .map(slot -> new WorkingWindow(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()))
                .toList();
    }

    @Override
    public List<BusyPeriod> findBusyPeriods(UUID professionalId, LocalDateTime from, LocalDateTime to) {
        return bookingRepository.findOverlappingBookings(professionalId, from, to).stream()
                .map(booking -> new BusyPeriod(booking.getStartTime(), booking.getEndTime()))
                .toList();
    }
}