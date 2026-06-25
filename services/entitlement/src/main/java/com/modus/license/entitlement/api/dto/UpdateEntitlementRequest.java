package com.modus.license.entitlement.api.dto;

import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.Set;

/** Patch-style update — null fields are left unchanged. */
public record UpdateEntitlementRequest(

        Set<String> featureKeys,

        @Min(1) Integer seatLimit,

        Instant endDate
) {}
