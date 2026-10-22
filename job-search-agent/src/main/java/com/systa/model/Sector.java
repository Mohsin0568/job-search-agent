package com.systa.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The industry a known company is in, so the profile page can list companies by sector.
 * Read and written in JSON - the seed file and the API - by its label; stored in Mongo by its name.
 */
public enum Sector {
    BIG_TECH_AND_SOFTWARE("Big Tech & Software"),
    AI("AI"),
    FINTECH("Fintech"),
    BANKING("Banking"),
    TRADING_AND_INVESTMENT("Trading & Investment"),
    INSURANCE_AND_FINANCIAL_SERVICES("Insurance & Financial Services"),
    CONSULTING_AND_IT_SERVICES("Consulting & IT Services"),
    RETAIL_AND_ECOMMERCE("Retail & E-commerce"),
    CONSUMER_GOODS_AND_HOSPITALITY("Consumer Goods & Hospitality"),
    ENERGY_AND_UTILITIES("Energy & Utilities"),
    ENGINEERING_AEROSPACE_AND_DEFENCE("Engineering, Aerospace & Defence"),
    AUTOMOTIVE_AND_TRANSPORT("Automotive & Transport"),
    TELECOMS_AND_MEDIA("Telecoms & Media"),
    HEALTHCARE_AND_PHARMA("Healthcare & Pharma"),
    GAMING_AND_ENTERTAINMENT("Gaming & Entertainment"),
    PUBLIC_SECTOR("Public Sector");

    private final String label;

    Sector(final String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }
}
