package dk.dsy1.chopshop.registration.domain;

import java.time.LocalDate;
import java.util.UUID;

public record AnimalRegistration(UUID id, String earTag, LocalDate arrivalDate, Double weight, String origin)
{
}
