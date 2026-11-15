package dk.dsy1.chopshop.processing;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProcessingService {
    private final dk.dsy1.chopshop.processing.messaging.Outbox outbox;
    private final TrayRepository trays;
    private final AnimalPartRepository parts;
    public ProcessingService(TrayRepository trays, AnimalPartRepository parts, dk.dsy1.chopshop.processing.messaging.Outbox outbox) { this.trays = trays; this.parts = parts; this.outbox = outbox; }

    public record CreateTray(String rfid, BigDecimal maxWeight) {}
    public record CreatePart(UUID animalId, String partType, BigDecimal weight, String trayRfid) {}
    public record PartView(UUID id, UUID animalId, String partType, BigDecimal weight, String trayRfid) {}
    public record TrayView(UUID id, String rfid, BigDecimal maxWeight, String partType, BigDecimal currentWeight, List<PartView> parts) {}

    @Transactional
    public TrayView createTray(CreateTray request) {
        requireText(request.rfid(), 100); requirePositive(request.maxWeight());
        Tray tray = trays.saveAndFlush(new Tray(UUID.randomUUID(), request.rfid(), request.maxWeight()));
        return view(tray, List.of());
    }

    @Transactional(readOnly = true)
    public TrayView getTray(String rfid) {
        Tray tray = trays.findByRfid(rfid).orElseThrow(ProcessingService::missingTray);
        return view(tray, parts.findByTrayIdOrderById(tray.getId()));
    }

    @Transactional
    public PartView createPart(CreatePart request) {
        if (request.animalId() == null) throw badRequest("animalId is required");
        requireText(request.partType(), 100); requireText(request.trayRfid(), 100); requirePositive(request.weight());
        Tray tray = trays.findByRfidForUpdate(request.trayRfid()).orElseThrow(ProcessingService::missingTray);
        List<AnimalPart> existing = parts.findByTrayIdOrderById(tray.getId());
        if (tray.getPartType() != null && !tray.getPartType().equals(request.partType()))
            throw conflict("Tray contains a different part type");
        if (weight(existing).add(request.weight()).compareTo(tray.getMaxWeight()) > 0)
            throw conflict("Tray capacity exceeded");
        if (tray.getPartType() == null) tray.setPartType(request.partType());
        AnimalPart part = parts.save(new AnimalPart(UUID.randomUUID(), request.animalId(), request.partType(), request.weight(), tray));
        outbox.add("part.registered", java.util.Map.of("partId", part.getId().toString(), "animalId", request.animalId().toString(), "trayRfid", tray.getRfid()));
        return partView(part, tray.getRfid());
    }

    private static TrayView view(Tray tray, List<AnimalPart> parts) {
        return new TrayView(tray.getId(), tray.getRfid(), tray.getMaxWeight(), tray.getPartType(), weight(parts),
                parts.stream().map(p -> partView(p, tray.getRfid())).toList());
    }
    private static PartView partView(AnimalPart part, String rfid) {
        return new PartView(part.getId(), part.getAnimalId(), part.getPartType(), part.getWeight(), rfid);
    }
    private static BigDecimal weight(List<AnimalPart> parts) {
        return parts.stream().map(AnimalPart::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static void requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) throw badRequest("Required text is blank or too long");
    }
    private static void requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw badRequest("Weight must be positive");
    }
    private static ResponseStatusException missingTray() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Tray not found"); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
