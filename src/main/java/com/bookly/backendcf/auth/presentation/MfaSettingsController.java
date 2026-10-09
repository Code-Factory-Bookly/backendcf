package com.bookly.backendcf.auth.presentation;

import com.bookly.backendcf.auth.application.MfaService;
import com.bookly.backendcf.auth.presentation.dto.MfaDisableRequest;
import com.bookly.backendcf.auth.presentation.dto.MfaRecoveryCodesResponse;
import com.bookly.backendcf.auth.presentation.dto.MfaRecoveryStatusResponse;
import com.bookly.backendcf.auth.presentation.dto.MfaRegenerateRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A diferencia de /api/v1/auth/mfa/** (que valida un mfaToken de login, sin sesion), estas rutas
 * las usa un ADMIN ya autenticado para administrar su propio segundo factor. Por eso viven bajo un
 * prefijo distinto: si compartieran el mismo prefijo que el permitAll de login, quedarian publicas
 * por accidente.
 */
@RestController
@RequestMapping("/api/v1/mfa")
public class MfaSettingsController {

    private final MfaService mfaService;

    public MfaSettingsController(MfaService mfaService) {
        this.mfaService = mfaService;
    }

    @GetMapping("/recovery-codes/estado")
    public MfaRecoveryStatusResponse recoveryStatus(Authentication authentication) {
        return new MfaRecoveryStatusResponse(mfaService.recoveryCodesRemaining(userId(authentication)));
    }

    @PostMapping("/recovery-codes/regenerar")
    public MfaRecoveryCodesResponse regenerate(
            Authentication authentication, @Valid @RequestBody MfaRegenerateRequest request) {
        return new MfaRecoveryCodesResponse(
                mfaService.regenerateRecoveryCodes(userId(authentication), request.code()));
    }

    @PostMapping("/desactivar")
    public ResponseEntity<Void> disable(
            Authentication authentication, @Valid @RequestBody MfaDisableRequest request) {
        mfaService.disable(userId(authentication), request.password(), request.code());
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
