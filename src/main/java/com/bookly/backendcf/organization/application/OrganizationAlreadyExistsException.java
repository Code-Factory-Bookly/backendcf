package com.bookly.backendcf.organization.application;

public class OrganizationAlreadyExistsException extends RuntimeException {

    private final String field;
    private final String detail;

    private OrganizationAlreadyExistsException(String field, String message, String detail) {
        super(message);
        this.field = field;
        this.detail = detail;
    }

    public static OrganizationAlreadyExistsException forName(String name) {
        return new OrganizationAlreadyExistsException(
                "name",
                "La razón social ya se encuentra registrada en la plataforma",
                "Ya existe una organización registrada con el nombre \"" + name + "\"");
    }

    public static OrganizationAlreadyExistsException forTaxId(String taxId) {
        return new OrganizationAlreadyExistsException(
                "taxId",
                "Ya existe una organización registrada con ese NIT",
                "NIT: " + taxId);
    }

    public String getField() {
        return field;
    }

    public String getDetail() {
        return detail;
    }
}
