package ma.pfa.ambulance.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import ma.pfa.ambulance.service.ReseauSumoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DetourController {

    private final ReseauSumoService reseauSumoService;

    public DetourController(ReseauSumoService reseauSumoService) {
        this.reseauSumoService = reseauSumoService;
    }

    @GetMapping("/api/simulation/accident/detour")
    public Detour lireDetour() {
        var fichier = Path.of("E:/pfa_project/simulation/reseau/casa.net.xml");
        String depart = "-578843461#2";
        String arrivee = "588802766#1";
        String bloque = "-421566989#1";

        try {
            List<String> route = reseauSumoService
                    .calculerDetour(fichier, depart, arrivee, bloque)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "Aucun détour praticable."
                    ));

            return new Detour(bloque, route.size(), route);
        } catch (IOException erreur) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Impossible de lire le réseau SUMO.", erreur
            );
        }
    }

    public record Detour(String tronconBloque, int nombreTroncons, List<String> troncons) {}
}