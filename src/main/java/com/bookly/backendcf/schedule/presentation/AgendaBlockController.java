package com.bookly.backendcf.schedule.presentation;

import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.schedule.application.AgendaBlockService;
import com.bookly.backendcf.schedule.application.ProfessionalNotFoundException;
import com.bookly.backendcf.schedule.presentation.dto.AgendaBlockRequest;
import com.bookly.backendcf.schedule.presentation.dto.AgendaBlockResponse;
import com.bookly.backendcf.shared.security.OwnershipGuard;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HU-05: bloqueos temporales de agenda. Solo el propio profesional o un ADMIN (como el horario, HU-04). */
@RestController
@RequestMapping("/api/v1/horarios/{professionalId}/bloqueos")
public class AgendaBlockController {

    private final AgendaBlockService blockService;
    private final OwnershipGuard ownershipGuard;
    private final ProfessionalRepository professionalRepository;

    public AgendaBlockController(
            AgendaBlockService blockService,
            OwnershipGuard ownershipGuard,
            ProfessionalRepository professionalRepository) {
        this.blockService = blockService;
        this.ownershipGuard = ownershipGuard;
        this.professionalRepository = professionalRepository;
    }

    @GetMapping
    public List<AgendaBlockResponse> list(@PathVariable UUID professionalId, Authentication authentication) {
        authorize(professionalId, authentication);
        return blockService.findUpcoming(professionalId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgendaBlockResponse create(
            @PathVariable UUID professionalId,
            @Valid @RequestBody AgendaBlockRequest request,
            Authentication authentication) {
        authorize(professionalId, authentication);
        return blockService.create(professionalId, request);
    }

    @DeleteMapping("/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID professionalId, @PathVariable UUID blockId, Authentication authentication) {
        authorize(professionalId, authentication);
        blockService.delete(professionalId, blockId);
    }

    private void authorize(UUID professionalId, Authentication authentication) {
        if (!professionalRepository.existsById(professionalId)) {
            throw new ProfessionalNotFoundException(professionalId);
        }
        ownershipGuard.check(professionalId, authentication);
    }
}
