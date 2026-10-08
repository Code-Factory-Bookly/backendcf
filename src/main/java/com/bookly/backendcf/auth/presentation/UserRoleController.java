package com.bookly.backendcf.auth.presentation;

import com.bookly.backendcf.auth.application.RoleAssignmentService;
import com.bookly.backendcf.auth.presentation.dto.RoleAssignmentRequest;
import com.bookly.backendcf.auth.presentation.dto.RoleAssignmentResponse;
import com.bookly.backendcf.auth.presentation.dto.UserSearchResult;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UserRoleController {

    private final RoleAssignmentService roleAssignmentService;

    public UserRoleController(RoleAssignmentService roleAssignmentService) {
        this.roleAssignmentService = roleAssignmentService;
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
}
