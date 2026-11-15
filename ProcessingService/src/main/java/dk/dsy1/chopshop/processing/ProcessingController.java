package dk.dsy1.chopshop.processing;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProcessingController {
    private final ProcessingService service;
    public ProcessingController(ProcessingService service) { this.service = service; }

    @PostMapping("/trays") @ResponseStatus(HttpStatus.CREATED)
    public ProcessingService.TrayView createTray(@RequestBody ProcessingService.CreateTray request) { return service.createTray(request); }
    @GetMapping("/trays/{rfid}")
    public ProcessingService.TrayView getTray(@PathVariable String rfid) { return service.getTray(rfid); }
    @PostMapping("/parts") @ResponseStatus(HttpStatus.CREATED)
    public ProcessingService.PartView createPart(@RequestBody ProcessingService.CreatePart request) { return service.createPart(request); }

    // A duplicate RFID is the expected integrity violation on tray creation.
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void integrityViolation() {}
}
