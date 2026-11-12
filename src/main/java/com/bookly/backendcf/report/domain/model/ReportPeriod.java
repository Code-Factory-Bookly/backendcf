package com.bookly.backendcf.report.domain.model;

/** Periodos soportados por el reporte de ocupación; ambos terminan hoy. */
public enum ReportPeriod {
    SEMANA(7),
    MES(30);

    private final int days;

    ReportPeriod(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }
}