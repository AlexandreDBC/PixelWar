package controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import util.Pixel;

public class UIControllerUDP {

    @FXML
    private TextField pseudo_field;

    @FXML
    private TextField addresse_field;

    @FXML
    private TextField port_field;

    @FXML
    private Button connexion_btn;

    @FXML
    private Label etat_connexion_label;

    @FXML
    private Button deconnexion_btn;

    @FXML
    private Label timer;

    @FXML
    private Rectangle couleur_choisie_rect;

    @FXML
    private ColorPicker color_picker_btn;

    @FXML
    private GridPane grille;

    // Attributs pour le client UDP Multicast
    private MulticastSocket socket;
    private InetAddress multicastAddress;
    private int multicastPort;
    private InetSocketAddress multicastGroup;
    private NetworkInterface networkInterface;
    private DatagramPacket packet;
    private static final int PACKET_SIZE = 1024;
    
    private Pixel[][] tabPixel;
    private Rectangle[][] tabRectangle;
    private String couleur;
    private boolean isWaitingForCooldown = false;
    private Thread receptionThread;

    public void initialize() {
        // Désactive les contrôles jusqu'à la connexion
        color_picker_btn.setDisable(true);
        grille.setDisable(true);
        
        // Initialise la couleur par défaut (blanc)
        couleur = "#FFFFFF";
        
        // Initialise le tableau de rectangles
        this.tabRectangle = new Rectangle[20][20];
        this.tabPixel = new Pixel[20][20];
        
        // Dimensions des rectangles dans la grille
        double height = 28.5;
        double width = 30.5;
        
        // Création de la grille de rectangles
        for (int i = 0; i < 20; i++) {
            for (int y = 0; y < 20; y++) {
                // Capture des indices pour les utiliser dans le gestionnaire d'événements
                int indx = i;
                int indy = y;
                Rectangle rect = new Rectangle(height, width);
                tabRectangle[i][y] = rect;
                rect.setFill(Color.web(couleur));
                
                // Ajout d'un gestionnaire de clic pour chaque rectangle
                rect.setOnMouseClicked(event -> {
                    System.out.println("Rectangle cliqué : " + indx + ", " + indy);                    
                    envoyer_selection_pixel(indx, indy);
                });
                grille.add(rect, i, y);
            }
        }
        
        // Initialise la grille avec des pixels blancs
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 20; j++) {
                this.tabPixel[i][j] = new Pixel(i, j, "#FFFFFF");
            }
        }
    }

    /**
     * Gère le clic sur le bouton de connexion
     * @throws ClassNotFoundException 
     */
    @FXML
    private void connexion_btn_clic(ActionEvent event) throws ClassNotFoundException {
        try {
            // Récupère les informations de connexion
            String adresse = addresse_field.getText();
            int port = Integer.parseInt(port_field.getText());

            // Établit la connexion au groupe multicast
            multicastAddress = InetAddress.getByName(adresse);
            multicastPort = port;
            
            socket = new MulticastSocket(multicastPort);
            multicastGroup = new InetSocketAddress(multicastAddress, multicastPort);
            networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            socket.joinGroup(multicastGroup, networkInterface);
            
            System.out.println("Connecté au groupe multicast " + multicastAddress + ":" + multicastPort);
            
            // Démarre le thread de réception des messages
            demarrerThreadReceptionPixels();
            
            // Demande l'état initial de la grille
            requestInitialGrid();
            
            // Met à jour l'interface pour indiquer la connexion réussie
            etat_connexion_label.setText("Connecté au serveur");

            // Désactive les contrôles de connexion
            connexion_btn.setDisable(true);
            pseudo_field.setDisable(true);
            addresse_field.setDisable(true);
            port_field.setDisable(true);

            // Active les contrôles de dessin et de déconnexion
            deconnexion_btn.setDisable(false);
            color_picker_btn.setDisable(false);
            grille.setDisable(false);

        } catch (IOException e) {
            // Affiche un message d'erreur en cas d'échec de connexion
            etat_connexion_label.setText("Erreur de connexion");
            e.printStackTrace();
        }
    }

    /**
     * Demande l'état initial du serveur et met à jour la grille locale
     */
    private void requestInitialGrid() throws IOException, ClassNotFoundException {
        String request = "DISCOVER_SERVER";
        byte[] requestData = request.getBytes();

        DatagramPacket requestPacket = new DatagramPacket(
                requestData,
                requestData.length,
                multicastAddress,
                multicastPort);

        socket.send(requestPacket);
        System.out.println("Requête de connexion envoyée");

        // Réception de la réponse contenant l'adresse du serveur
        byte[] buffer = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
        socket.receive(responsePacket);

        String serverInfo = new String(responsePacket.getData(), 0, responsePacket.getLength()).trim();
        System.out.println("Adresse du serveur reçue : " + serverInfo);

        // Réception de la grille complète
        buffer = new byte[8192]; // Augmenter la taille du buffer pour la grille
        DatagramPacket gridPacket = new DatagramPacket(buffer, buffer.length);
        socket.receive(gridPacket);

        // Désérialiser la grille reçue
        ByteArrayInputStream byteStream = new ByteArrayInputStream(gridPacket.getData());
        ObjectInputStream objectStream = new ObjectInputStream(byteStream);
        Pixel[][] receivedGrid = (Pixel[][]) objectStream.readObject();

        // Vérifier si la grille est valide
        if (receivedGrid != null) {
            System.out.println("Grille reçue avec succès ! Mise à jour en cours...");

            // Mise à jour de la grille locale
            for (int i = 0; i < receivedGrid.length; i++) {
                for (int j = 0; j < receivedGrid[i].length; j++) {
                    if (receivedGrid[i][j] != null) {
                        this.tabPixel[i][j] = receivedGrid[i][j]; // Mise à jour du pixel
                        this.couleur = tabPixel[i][j].getCouleur();
                        tabRectangle[i][j].setFill(Color.web(couleur));;
                        
                    }
                }
            }

            System.out.println("Grille locale mise à jour !");
        } else {
            System.err.println("Erreur : Grille reçue invalide !");
        }
    }


    /**
     * Gère le clic sur le bouton de déconnexion
     */
    @FXML
    private void deconnexion_btn_clic(ActionEvent event) {
        gererDeconnexion();
    }

    /**
     * Met à jour la couleur sélectionnée lorsque l'utilisateur utilise le sélecteur de couleur
     */
    @FXML
    private void color_picker_btn_select(ActionEvent event) {
        this.couleur_choisie_rect.setFill(this.color_picker_btn.getValue());
        this.couleur = color_picker_btn.getValue().toString();
    }

    /**
     * Envoie une demande de modification d'un pixel au serveur
     */
    public void envoyer_selection_pixel(int x, int y) {
        try {
            // Format de la demande: PIXEL_REQUEST:x,y,couleur
            String request = "PIXEL_REQUEST:" + x + "," + y + "," + this.couleur;
            byte[] requestData = request.getBytes();
            
            DatagramPacket packet = new DatagramPacket(
                    requestData,
                    requestData.length,
                    multicastAddress,
                    multicastPort);
            
            socket.send(packet);
            System.out.println("Demande de modification de pixel envoyée: " + request);
            
            // Désactive temporairement la grille jusqu'à la réponse du serveur
            grille.setDisable(true);
            isWaitingForCooldown = true;
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Démarre un thread pour recevoir les mises à jour de pixels du serveur
     */
    public void demarrerThreadReceptionPixels() {
        receptionThread = new Thread(() -> {
            try {
                byte[] buffer = new byte[PACKET_SIZE];
                packet = new DatagramPacket(buffer, buffer.length);
                
                System.out.println("En attente de messages...");
                
                while (socket != null && !socket.isClosed()) {
                    // Réinitialiser le buffer
                    buffer = new byte[PACKET_SIZE];
                    packet.setData(buffer);
                    
                    // Attendre un message
                    socket.receive(packet);
                    
                    // Traiter le message reçu
                    processReceivedPacket(packet);
                }
            } catch (IOException e) {
                if (socket != null && !socket.isClosed()) {
                    System.err.println("Erreur lors de la réception des messages:");
                    e.printStackTrace();
                }
            }
        });

        // Configure le thread comme daemon pour qu'il se termine avec l'application
        receptionThread.setDaemon(true);
        receptionThread.start();
    }
    
    /**
     * Traite un paquet reçu
     */
    private void processReceivedPacket(DatagramPacket packet) {
        try {
            byte[] data = packet.getData();
            
            try {
                ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
                ObjectInputStream objectStream = new ObjectInputStream(byteStream);
                Object obj = objectStream.readObject();
                
                if (obj instanceof Pixel[][]) {
                    // Mise à jour de la grille complète
                    Pixel[][] newGrid = (Pixel[][]) obj;
                    this.tabPixel = newGrid;
                    
                    // Mettre à jour l'interface graphique
                    Platform.runLater(() -> {
                        for (int i = 0; i < 20; i++) {
                            for (int j = 0; j < 20; j++) {
                                if (newGrid[i][j] != null) {
                                    tabRectangle[i][j].setFill(Color.web(newGrid[i][j].getCouleur()));
                                }
                            }
                        }
                    });
                    return;
                }
            } catch (Exception e) {
            }
            
            // Traiter comme un message texte
            String message = new String(data, 0, packet.getLength()).trim();
            System.out.println("Message reçu: " + message);
            
            if (message.contains(":")) {
                String[] parts = message.split(":", 2);
                String type = parts[0];
                String content = parts[1];
                
                switch (type) {
                    case "PIXEL_UPDATED":
                    case "PIXEL_BROADCAST":
                        // Format: x,y,couleur
                        String[] pixelInfo = content.split(",");
                        int x = Integer.parseInt(pixelInfo[0]);
                        int y = Integer.parseInt(pixelInfo[1]);
                        String couleur = pixelInfo[2];
                        
                        // Mettre à jour le modèle
                        if (x >= 0 && x < 20 && y >= 0 && y < 20) {
                            tabPixel[x][y] = new Pixel(x, y, couleur);
                            
                            // Mettre à jour l'interface graphique
                            Platform.runLater(() -> {
                                tabRectangle[x][y].setFill(Color.web(couleur));
                            });
                            
                            // Si c'était un pixel que nous avons sélectionné, démarrer le cooldown
                            if (isWaitingForCooldown && type.equals("PIXEL_UPDATED")) {
                                Platform.runLater(() -> {
                                    demarrerCooldown();
                                    isWaitingForCooldown = false;
                                });
                            }
                        }
                        break;
                        
                    default:
                        // Ne rien faire pour les autres types de messages
                        break;
                }
            } else if (message.equals("PIXEL_RESERVED")) {
                // Le pixel demandé est réservé
                Platform.runLater(() -> {
                    afficherAlertPixelReserve();
                    grille.setDisable(false);
                    isWaitingForCooldown = false;
                });
            } else if (message.equals("INVALID_POSITION")) {
                System.err.println("Position de pixel invalide");
                Platform.runLater(() -> {
                    grille.setDisable(false);
                    isWaitingForCooldown = false;
                });
            }
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement du paquet reçu:");
            e.printStackTrace();
        }
    }
    
    /**
     * Affiche une alerte lorsqu'un pixel est réservé par un autre utilisateur
     */
    private void afficherAlertPixelReserve() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Pixel indisponible");
        alert.setHeaderText("Ce pixel est réservé");
        alert.setContentText("Ce pixel est actuellement réservé par un autre utilisateur. Veuillez attendre ou sélectionner un autre pixel.");
        alert.show();
    }
    
    /**
     * Démarre le compte à rebours avant de pouvoir placer un nouveau pixel
     */
    private void demarrerCooldown() {
        final int duree = 30; // Temps en secondes pour le cooldown client
        Timeline timeline = new Timeline();

        // Crée une animation qui met à jour l'affichage du timer chaque seconde
        for (int s = duree; s > -1; s--) {
            final int secondes = s;
            timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(duree - s),
                e -> this.timer.setText(String.format("00:%02d", secondes))));
        }

        // Réactive la grille une fois le temps écoulé
        timeline.setOnFinished(e -> grille.setDisable(false));
        timeline.play();
    }
    
    /**
     * Gère la déconnexion du serveur
     */
    private void gererDeconnexion() {
        try {
            if (socket != null) {
                socket.leaveGroup(multicastGroup, networkInterface);
                socket.close();
            }
            
            // Met à jour l'interface dans le thread JavaFX
            Platform.runLater(() -> {
                etat_connexion_label.setText("Déconnecté");

                // Réactive les contrôles de connexion
                pseudo_field.setDisable(false);
                addresse_field.setDisable(false);
                port_field.setDisable(false);
                connexion_btn.setDisable(false);
                
                // Désactive les contrôles de dessin
                deconnexion_btn.setDisable(true);
                color_picker_btn.setDisable(true);
                grille.setDisable(true);

                // Réinitialise la grille en blanc
                for (int i = 0; i < 20; i++) {
                    for (int j = 0; j < 20; j++) {
                        tabRectangle[i][j].setFill(Color.web("#FFFFFF"));
                    }
                }
            });
            
            System.out.println("Client UDP déconnecté.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}