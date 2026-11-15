package dk.dsy1.chopshop.processing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "animal_parts")
public class AnimalPart {
    @Id private UUID id;
    @Column(name = "animal_id", nullable = false) private UUID animalId;
    @Column(name = "part_type", nullable = false, length = 100) private String partType;
    @Column(nullable = false, columnDefinition = "numeric") private BigDecimal weight;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tray_id", nullable = false) private Tray tray;

    protected AnimalPart() {}
    public AnimalPart(UUID id, UUID animalId, String partType, BigDecimal weight, Tray tray) {
        this.id = id; this.animalId = animalId; this.partType = partType; this.weight = weight; this.tray = tray;
    }
    public UUID getId() { return id; }
    public UUID getAnimalId() { return animalId; }
    public String getPartType() { return partType; }
    public BigDecimal getWeight() { return weight; }
}
