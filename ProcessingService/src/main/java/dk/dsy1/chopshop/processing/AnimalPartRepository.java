package dk.dsy1.chopshop.processing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AnimalPartRepository extends JpaRepository<AnimalPart, UUID> {
    List<AnimalPart> findByTrayIdOrderById(UUID trayId);
}
