package com.bookly.backendcf.catalog.presentation;

import com.bookly.backendcf.catalog.application.SpecialtyService;
import com.bookly.backendcf.catalog.presentation.dto.SpecialtyRequest;
import com.bookly.backendcf.catalog.presentation.dto.SpecialtyResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/specialties")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    public SpecialtyController(SpecialtyService specialtyService) {
        this.specialtyService = specialtyService;
    }

    @GetMapping
    public ResponseEntity<List<SpecialtyResponse>> list() {
        return ResponseEntity.ok(specialtyService.list());
    }

    @PostMapping
    public ResponseEntity<SpecialtyResponse> create(@Valid @RequestBody SpecialtyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(specialtyService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SpecialtyResponse> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SpecialtyRequest request) {
        return ResponseEntity.ok(specialtyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        specialtyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
