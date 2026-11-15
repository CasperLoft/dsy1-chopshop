package dk.dsy1.chopshop.registration.service;

import dk.dsy1.chopshop.registration.dto.RegistrationRequest;
import dk.dsy1.chopshop.registration.persistence.AnimalRegistrationEntity;
import dk.dsy1.chopshop.registration.repository.AnimalRegistrationRepository;
import org.springframework.dao.EmptyResultDataAccessException;
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

  public AnimalRegistrationEntity registerAnimal(RegistrationRequest request)
  {
    AnimalRegistrationEntity animal = new AnimalRegistrationEntity(
        UUID.randomUUID(),
        request.earTag(),
        request.arrivalDate(),
        request.weight(),
        request.origin()
    );

    return repository.save(animal);
  }

  public AnimalRegistrationEntity getByEarTag(String earTag)
  {
    return repository.getByEarTag(earTag)
        .orElseThrow(() -> new EmptyResultDataAccessException(1));
  }

  public List<AnimalRegistrationEntity> getAllByArrivalDate(LocalDate arrivalDate)
  {
    return repository.getAllByArrivalDateOrderByEarTag(arrivalDate);
  }

  public List<AnimalRegistrationEntity> getAllByOrigin(String origin)
  {
    return repository.getAllByOriginOrderByEarTag(origin);
  }
}
