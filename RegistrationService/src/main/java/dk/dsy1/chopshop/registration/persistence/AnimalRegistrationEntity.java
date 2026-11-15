package dk.dsy1.chopshop.registration.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "animals") public class AnimalRegistrationEntity
{
  @Id @Column(name = "id", updatable = false)
  private UUID id;

  @Column(name = "ear_tag", nullable = false, unique = true, length = 50)
  private String earTag;

  @Column(name = "arrival_date", nullable = false)
  private LocalDate arrivalDate;

  @Column(name = "weight",nullable = false)
  private Double weight;

  @Column(name = "origin",nullable = false)
  private String origin;

  public AnimalRegistrationEntity(UUID id, String earTag, LocalDate arrivalDate, Double weight, String origin)
  {
    this.id = id;
    this.earTag = earTag;
    this.arrivalDate = arrivalDate;
    this.weight = weight;
    this.origin = origin;
  }

  public AnimalRegistrationEntity()
  {
  }

  public UUID getId()
  {
    return id;
  }

  public void setId(UUID id)
  {
    this.id = id;
  }

  public String getEarTag()
  {
    return earTag;
  }

  public void setEarTag(String earTag)
  {
    this.earTag = earTag;
  }

  public LocalDate getArrivalDate()
  {
    return arrivalDate;
  }

  public void setArrivalDate(LocalDate arrivalDate)
  {
    this.arrivalDate = arrivalDate;
  }

  public Double getWeight()
  {
    return weight;
  }

  public void setWeight(Double weight)
  {
    this.weight = weight;
  }

  public String getOrigin()
  {
    return origin;
  }

  public void setOrigin(String origin)
  {
    this.origin = origin;
  }
}
