package ma.pfa.ambulance.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

@Service
public class ReseauSumoService {

    private record Candidat(String troncon, double cout, double score) {}

    public Optional<List<String>> calculerDetour(
            Path fichier, String depart, String arrivee, String tronconBloque
    ) throws IOException {
        var graphe = lireGraphe(fichier, tronconBloque);
        var inverse = new HashMap<String, List<String>>();
        double coutMinimal = Double.POSITIVE_INFINITY;

        for (var origine : graphe.entrySet()) {
            for (var transition : origine.getValue().entrySet()) {
                inverse.computeIfAbsent(transition.getKey(), id -> new ArrayList<>())
                        .add(origine.getKey());
                coutMinimal = Math.min(coutMinimal, transition.getValue());
            }
        }

        // Calcule le nombre minimal de transitions jusqu'à l'arrivée.
        var sauts = new HashMap<String, Integer>();
        var file = new ArrayDeque<String>();
        sauts.put(arrivee, 0);
        file.add(arrivee);

        while (!file.isEmpty()) {
            String actuel = file.remove();
            for (String precedent : inverse.getOrDefault(actuel, List.of())) {
                if (sauts.putIfAbsent(precedent, sauts.get(actuel) + 1) == null) {
                    file.add(precedent);
                }
            }
        }

        if (!sauts.containsKey(depart) || depart.equals(tronconBloque)
                || arrivee.equals(tronconBloque)) {
            return Optional.empty();
        }
        if (depart.equals(arrivee)) {
            return Optional.of(List.of(depart));
        }

        var candidats = new PriorityQueue<>(Comparator.comparingDouble(Candidat::score));
        var couts = new HashMap<String, Double>();
        var parents = new HashMap<String, String>();

        couts.put(depart, 0.0);
        candidats.add(new Candidat(depart, 0, sauts.get(depart) * coutMinimal));

        while (!candidats.isEmpty()) {
            Candidat actuel = candidats.remove();
            if (actuel.cout() > couts.get(actuel.troncon())) {
                continue;
            }

            if (actuel.troncon().equals(arrivee)) {
                var route = new ArrayList<String>();
                for (String id = arrivee; id != null; id = parents.get(id)) {
                    route.add(id);
                }
                Collections.reverse(route);
                return Optional.of(List.copyOf(route));
            }

            for (var transition : graphe.getOrDefault(actuel.troncon(), Map.of()).entrySet()) {
                String suivant = transition.getKey();
                double nouveauCout = actuel.cout() + transition.getValue();

                if (sauts.containsKey(suivant)
                        && nouveauCout < couts.getOrDefault(suivant, Double.POSITIVE_INFINITY)) {
                    couts.put(suivant, nouveauCout);
                    parents.put(suivant, actuel.troncon());

                    double estimation = sauts.get(suivant) * coutMinimal;
                    candidats.add(new Candidat(suivant, nouveauCout, nouveauCout + estimation));
                }
            }
        }

        return Optional.empty();
    }

    private Map<String, Map<String, Double>> lireGraphe(Path fichier, String bloque)
            throws IOException {
        var voies = new HashMap<String, Double>();
        var graphe = new HashMap<String, Map<String, Double>>();

        try {
            var fabrique = DocumentBuilderFactory.newInstance();
            fabrique.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrique.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = fabrique.newDocumentBuilder().parse(fichier.toFile());

            var elementsVoies = document.getElementsByTagName("lane");
            for (int index = 0; index < elementsVoies.getLength(); index++) {
                var voie = (Element) elementsVoies.item(index);
                if (autoriseAmbulance(voie)) {
                    double longueur = Double.parseDouble(voie.getAttribute("length"));
                    double vitesse = Double.parseDouble(voie.getAttribute("speed"));
                    if (vitesse > 0) {
                        voies.put(voie.getAttribute("id"), longueur / vitesse);
                    }
                }
            }

            var connexions = document.getElementsByTagName("connection");
            for (int index = 0; index < connexions.getLength(); index++) {
                var connexion = (Element) connexions.item(index);
                String origine = connexion.getAttribute("from");
                String destination = connexion.getAttribute("to");

                if (origine.startsWith(":") || destination.startsWith(":")
                        || origine.equals(bloque) || destination.equals(bloque)) {
                    continue;
                }

                String voieDepart = origine + "_" + connexion.getAttribute("fromLane");
                String voieArrivee = destination + "_" + connexion.getAttribute("toLane");
                String voieInterne = connexion.getAttribute("via");

                Double coutArrivee = voies.get(voieArrivee);
                Double coutInterne = voieInterne.isEmpty()
                        ? Double.valueOf(0) : voies.get(voieInterne);

                if (voies.containsKey(voieDepart) && coutArrivee != null && coutInterne != null) {
                    graphe.computeIfAbsent(origine, id -> new HashMap<>())
                            .merge(destination, coutArrivee + coutInterne, Math::min);
                }
            }

            return graphe;
        } catch (ParserConfigurationException | SAXException erreur) {
            throw new IOException("Lecture de casa.net.xml impossible.", erreur);
        }
    }

    private boolean autoriseAmbulance(Element voie) {
        var allow = List.of(voie.getAttribute("allow").strip().split("\\s+"));
        var disallow = List.of(voie.getAttribute("disallow").strip().split("\\s+"));

        return (!voie.hasAttribute("allow") || allow.contains("all") || allow.contains("emergency"))
                && !disallow.contains("all") && !disallow.contains("emergency");
    }
}