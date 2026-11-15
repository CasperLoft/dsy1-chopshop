package dk.dsy1.chopshop.processing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "trays")
public class Tray {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 100) private String rfid;
    @Column(name = "max_weight", nullable = false, columnDefinition = "numeric") private BigDecimal maxWeight;
    @Column(name = "part_type", length = 100) private String partType;

    protected Tray() {}
    public Tray(UUID id, String rfid, BigDecimal maxWeight) {
        this.id = id; this.rfid = rfid; this.maxWeight = maxWeight;
    }
    public UUID getId() { return id; }
    public String getRfid() { return rfid; }
    public BigDecimal getMaxWeight() { return maxWeight; }
    public String getPartType() { return partType; }
    public void setPartType(String partType) { this.partType = partType; }
}
