package dk.dsy1.chopshop.registration.service;

import dk.dsy1.chopshop.registration.dto.RegistrationRequest;
import dk.dsy1.chopshop.registration.domain.AnimalRegistration;
import dk.dsy1.chopshop.registration.repository.AnimalRegistrationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class RegistrationService
{
  private final AnimalRegistrationRepository repository;

  public RegistrationService(AnimalRegistrationRepository repository)
  {
    this.repository = repository;
  }

  public AnimalRegistration registerAnimal(RegistrationRequest request)
  {
    AnimalRegistration animal = new AnimalRegistration(
        UUID.randomUUID(),
        request.earTag(),
        request.arrivalDate(),
        request.weight(),
        request.origin()
    );

    repository.save(animal);

    return animal;
  }

  public AnimalRegistration getByEarTag(String earTag)
  {
    return repository.getByEarTag(earTag);
  }

  public List<AnimalRegistration> getAllByArrivalDate(LocalDate arrivalDate)
  {
    return repository.getAllByArrivalDate(arrivalDate);
  }

  public List<AnimalRegistration> getAllByOrigin(String origin)
  {
    return repository.getAllByOrigin(origin);
  }
}
