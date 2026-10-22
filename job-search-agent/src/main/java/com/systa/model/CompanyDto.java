package com.systa.model;

/** A known company as the profile page lists it. The sector is null only if it was seeded without one. */
public record CompanyDto(String name, Sector sector) {

    public static CompanyDto from(final Company company) {
        return new CompanyDto(company.name(), company.sector());
    }
}
