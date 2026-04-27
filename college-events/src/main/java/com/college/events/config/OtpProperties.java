package com.college.events.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds "otp.*" properties to a typed POJO
 * so the IDE recognises them and stops showing warnings.
 */
@Configuration
@ConfigurationProperties(prefix = "otp")
public class OtpProperties {

    private Expiry expiry = new Expiry();
    private Max max = new Max();
    private RateLimit rateLimit = new RateLimit();

    public Expiry getExpiry() { return expiry; }
    public void setExpiry(Expiry expiry) { this.expiry = expiry; }

    public Max getMax() { return max; }
    public void setMax(Max max) { this.max = max; }

    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }

    public static class Expiry {
        private int minutes;
        public int getMinutes() { return minutes; }
        public void setMinutes(int minutes) { this.minutes = minutes; }
    }

    public static class Max {
        private int attempts;
        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }
    }

    public static class RateLimit {
        private int maxRequests;
        private int windowMinutes;
        public int getMaxRequests() { return maxRequests; }
        public void setMaxRequests(int maxRequests) { this.maxRequests = maxRequests; }
        public int getWindowMinutes() { return windowMinutes; }
        public void setWindowMinutes(int windowMinutes) { this.windowMinutes = windowMinutes; }
    }
}
