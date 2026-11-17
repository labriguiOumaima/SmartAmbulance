package ma.pfa.ambulance.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import ma.pfa.ambulance.service.ReseauSumoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TraciController {

    private static final int PORT_TRACI = 8813;
    private static final String AMBULANCE_ID = "AMBULANCE";
    private static final String TRONCON_BLOQUE = "-421566989#1";
    private static final String TRONCON_SOINS = "588802766#1";
    private static final Path RESEAU = Path.of("E:/pfa_project/simulation/reseau/casa.net.xml");
    private static final byte[] SANS_PARAMETRE = new byte[0];

    private final ReseauSumoService reseauSumoService;

    public TraciController(ReseauSumoService reseauSumoService) {
        this.reseauSumoService = reseauSumoService;
    }

    @PostMapping("/api/simulation/traci/version")
    public VersionSumo lireVersion() {
        try (Socket connexion = new Socket()) {
            connecter(connexion);

            var sortie = new DataOutputStream(connexion.getOutputStream());
            var entree = new DataInputStream(connexion.getInputStream());
            var reponse = echanger(sortie, entree, 0x00, SANS_PARAMETRE);

            lireCodeCommande(reponse);
            var version = new VersionSumo(reponse.readInt(), lireTexte(reponse));
            echanger(sortie, entree, 0x7F, SANS_PARAMETRE);
            return version;
        } catch (IOException erreur) {
            throw erreurTraci(erreur);
        }
    }

    @PostMapping("/api/simulation/traci/premier-troncon")
    public TronconAmbulance lirePremierTroncon() {
        return executerScenario(false).position();
    }

    @PostMapping("/api/simulation/traci/appliquer-detour")
    public ResultatSimulation appliquerDetour() {
        return executerScenario(true);
    }

    private ResultatSimulation executerScenario(boolean appliquerDetour) {
        try (Socket connexion = new Socket()) {
            connecter(connexion);

            var sortie = new DataOutputStream(connexion.getOutputStream());
            var entree = new DataInputStream(connexion.getInputStream());
            var donneesPas = new ByteArrayOutputStream();
            var parametrePas = new DataOutputStream(donneesPas);

            for (int seconde = 121; seconde <= 300; seconde++) {
                donneesPas.reset();
                parametrePas.writeDouble(seconde);
                echanger(sortie, entree, 0x02, donneesPas.toByteArray());

                var vehicules = lireVariableVehicule(sortie, entree, 0x00, "");
                if (!lireListeTextes(vehicules).contains(AMBULANCE_ID)) {
                    continue;
                }

                var reponseTroncon = lireVariableVehicule(sortie, entree, 0x50, AMBULANCE_ID);
                String troncon = lireTexte(reponseTroncon);
                var position = new TronconAmbulance(AMBULANCE_ID, seconde, troncon);

                List<String> route = appliquerDetour
                        ? calculerEtAppliquer(sortie, entree, troncon)
                        : List.of();

                echanger(sortie, entree, 0x7F, SANS_PARAMETRE);
                return new ResultatSimulation(position, appliquerDetour, route.size(), route);
            }

            echanger(sortie, entree, 0x7F, SANS_PARAMETRE);
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "L’ambulance n’est pas apparue avant 300 s."
            );
        } catch (IOException erreur) {
            throw erreurTraci(erreur);
        }
    }

    private List<String> calculerEtAppliquer(
            DataOutputStream sortie, DataInputStream entree, String depart
    ) throws IOException {
        List<String> route = reseauSumoService
                .calculerDetour(RESEAU, depart, TRONCON_SOINS, TRONCON_BLOQUE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Aucun détour praticable."
                ));

        var donnees = new ByteArrayOutputStream();
        var parametres = new DataOutputStream(donnees);

        parametres.writeByte(0x57); // Nouveau trajet
        ecrireTexte(parametres, AMBULANCE_ID);
        parametres.writeByte(0x0E); // Liste de chaînes
        parametres.writeInt(route.size());
        for (String troncon : route) {
            ecrireTexte(parametres, troncon);
        }

        echanger(sortie, entree, 0xC4, donnees.toByteArray());

        var reponse = lireVariableVehicule(sortie, entree, 0x54, AMBULANCE_ID);
        List<String> routeEnregistree = lireListeTextes(reponse);

        if (!route.equals(routeEnregistree)) {
            throw new IOException("Le trajet enregistré par SUMO diffère du détour calculé.");
        }
        return routeEnregistree;
    }

    private static void connecter(Socket connexion) throws IOException {
        connexion.connect(new InetSocketAddress("127.0.0.1", PORT_TRACI), 3000);
        connexion.setSoTimeout(10000);
    }

    private static DataInputStream lireVariableVehicule(
            DataOutputStream sortie, DataInputStream entree, int variable, String vehicule
    ) throws IOException {
        var donnees = new ByteArrayOutputStream();
        var parametres = new DataOutputStream(donnees);

        parametres.writeByte(variable);
        ecrireTexte(parametres, vehicule);

        var reponse = echanger(sortie, entree, 0xA4, donnees.toByteArray());
        lireCodeCommande(reponse);
        reponse.readUnsignedByte(); // Variable demandée
        lireTexte(reponse);         // Identifiant du véhicule
        reponse.readUnsignedByte(); // Type de la valeur
        return reponse;
    }

    private static DataInputStream echanger(
            DataOutputStream sortie, DataInputStream entree, int commande, byte[] parametres
    ) throws IOException {
        int tailleCourte = 2 + parametres.length;
        if (tailleCourte <= 255) {
            sortie.writeInt(4 + tailleCourte);
            sortie.writeByte(tailleCourte);
        } else {
            int tailleLongue = 6 + parametres.length;
            sortie.writeInt(4 + tailleLongue);
            sortie.writeByte(0);
            sortie.writeInt(tailleLongue);
        }

        sortie.writeByte(commande);
        sortie.write(parametres);
        sortie.flush();

        int tailleDonnees = entree.readInt() - 4;
        if (tailleDonnees < 0 || tailleDonnees > 1_048_576) {
            throw new IOException("Taille de réponse TraCI invalide.");
        }

        byte[] donnees = new byte[tailleDonnees];
        entree.readFully(donnees);
        var reponse = new DataInputStream(new ByteArrayInputStream(donnees));

        lireCodeCommande(reponse);
        int statut = reponse.readUnsignedByte();
        String description = lireTexte(reponse);

        if (statut != 0) {
            throw new IOException(description);
        }
        return reponse;
    }

    private static int lireCodeCommande(DataInputStream entree) throws IOException {
        if (entree.readUnsignedByte() == 0) {
            entree.readInt();
        }
        return entree.readUnsignedByte();
    }

    private static void ecrireTexte(DataOutputStream sortie, String texte) throws IOException {
        byte[] octets = texte.getBytes(StandardCharsets.US_ASCII);
        sortie.writeInt(octets.length);
        sortie.write(octets);
    }

    private static String lireTexte(DataInputStream entree) throws IOException {
        int taille = entree.readInt();
        if (taille < 0 || taille > entree.available()) {
            throw new IOException("Taille de texte TraCI invalide.");
        }
        return new String(entree.readNBytes(taille), StandardCharsets.US_ASCII);
    }

    private static List<String> lireListeTextes(DataInputStream entree) throws IOException {
        int nombre = entree.readInt();
        var elements = new ArrayList<String>();
        for (int index = 0; index < nombre; index++) {
            elements.add(lireTexte(entree));
        }
        return elements;
    }

    private static ResponseStatusException erreurTraci(IOException erreur) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Échange TraCI avec SUMO impossible.", erreur
        );
    }

    public record VersionSumo(int versionTraci, String versionSumo) {}

    public record TronconAmbulance(String id, int tempsSimulation, String troncon) {}

    public record ResultatSimulation(
            TronconAmbulance position,
            boolean detourApplique,
            int nombreTroncons,
            List<String> troncons
    ) {}
}