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
  private final dk.dsy1.chopshop.registration.messaging.Outbox outbox;

  public RegistrationService(AnimalRegistrationRepository repository, dk.dsy1.chopshop.registration.messaging.Outbox outbox)
  {
    this.repository = repository;
    this.outbox = outbox;
  }

  @org.springframework.transaction.annotation.Transactional
  public AnimalRegistrationEntity registerAnimal(RegistrationRequest request)
  {
    AnimalRegistrationEntity animal = new AnimalRegistrationEntity(
        UUID.randomUUID(),
        request.earTag(),
        request.arrivalDate(),
        request.weight(),
        request.origin()
    );

    AnimalRegistrationEntity saved = repository.save(animal);
    outbox.add("animal.registered", java.util.Map.of("animalId", saved.getId().toString(), "earTag", saved.getEarTag()));
    return saved;
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
