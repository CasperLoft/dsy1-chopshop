package dk.dsy1.chopshop.registration.repository;

import dk.dsy1.chopshop.registration.persistence.AnimalRegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository public interface AnimalRegistrationRepository extends JpaRepository<AnimalRegistrationEntity, UUID>
{

  Optional<AnimalRegistrationEntity> getByEarTag(String earTag);
  List<AnimalRegistrationEntity> getAllByArrivalDateOrderByEarTag(LocalDate arrivalDate);
  List<AnimalRegistrationEntity> getAllByOriginOrderByEarTag(String origin);
}