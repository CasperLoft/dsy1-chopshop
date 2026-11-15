package dk.dsy1.chopshop.registration.controller;

import dk.dsy1.chopshop.registration.dto.RegistrationRequest;
import dk.dsy1.chopshop.registration.persistence.AnimalRegistrationEntity;
import dk.dsy1.chopshop.registration.service.RegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@org.springframework.web.bind.annotation.CrossOrigin(origins = "http://localhost:8000", methods = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
@RestController
@RequestMapping("/registrations")
public class RegistrationController
{
  private final RegistrationService registrationService;

  public RegistrationController(RegistrationService registrationService)
  {
    this.registrationService = registrationService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AnimalRegistrationEntity registerAnimal(@RequestBody RegistrationRequest request)
  {
    return registrationService.registerAnimal(request);
  }

  @GetMapping("/{earTag}")
  public AnimalRegistrationEntity getByEarTag(@PathVariable String earTag)
  {
    return registrationService.getByEarTag(earTag);
  }

  @GetMapping(params = "arrivalDate")
  public List<AnimalRegistrationEntity> getAllByArrivalDate(@RequestParam("arrivalDate") LocalDate arrivalDate)
  {
    return registrationService.getAllByArrivalDate(arrivalDate);
  }

  @GetMapping(params = "origin")
  public List<AnimalRegistrationEntity> getAllByOrigin(@RequestParam("origin") String origin)
  {
    return registrationService.getAllByOrigin(origin);
  }
}
