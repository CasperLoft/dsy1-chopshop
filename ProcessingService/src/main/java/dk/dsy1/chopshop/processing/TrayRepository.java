package dk.dsy1.chopshop.processing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface TrayRepository extends JpaRepository<Tray, UUID> {
    Optional<Tray> findByRfid(String rfid);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tray t where t.rfid = :rfid")
    Optional<Tray> findByRfidForUpdate(@Param("rfid") String rfid);
}
