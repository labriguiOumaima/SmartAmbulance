package ma.pfa.ambulance.service;

import java.nio.file.Path;
import ma.pfa.ambulance.dto.Point;
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.Vehicle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SumoService {

    private static final String AMBULANCE_ID = "AMBULANCE";
    private static final Path SCENARIO = Path.of(
            "E:/pfa_project/simulation/scenarios/04_accident_simple/04_accident_simple.sumocfg"
    );

    public synchronized PositionAmbulance lirePremierePosition() {
        Simulation.preloadLibraries();
        Simulation.start(new StringVector(new String[]{
                "sumo", "-c", SCENARIO.toString(), "--no-step-log", "true"
        }));

        try {
            while (Simulation.getTime() < 300 && Simulation.getMinExpectedNumber() > 0) {
                Simulation.step();

                if (Vehicle.getIDList().contains(AMBULANCE_ID)) {
                    var position = Vehicle.getPosition(AMBULANCE_ID);
                    var gps = Simulation.convertGeo(position.getX(), position.getY(), false);

                    return new PositionAmbulance(
                            AMBULANCE_ID,
                            Simulation.getTime(),
                            new Point(gps.getY(), gps.getX())
                    );
                }
            }

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "L’ambulance n’est pas apparue avant 300 s de simulation."
            );
        } finally {
            Simulation.close();
        }
    }

    public record PositionAmbulance(String id, double tempsSimulation, Point position) {}
}