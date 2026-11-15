package dk.dsy1.chopshop.packaging;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class PackagingService {
    private final ProductRepository products;

    public PackagingService(ProductRepository products) {
        this.products = products;
    }

    public record CreateProduct(String productType, List<String> trayRfids) {}
    public record ProductView(UUID id, String productType, List<String> trayRfids) {}

    @Transactional
    public ProductView createProduct(CreateProduct request) {
        if (request == null || !validText(request.productType()) || request.trayRfids() == null
                || request.trayRfids().isEmpty() || request.trayRfids().stream().anyMatch(rfid -> !validText(rfid))
                || new HashSet<>(request.trayRfids()).size() != request.trayRfids().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product type and distinct tray RFIDs are required");
        }
        Product saved = products.save(new Product(UUID.randomUUID(), request.productType(),
                new LinkedHashSet<>(request.trayRfids())));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public ProductView getProduct(UUID id) {
        return view(products.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")));
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank() && value.length() <= 100;
    }

    private static ProductView view(Product product) {
        return new ProductView(product.getId(), product.getProductType(), product.getTrayRfids().stream().sorted().toList());
    }
}
