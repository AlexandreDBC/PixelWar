package serveur;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import util.Pixel;

public class ServeurTCP {
    private ServerSocket serverSocket;
    protected Pixel[][] tabPixel;
    private List<ClientHandler> ListClient;
    protected Pixel pixel;
    private Timer timerReservation;

    /**
     * Constructeur du serveur
     * @param port Port sur lequel le serveur écoute
     */
    public ServeurTCP(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Serveur démarré sur le port " + port);
            ListClient = new ArrayList<>();
            tabPixel = new Pixel[20][20];
            timerReservation = new Timer();
            
            // Initialisation de la grille avec des pixels blancs
            for (int i = 0; i < 20; i++) {
                for (int j = 0; j < 20; j++) {
                    this.tabPixel[i][j] = new Pixel(i, j, "#FFFFFF");
                }
            }
            
        } catch (IOException e) {
            System.err.println("Erreur lors du lancement du serveur.");
            e.printStackTrace();
        }
    }

    /**
     * Met à jour l'état de la grille de pixels (méthode synchronisée pour éviter les conflits)
     * @param tabPixel Nouvel état de la grille
     */
    public synchronized void getCouleurs(Pixel[][] tabPixel) {
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 20; j++) {
                if (tabPixel[i][j] != null) {
                    this.tabPixel[i][j] = tabPixel[i][j];
                }
            }
        }
    }

    /**
     * Démarre le serveur et attend les connexions des clients
     */
    public void startServeur() throws IOException, ClassNotFoundException {
        System.out.println("Le serveur attend des connexions...");
        System.out.println("Adresse du serveur : " + InetAddress.getLocalHost().getHostAddress() + ":" + serverSocket.getLocalPort());

        // Boucle infinie pour accepter les connexions
        while (true) {
            Socket socket = serverSocket.accept();
            ClientHandler clientHandler = new ClientHandler(socket);
            ListClient.add(clientHandler);
            clientHandler.start();
        }
    }

    /**
     * Ferme le serveur proprement
     */
    public void close() {
        try {
            if (timerReservation != null) {
                timerReservation.cancel();
            }
            serverSocket.close();
            System.out.println("Serveur arrêté.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Classe interne pour gérer chaque client connecté dans un thread séparé
     */
    class ClientHandler extends Thread {
        // Socket de connexion avec le client
        private Socket socket;
        
        // Flux d'entrée pour recevoir des objets du client
        private ObjectInputStream input;
        
        // Flux de sortie pour envoyer des objets au client
        private ObjectOutputStream output;

        /**
         * Constructeur du gestionnaire de client
         * @param socket Socket de connexion avec le client
         */
        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                output = new ObjectOutputStream(socket.getOutputStream());
                input = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Méthode principale du thread qui gère la communication avec le client
         */
        @Override
        public void run() {
            try {
                // Envoie l'état initial de la grille au client
                output.writeObject(tabPixel);

                // Boucle de traitement des requêtes du client
                while (!socket.isClosed()) {
                    try {
                        // Reçoit un pixel modifié par le client
                        Pixel pixelRecu = (Pixel) input.readObject();
                        int x = pixelRecu.getX();
                        int y = pixelRecu.getY();
                        
                        // Vérifie si l'embargo sur le pixel est terminé
                        if (tabPixel[x][y].estEmbargoTermine()) {
                            tabPixel[x][y].liberer();
                        }
                        
                        // Section critique pour accéder au tableau de pixels
                        synchronized (tabPixel) {
                            Pixel pixelCible = tabPixel[x][y];
                            
                            // Vérifie si le pixel est disponible
                            if (pixelCible != null && !pixelCible.estReserve() || 
                                (pixelCible.estReserve() && pixelCible.estEmbargoTermine())) {
                                // Pixel disponible, on le met à jour et on le réserve
                                tabPixel[x][y] = pixelRecu;
                                tabPixel[x][y].reserver();
                                
                                // Diffuse la mise à jour à tous les clients
                                broadcastPixel(tabPixel[x][y]);
                            } else {
                                // Pixel réservé, envoie un message d'erreur au client
                                Pixel pixelErreur = new Pixel(-1, -1, "RESERVED");
                                output.writeObject(pixelErreur);
                            }
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        System.out.println("Client déconnecté : " + socket.getInetAddress());
                        break;
                    }
                }
            } catch (IOException e) {
                System.out.println("Erreur avec le client : " + e.getMessage());
            } finally {
                try {
                    // Nettoyage des ressources à la déconnexion du client
                    ListClient.remove(this);
                    if (input != null) input.close();
                    if (output != null) output.close();
                    if (socket != null && !socket.isClosed()) socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Diffuse une mise à jour de pixel à tous les clients connectés
         * @param pixel Le pixel modifié à diffuser
         */
        public synchronized void broadcastPixel(Pixel pixel) {
            for (ClientHandler client : ListClient) {
                try {
                    client.output.writeObject(pixel);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Point d'entrée principal du serveur
     */
    public static void main(String[] args) throws ClassNotFoundException {
        /*if (args.length < 1) {
            System.err.println("Usage: java ServeurTCP <port>");
            return;
        }*/

        try {
            //int port = Integer.parseInt(args[0]);
            //ServeurTCP serveur = new ServeurTCP(port);
            ServeurTCP serveur = new ServeurTCP(5001);
            serveur.startServeur();
            serveur.close();
        } catch (NumberFormatException e) {
            System.err.println("Le port doit être un nombre valide.");
        } catch (IOException e) {
            System.err.println("Erreur lors de l'exécution du serveur.");
        }
    }
}