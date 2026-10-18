package dk.dsy1.chopshop.registration.dto;

import java.time.LocalDate;

public record RegistrationRequest(String earTag, LocalDate arrivalDate, Double weight, String origin)
{
}
