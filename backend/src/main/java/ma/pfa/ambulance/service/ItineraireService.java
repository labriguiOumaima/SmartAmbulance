package ma.pfa.ambulance.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import ma.pfa.ambulance.dto.Intervention;
import ma.pfa.ambulance.dto.Point;
import org.springframework.stereotype.Service;

@Service
public class ItineraireService {

    private enum Noeud {
        DEPART(new Point(33.5731, -7.5898), 400),
        B(new Point(33.5770, -7.5950), 280),
        C(new Point(33.5800, -7.6040), 150),
        E(new Point(33.5850, -7.5950), 200),
        DESTINATION(new Point(33.5850, -7.6100), 0);

        final Point point;
        final int estimationRestante;

        Noeud(Point point, int estimationRestante) {
            this.point = point;
            this.estimationRestante = estimationRestante;
        }
    }

    private record Troncon(Noeud arrivee, int secondes) {}

    private record Candidat(Noeud noeud, int tempsParcouru) {
        int score() {
            return tempsParcouru + noeud.estimationRestante;
        }
    }

    private final Map<Noeud, List<Troncon>> graphe = Map.of(
            Noeud.DEPART, List.of(
                    new Troncon(Noeud.B, 150),
                    new Troncon(Noeud.E, 240)
            ),
            Noeud.B, List.of(new Troncon(Noeud.C, 150)),
            Noeud.C, List.of(new Troncon(Noeud.DESTINATION, 180)),
            Noeud.E, List.of(new Troncon(Noeud.DESTINATION, 360))
    );

    public Intervention calculerIntervention(boolean accident) {
        Map<Noeud, Integer> meilleursTemps = new EnumMap<>(Noeud.class);
        Map<Noeud, Noeud> precedents = new EnumMap<>(Noeud.class);
        PriorityQueue<Candidat> candidats =
                new PriorityQueue<>(Comparator.comparingInt(Candidat::score));

        meilleursTemps.put(Noeud.DEPART, 0);
        candidats.add(new Candidat(Noeud.DEPART, 0));

        while (!candidats.isEmpty()) {
            Candidat actuel = candidats.remove();

            if (actuel.tempsParcouru() > meilleursTemps.get(actuel.noeud())) {
                continue;
            }

            if (actuel.noeud() == Noeud.DESTINATION) {
                List<Point> route = new ArrayList<>();

                for (Noeud noeud = Noeud.DESTINATION; noeud != null;
                     noeud = precedents.get(noeud)) {
                    route.add(noeud.point);
                }

                Collections.reverse(route);

                return new Intervention(
                        1,
                        Noeud.DEPART.point,
                        Noeud.DESTINATION.point,
                        actuel.tempsParcouru(),
                        route
                );
            }

            for (Troncon troncon : graphe.getOrDefault(actuel.noeud(), List.of())) {
                Noeud arrivee = troncon.arrivee();

                if (accident && actuel.noeud() == Noeud.DEPART && arrivee == Noeud.B) {
                    continue;
                }

                int nouveauTemps = actuel.tempsParcouru() + troncon.secondes();

                if (nouveauTemps < meilleursTemps.getOrDefault(arrivee, Integer.MAX_VALUE)) {
                    meilleursTemps.put(arrivee, nouveauTemps);
                    precedents.put(arrivee, actuel.noeud());
                    candidats.add(new Candidat(arrivee, nouveauTemps));
                }
            }
        }

        throw new IllegalStateException("Aucun trajet disponible sur le graphe de démonstration.");
    }
}