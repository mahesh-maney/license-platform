package com.modus.license.subscription.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Patch-style: null fields are left unchanged. */
public record UpdatePlanRequest(

        @Size(max = 500)
        String description,

        @Min(1)
        Integer maxSeats,

        @Min(0)
        Long priceInCents,

        Boolean active,

        Set<String> features
) {}
