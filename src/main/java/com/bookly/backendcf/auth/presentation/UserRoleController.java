package com.bookly.backendcf.auth.presentation;

import com.bookly.backendcf.auth.application.PasswordResetService;
import com.bookly.backendcf.auth.application.RoleAssignmentService;
import com.bookly.backendcf.auth.presentation.dto.PasswordResetResponse;
import com.bookly.backendcf.auth.presentation.dto.RoleAssignmentRequest;
import com.bookly.backendcf.auth.presentation.dto.RoleAssignmentResponse;
import com.bookly.backendcf.auth.presentation.dto.UserSearchResult;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UserRoleController {

    private final RoleAssignmentService roleAssignmentService;
    private final PasswordResetService passwordResetService;

    public UserRoleController(RoleAssignmentService roleAssignmentService, PasswordResetService passwordResetService) {
        this.roleAssignmentService = roleAssignmentService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/buscar")
    public List<UserSearchResult> search(@RequestParam(name = "q", defaultValue = "") String query) {
        return roleAssignmentService.search(query);
    }

    @PutMapping("/{userId}/rol")
    public RoleAssignmentResponse assign(
            @PathVariable UUID userId,
            @Valid @RequestBody RoleAssignmentRequest request) {
        return roleAssignmentService.assign(userId, request);
    }

    // No hay recuperacion de clave por correo (depende de HU-10). Mientras tanto, un ADMIN puede
    // resetear a una clave temporal que debe entregar al usuario fuera de la plataforma.
    @PostMapping("/{userId}/clave/restablecer")
    public PasswordResetResponse resetPassword(@PathVariable UUID userId) {
        return new PasswordResetResponse(passwordResetService.resetToTemporaryPassword(userId));
    }
}
