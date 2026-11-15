package dk.dsy1.chopshop.packaging;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {
    @Id
    private UUID id;

    @Column(name = "product_type", nullable = false, length = 100)
    private String productType;

    @ElementCollection
    @CollectionTable(name = "product_trays", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tray_rfid", nullable = false, length = 100)
    private Set<String> trayRfids = new LinkedHashSet<>();

    protected Product() {}

    public Product(UUID id, String productType, Set<String> trayRfids) {
        this.id = id;
        this.productType = productType;
        this.trayRfids = new LinkedHashSet<>(trayRfids);
    }

    public UUID getId() { return id; }
    public String getProductType() { return productType; }
    public Set<String> getTrayRfids() { return trayRfids; }
}
