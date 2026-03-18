package com.insurance.underwriting.model;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.math.BigDecimal;

@Data
public class RiskScoreRequest {

    @Valid
    @NotNull
    private BusinessInfo businessInfo;

    @Valid
    @NotNull
    private FleetDetails fleetDetails;

    @Valid
    @NotNull
    private DriverPool driverPool;

    @Valid
    @NotNull
    private ClaimsHistory claimsHistory;

    @Data
    public static class BusinessInfo {
        @NotBlank(message = "Company name is required")
        private String companyName;

        @Min(value = 0, message = "Years in operation cannot be negative")
        private int yearsInOperation;

        @Min(value = 300) @Max(value = 850)
        @NotNull(message = "Credit score is required")
        private Integer creditScore;

        private boolean warZone;
    }

    @Data
    public static class FleetDetails {
        @Min(value = 1, message = "Fleet must have at least 1 vehicle")
        private int totalVehicles;

        @DecimalMin(value = "0.0", message = "Average vehicle age cannot be negative")
        @NotNull(message = "Average vehicle age is required")
        private Double averageVehicleAgeYears;

        @NotNull(message = "Primary vehicle type is required")
        private VehicleType primaryVehicleType;
    }

    @Data
    public static class DriverPool {
        @Min(value = 1, message = "Must have at least 1 driver")
        private int totalDrivers;

        @DecimalMin(value = "18.0", message = "Average driver age must be at least 18")
        @NotNull(message = "Average driver age is required")
        private Double averageDriverAge;
    }

    @Data
    public static class ClaimsHistory {
        @Min(value = 0, message = "Claim count cannot be negative")
        private int claimsLast3Years;

        @DecimalMin(value = "0.0", message = "Total claim amount cannot be negative")
        @NotNull(message = "Total claim amount is required")
        private BigDecimal totalClaimAmount;

        @Min(value = 0)
        private int atFaultCount;
    }
}
