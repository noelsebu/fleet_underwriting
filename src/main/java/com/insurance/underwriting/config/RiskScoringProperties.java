package com.insurance.underwriting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "risk")
public class RiskScoringProperties {

    private Sigmoid sigmoid = new Sigmoid();
    private Thresholds thresholds = new Thresholds();
    private Weights weights = new Weights();

    public Sigmoid getSigmoid() { return sigmoid; }
    public Thresholds getThresholds() { return thresholds; }
    public Weights getWeights() { return weights; }

    public static class Sigmoid {
        private double steepness = 6.0;
        private double midpoint  = 0.55;

        public double getSteepness() { return steepness; }
        public void setSteepness(double steepness) { this.steepness = steepness; }
        public double getMidpoint() { return midpoint; }
        public void setMidpoint(double midpoint) { this.midpoint = midpoint; }
    }

    public static class Thresholds {
        private double low    = 0.35;
        private double medium = 0.65;

        public double getLow() { return low; }
        public void setLow(double low) { this.low = low; }
        public double getMedium() { return medium; }
        public void setMedium(double medium) { this.medium = medium; }
    }

    public static class Weights {
        // Business stability
        private double businessNewHigh      = 0.12;
        private double businessNewModerate  = 0.06;
        private double creditScoreLow       = 0.08;
        private double creditScoreFair      = 0.04;
        private double warZone              = 0.20;

        // Fleet
        private double fleetAgeHigh         = 0.15;
        private double fleetAgeModerate     = 0.07;
        private double vehicleTruck         = 0.10;
        private double vehicleVan           = 0.07;
        private double vehicleSuv           = 0.04;

        // Driver pool
        private double driverAgeHigh        = 0.15;
        private double driverAgeModerate    = 0.08;
        private double largeDriverPool      = 0.05;

        // Claims
        private double claimsFreqHigh       = 0.18;
        private double claimsFreqModerate   = 0.09;
        private double claimsFreqLow        = 0.03;
        private double claimSeverityHigh    = 0.08;
        private double claimSeverityMedium  = 0.04;
        private double atFaultHigh          = 0.04;

        public double getBusinessNewHigh()      { return businessNewHigh; }
        public void setBusinessNewHigh(double v) { this.businessNewHigh = v; }
        public double getBusinessNewModerate()   { return businessNewModerate; }
        public void setBusinessNewModerate(double v) { this.businessNewModerate = v; }
        public double getCreditScoreLow()        { return creditScoreLow; }
        public void setCreditScoreLow(double v)  { this.creditScoreLow = v; }
        public double getCreditScoreFair()       { return creditScoreFair; }
        public void setCreditScoreFair(double v) { this.creditScoreFair = v; }
        public double getWarZone()               { return warZone; }
        public void setWarZone(double v)         { this.warZone = v; }

        public double getFleetAgeHigh()          { return fleetAgeHigh; }
        public void setFleetAgeHigh(double v)    { this.fleetAgeHigh = v; }
        public double getFleetAgeModerate()      { return fleetAgeModerate; }
        public void setFleetAgeModerate(double v){ this.fleetAgeModerate = v; }
        public double getVehicleTruck()          { return vehicleTruck; }
        public void setVehicleTruck(double v)    { this.vehicleTruck = v; }
        public double getVehicleVan()            { return vehicleVan; }
        public void setVehicleVan(double v)      { this.vehicleVan = v; }
        public double getVehicleSuv()            { return vehicleSuv; }
        public void setVehicleSuv(double v)      { this.vehicleSuv = v; }

        public double getDriverAgeHigh()         { return driverAgeHigh; }
        public void setDriverAgeHigh(double v)   { this.driverAgeHigh = v; }
        public double getDriverAgeModerate()     { return driverAgeModerate; }
        public void setDriverAgeModerate(double v){ this.driverAgeModerate = v; }
        public double getLargeDriverPool()       { return largeDriverPool; }
        public void setLargeDriverPool(double v) { this.largeDriverPool = v; }

        public double getClaimsFreqHigh()        { return claimsFreqHigh; }
        public void setClaimsFreqHigh(double v)  { this.claimsFreqHigh = v; }
        public double getClaimsFreqModerate()    { return claimsFreqModerate; }
        public void setClaimsFreqModerate(double v){ this.claimsFreqModerate = v; }
        public double getClaimsFreqLow()         { return claimsFreqLow; }
        public void setClaimsFreqLow(double v)   { this.claimsFreqLow = v; }
        public double getClaimSeverityHigh()     { return claimSeverityHigh; }
        public void setClaimSeverityHigh(double v){ this.claimSeverityHigh = v; }
        public double getClaimSeverityMedium()   { return claimSeverityMedium; }
        public void setClaimSeverityMedium(double v){ this.claimSeverityMedium = v; }
        public double getAtFaultHigh()           { return atFaultHigh; }
        public void setAtFaultHigh(double v)     { this.atFaultHigh = v; }
    }
}
