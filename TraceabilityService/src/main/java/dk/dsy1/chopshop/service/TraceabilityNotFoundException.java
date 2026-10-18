package dk.dsy1.chopshop.service;

public final class TraceabilityNotFoundException extends RuntimeException {
    public TraceabilityNotFoundException(String message) {
        super(message);
    }
}
