package controller;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

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

public class UIController {

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

    private Socket socket;
    
    private ObjectOutputStream outputStream;
    
    private ObjectInputStream inputStream;
    
    private Pixel[][] tabPixel;
    
    private Rectangle[][] tabRectangle;

    private String couleur;
    
    private boolean isWaitingForCooldown = false;


    public void initialize() {
        // Désactive les contrôles jusqu'à la connexion
        color_picker_btn.setDisable(true);
        grille.setDisable(true);
        
        // Initialise la couleur par défaut (blanc)
        couleur = "#FFFFFF";
        
        // Initialise le tableau de rectangles
        this.tabRectangle = new Rectangle[20][20];
        
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
    }

    /**
     * Gère le clic sur le bouton de connexion
     */
    @FXML
    private void connexion_btn_clic(ActionEvent event) throws IOException, ClassNotFoundException {
        // Récupère les informations de connexion
        String adresse = addresse_field.getText();
        int port = Integer.parseInt(port_field.getText());

        try {
            // Établit la connexion au serveur
            socket = new Socket(adresse, port);

            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());

            // Reçoit l'état initial de la grille de pixels
            this.tabPixel = (Pixel[][]) inputStream.readObject();

            // Met à jour l'affichage de la grille avec l'état reçu
            for (int i = 0; i < 20; i++) {
                for (int y = 0; y < 20; y++)  
                    if (this.tabPixel[i][y] != null) { 
                        this.tabRectangle[i][y].setFill(Color.web(this.tabPixel[i][y].getCouleur()));
                    }
            }
            
            // Démarre le thread de réception des mises à jour des pixels
            demarrerThreadReceptionPixels();
            
            // Met à jour l'interface pour indiquer la connexion réussie
            etat_connexion_label.setText("Connecté au serveur");

            // Désactive les contrôles de connexion
            connexion_btn.setDisable(true);
            pseudo_field.setDisable(true);
            addresse_field.setDisable(true);
            port_field.setDisable(true);
            connexion_btn.setDisable(true);

            // Active les contrôles de dessin et de déconnexion
            deconnexion_btn.setDisable(false);
            color_picker_btn.setDisable(false);
            grille.setDisable(false);

        } catch (IOException e) {
            // Affiche un message d'erreur en cas d'échec de connexion
            etat_connexion_label.setText("Erreur de connexion");
        }
    }

    /**
     * Gère le clic sur le bouton de déconnexion
     */
    @FXML
    private void deconnexion_btn_clic(ActionEvent event) throws IOException {
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
            // Crée un objet Pixel avec les coordonnées et la couleur choisie
            Pixel pixel = new Pixel(x, y, this.couleur);
            this.outputStream.writeObject(pixel);
            
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
        Thread receptionThread = new Thread(() -> {
            try {
                while (!socket.isClosed()) {
                    // Attend et lit les objets Pixel envoyés par le serveur
                    Pixel pixelRecu = (Pixel) inputStream.readObject();

                    // Exécute les mises à jour d'interface dans le thread JavaFX
                    Platform.runLater(() -> {
                        if (pixelRecu != null) {
                            if (pixelRecu.getX() == -1 && pixelRecu.getY() == -1 && "RESERVED".equals(pixelRecu.getCouleur())) {
                                // Le pixel demandé est réservé par un autre utilisateur
                                System.out.println("test1");
                                afficherAlertPixelReserve();
                                grille.setDisable(false);
                                isWaitingForCooldown = false;
                            } else {
                                // Mise à jour du pixel dans la grille
                                System.out.println("test2");
                                int x = pixelRecu.getX();
                                int y = pixelRecu.getY();
                                String couleur = pixelRecu.getCouleur();
                                
                                tabRectangle[x][y].setFill(Color.web(couleur));
                                
                                // Si ce pixel est celui que nous avons sélectionné, démarre le temps de rechargement
                                if (isWaitingForCooldown) {
                                    demarrerCooldown();
                                    isWaitingForCooldown = false;
                                }
                            }
                        }
                    });
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Connexion au serveur perdue.");
                gererDeconnexion();
            }
        });

        // Configure le thread comme daemon pour qu'il se termine avec l'application
        receptionThread.setDaemon(true);
        receptionThread.start();
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

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}