package ma.pfa.ambulance.controller;

import ma.pfa.ambulance.service.SumoService;
import ma.pfa.ambulance.service.SumoService.PositionAmbulance;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimulationController {

    private final SumoService sumoService;

    public SimulationController(SumoService sumoService) {
        this.sumoService = sumoService;
    }

    @PostMapping("/api/simulation/accident/premiere-position")
    public PositionAmbulance lirePremierePosition() {
        return sumoService.lirePremierePosition();
    }
}