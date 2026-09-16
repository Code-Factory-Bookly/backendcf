package com.bookly.backendcf.platform.application;

/**
 * La plataforma es única, así que su aprovisionamiento es irrepetible: una vez configurada,
 * cualquier intento posterior se rechaza.
 */
public class PlatformAlreadyConfiguredException extends RuntimeException {

    public PlatformAlreadyConfiguredException() {
        super("La plataforma ya fue configurada previamente");
    }
}
