# Manual gRPC testing with Kreya

**Endpoint:** `http://localhost:9090`  
**Mode:** gRPC, plaintext/TLS validation disabled  
**Database:** local PostgreSQL Docker Compose container

| Test | Method | Request | Expected result | Actual result | Status |
|---|---|---|---|---|---|
| Product traceability | `GetAnimalsForProduct` | `product_id = ...0031` | `DK-A1001`, `DK-B2002` | Matched expected response | Pass |
| Animal traceability | `GetProductsForAnimal` | `ear_tag = DK-A1001` | Products `...0031` and `...0032` | Matched expected response | Pass |
| Unknown product | `GetAnimalsForProduct` | `product_id = ...0099` | `NOT_FOUND` | `NOT_FOUND` | Pass |
| Unknown animal | `GetProductsForAnimal` | `ear_tag = DK-UNKNOWN` | `NOT_FOUND` | `NOT_FOUND` | Pass |
| Invalid product ID | `GetAnimalsForProduct` | `product_id = not-a-uuid` | `INVALID_ARGUMENT` | `INVALID_ARGUMENT` | Pass |
| Blank ear tag | `GetProductsForAnimal` | blank `ear_tag` | `INVALID_ARGUMENT` | `INVALID_ARGUMENT` | Pass |