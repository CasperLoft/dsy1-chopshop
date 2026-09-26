package dk.dsy1.chopshop.service;

import dk.dsy1.chopshop.domain.Product;
import dk.dsy1.chopshop.repository.TraceabilityRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Coordinates the two traceability query use cases. */
public final class TraceabilityQueryService {
    private final TraceabilityRepository repository;

    public TraceabilityQueryService(TraceabilityRepository repository) {
        this.repository = repository;
    }

    public List<String> findAnimalsForProduct(UUID productId) {
        try {
            return repository.findAnimalEarTagsForProduct(productId)
                    .orElseThrow(() -> new TraceabilityNotFoundException("Product was not found"));
        } catch (SQLException exception) {
            throw new TraceabilityDataAccessException(exception);
        }
    }

    public List<Product> findProductsForAnimal(String earTag) {
        try {
            return repository.findProductsForAnimal(earTag)
                    .orElseThrow(() -> new TraceabilityNotFoundException("Animal was not found"));
        } catch (SQLException exception) {
            throw new TraceabilityDataAccessException(exception);
        }
    }
}
