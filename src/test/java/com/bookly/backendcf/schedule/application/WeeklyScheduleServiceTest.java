package com.bookly.backendcf.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.schedule.domain.model.WeeklySlot;
import com.bookly.backendcf.schedule.infrastructure.persistence.WeeklySlotRepository;
import com.bookly.backendcf.schedule.presentation.dto.ScheduleRequest;
import com.bookly.backendcf.schedule.presentation.dto.SlotRequest;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WeeklyScheduleServiceTest {

    private final UUID professionalId = UUID.randomUUID();
    private WeeklySlotRepository slotRepository;
    private ProfessionalRepository professionalRepository;
    private WeeklyScheduleService service;

    @BeforeEach
    void setUp() {
        slotRepository = mock(WeeklySlotRepository.class);
        professionalRepository = mock(ProfessionalRepository.class);
        when(professionalRepository.existsById(professionalId)).thenReturn(true);
        when(slotRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new WeeklyScheduleService(slotRepository, professionalRepository);
    }

    @Test
    void guardaHorarioValidoYReemplazaElAnterior() {
        var response = service.replace(professionalId, request(
                slot(DayOfWeek.MONDAY, "08:00", "12:00"),
                slot(DayOfWeek.MONDAY, "14:00", "18:00")));

        assertThat(response.slots()).hasSize(2);
        verify(slotRepository).deleteAll(anyList());
    }

    @Test
    void aceptaFranjasContiguas() {
        var response = service.replace(professionalId, request(
                slot(DayOfWeek.TUESDAY, "08:00", "12:00"),
                slot(DayOfWeek.TUESDAY, "12:00", "14:00")));

        assertThat(response.slots()).hasSize(2);
    }

    @Test
    void rechazaFranjasSuperpuestasConElDetalleDelConflicto() {
        assertThatThrownBy(() -> service.replace(professionalId, request(
                slot(DayOfWeek.WEDNESDAY, "08:00", "12:00"),
                slot(DayOfWeek.WEDNESDAY, "11:00", "15:00"))))
                .isInstanceOfSatisfying(ScheduleConflictException.class, exception ->
                        assertThat(exception.getDetails())
                                .containsEntry("WEDNESDAY 11:00-15:00", "Se superpone con WEDNESDAY 08:00-12:00"));

        verify(slotRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void rechazaHoraFinAnteriorALaDeInicio() {
        assertThatThrownBy(() -> service.replace(professionalId, request(
                slot(DayOfWeek.FRIDAY, "18:00", "09:00"))))
                .isInstanceOf(ScheduleConflictException.class);
    }

    @Test
    void profesionalInexistenteDevuelveNotFound() {
        UUID unknown = UUID.randomUUID();
        when(professionalRepository.existsById(unknown)).thenReturn(false);

        assertThatThrownBy(() -> service.find(unknown))
                .isInstanceOf(ProfessionalNotFoundException.class);
    }

    private ScheduleRequest request(SlotRequest... slots) {
        return new ScheduleRequest(List.of(slots));
    }

    private SlotRequest slot(DayOfWeek day, String start, String end) {
        return new SlotRequest(day, LocalTime.parse(start), LocalTime.parse(end));
    }
}
