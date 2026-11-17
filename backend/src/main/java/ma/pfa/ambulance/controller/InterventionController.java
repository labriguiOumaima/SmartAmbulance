package ma.pfa.ambulance.controller;

import ma.pfa.ambulance.dto.Intervention;
import ma.pfa.ambulance.service.ItineraireService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterventionController {

    private final ItineraireService itineraireService;

    public InterventionController(ItineraireService itineraireService) {
        this.itineraireService = itineraireService;
    }

    @GetMapping("/api/interventions/1")
    public Intervention getIntervention(
            @RequestParam(name = "accident", defaultValue = "false") boolean accident) {
        return itineraireService.calculerIntervention(accident);
    }
}