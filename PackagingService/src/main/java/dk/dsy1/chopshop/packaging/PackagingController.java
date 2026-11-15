package dk.dsy1.chopshop.packaging;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
public class PackagingController {
    private final PackagingService service;

    public PackagingController(PackagingService service) {
        this.service = service;
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public PackagingService.ProductView createProduct(@RequestBody PackagingService.CreateProduct request) {
        return service.createProduct(request);
    }

    @GetMapping("/products/{id}")
    public PackagingService.ProductView getProduct(@PathVariable UUID id) {
        return service.getProduct(id);
    }
}
