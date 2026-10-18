package dk.dsy1.chopshop.grpc;

import dk.dsy1.chopshop.domain.Product;
import dk.dsy1.chopshop.service.TraceabilityDataAccessException;
import dk.dsy1.chopshop.service.TraceabilityNotFoundException;
import dk.dsy1.chopshop.service.TraceabilityQueryService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.UUID;

/** Implements the generated gRPC contract and maps it to query-service calls. */
public final class TraceabilityServiceImpl extends TraceabilityServiceGrpc.TraceabilityServiceImplBase
{
  private final TraceabilityQueryService queryService;

  public TraceabilityServiceImpl(TraceabilityQueryService queryService)
  {
    this.queryService = queryService;
  }

  @Override public void getAnimalsForProduct(ProductRequest request,
      StreamObserver<AnimalsForProductResponse> responseObserver)
  {
    UUID productId;
    try
    {
      productId = parseProductId(request.getProductId());
    }
    catch (IllegalArgumentException exception)
    {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription("product_id must be a UUID").asRuntimeException());
      return;
    }

    try
    {
      List<String> earTags = queryService.findAnimalsForProduct(productId);
      AnimalsForProductResponse response = AnimalsForProductResponse.newBuilder()
          .addAllAnimals(earTags.stream().map(earTag -> AnimalResponse.newBuilder().setEarTag(earTag).build()).toList())
          .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
    catch (TraceabilityNotFoundException exception)
    {
      responseObserver.onError(Status.NOT_FOUND.withDescription(exception.getMessage()).asRuntimeException());
    }
    catch (TraceabilityDataAccessException exception)
    {
      responseObserver.onError(
          Status.INTERNAL.withDescription("Traceability data is unavailable").asRuntimeException());
    }
  }

  @Override public void getProductsForAnimal(AnimalRequest request,
      StreamObserver<ProductsForAnimalResponse> responseObserver)
  {
    String earTag = request.getEarTag().trim();
    if (earTag.isEmpty())
    {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription("ear_tag must not be blank").asRuntimeException());
      return;
    }

    try
    {
      List<Product> products = queryService.findProductsForAnimal(earTag);
      ProductsForAnimalResponse response = ProductsForAnimalResponse.newBuilder().addAllProducts(products.stream().map(
          product -> ProductResponse.newBuilder().setProductId(product.id().toString())
              .setProductType(product.productType()).build()).toList()).build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
    catch (TraceabilityNotFoundException exception)
    {
      responseObserver.onError(Status.NOT_FOUND.withDescription(exception.getMessage()).asRuntimeException());
    }
    catch (TraceabilityDataAccessException exception)
    {
      responseObserver.onError(
          Status.INTERNAL.withDescription("Traceability data is unavailable").asRuntimeException());
    }
  }

  private UUID parseProductId(String value)
  {
    if (value == null || value.isBlank())
    {
      throw new IllegalArgumentException("product_id is blank");
    }
    return UUID.fromString(value);
  }
}
