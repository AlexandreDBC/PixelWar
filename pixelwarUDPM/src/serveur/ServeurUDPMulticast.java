package serveur;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import util.Pixel;

public class ServeurUDPMulticast {
    private MulticastSocket multicastSocket; // Socket multicast
    private InetSocketAddress multicastGroup; // Groupe multicast
    private NetworkInterface networkInterface; // Interface réseau
    protected Pixel[][] tabPixel; // Grille de pixels
    protected Pixel pixel; // Pixel en cours de modification
    private Timer timerReservation; // Timer pour les réservations
    private ExecutorService executorService; // Thread pool

    // Constantes
    private static final int PACKET_SIZE = 1024;
    private static final String DEFAULT_MULTICAST_ADDRESS = "239.0.0.1";
    private static final int DEFAULT_MULTICAST_PORT = 7777;

    /**
     * Constructeur du serveur
     * @param multicastAddress Adresse multicast
     * @param multicastPort Port multicast
     */
    public ServeurUDPMulticast(String multicastAddress, int multicastPort) {
        try {
            // Initialisation du socket multicast
            multicastSocket = new MulticastSocket(multicastPort);
            InetAddress groupAddr = InetAddress.getByName(multicastAddress);
            multicastGroup = new InetSocketAddress(groupAddr, multicastPort);
            networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            multicastSocket.joinGroup(multicastGroup, networkInterface);
            System.out.println("Serveur multicast démarré sur " + multicastAddress + ":" + multicastPort);
            
            // Initialisation des autres attributs
            tabPixel = new Pixel[20][20];
            timerReservation = new Timer();
            executorService = Executors.newFixedThreadPool(2);
            
            // Initialisation de la grille avec des pixels blancs
            for (int i = 0; i < 20; i++) {
                for (int j = 0; j < 20; j++) {
                    this.tabPixel[i][j] = new Pixel(i, j, "#FFFFFF");
                }
            }
            
            // Démarrage du service multicast
            startMulticastService();
            
        } catch (IOException e) {
            System.err.println("Erreur lors du lancement du serveur:");
            e.printStackTrace();
        }
    }

    /**
     * Démarre le service multicast qui reçoit les demandes et envoie les réponses
     */
    private void startMulticastService() {
        executorService.submit(() -> {
            try {
                byte[] buffer = new byte[PACKET_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                
                System.out.println("Service multicast démarré, en attente de requêtes...");
                
                while (!multicastSocket.isClosed()) {
                    // Réinitialiser le buffer
                    buffer = new byte[PACKET_SIZE];
                    packet.setData(buffer);
                    
                    // Attendre une requête
                    multicastSocket.receive(packet);
                    
                    String request = new String(packet.getData(), 0, packet.getLength()).trim();
                    
                    // Traiter la requête de 1ère connexion
                    if (request.equals("DISCOVER_SERVER")) {
                        // Envoyer l'adresse du serveur
                        String serverInfo = InetAddress.getLocalHost().getHostAddress();
                        byte[] responseData = serverInfo.getBytes();
                        
                        DatagramPacket response = new DatagramPacket(
                                responseData,
                                responseData.length,
                                packet.getAddress(),
                                packet.getPort());
                        
                        multicastSocket.send(response);
                        // Envoyer la grille de pixels après la réponse DISCOVER_SERVER
                        sendGridToClient(packet.getAddress(), packet.getPort());
                        
                    } else if (request.startsWith("PIXEL_REQUEST:")) {
                        // Format attendu: PIXEL_REQUEST:x,y,couleur
                        String[] parts = request.substring("PIXEL_REQUEST:".length()).split(",");
                        int x = Integer.parseInt(parts[0]);
                        int y = Integer.parseInt(parts[1]);
                        String couleur = parts[2];
                        
                        // Traiter la demande de modification de pixel
                        processPixelRequest(x, y, couleur, packet.getAddress(), packet.getPort());
                    } else if (request.equals("GET_GRID")) {
                        // Envoyer toute la grille de pixels
                        sendGridToClient(packet.getAddress(), packet.getPort());
                    }
                }
            } catch (IOException e) {
                if (!multicastSocket.isClosed()) {
                    System.err.println("Erreur dans le service multicast:");
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Traite une demande de modification de pixel
     */
    private void processPixelRequest(int x, int y, String couleur, InetAddress clientAddress, int clientPort) {
        try {
            // Vérifier si la position est valide
            if (x < 0 || x >= 20 || y < 0 || y >= 20) {
                sendResponse("INVALID_POSITION", clientAddress, clientPort);
                return;
            }
            
            // Créer un nouveau pixel avec la couleur demandée
            Pixel nouveauPixel = new Pixel(x, y, couleur);
            
            // Vérifier si le pixel n'est pas réservé
            synchronized (tabPixel) {
                Pixel pixelCible = tabPixel[x][y];
                
                if (pixelCible.estEmbargoTermine()) {
                    pixelCible.liberer();
                }
                
                if (!pixelCible.estReserve() || (pixelCible.estReserve() && pixelCible.estEmbargoTermine())) {
                    // Pixel disponible, on le met à jour
                    tabPixel[x][y] = nouveauPixel;
                    tabPixel[x][y].reserver();
                    
                    // Envoyer une confirmation au client
                    sendResponse("PIXEL_UPDATED:" + x + "," + y + "," + couleur, clientAddress, clientPort);
                    
                    // Diffuser la mise à jour à tous (broadcast)
                    broadcastPixelUpdate(nouveauPixel);
                } else {
                    // Pixel réservé, envoyer un message d'erreur
                    sendResponse("PIXEL_RESERVED", clientAddress, clientPort);
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement de la demande de pixel:");
            e.printStackTrace();
        }
    }

    /**
     * Envoie une réponse à un client spécifique
     */
    private void sendResponse(String response, InetAddress clientAddress, int clientPort) throws IOException {
        byte[] responseData = response.getBytes();
        DatagramPacket packet = new DatagramPacket(
                responseData,
                responseData.length,
                clientAddress,
                clientPort);
        
        multicastSocket.send(packet);
    }

    /**
     * Diffuse une mise à jour de pixel à tous les clients
     */
    private void broadcastPixelUpdate(Pixel pixel) throws IOException {
        String update = "PIXEL_BROADCAST:" + pixel.getX() + "," + pixel.getY() + "," + pixel.getCouleur();
        byte[] updateData = update.getBytes();
        
        DatagramPacket packet = new DatagramPacket(
                updateData,
                updateData.length,
                multicastGroup.getAddress(),
                multicastGroup.getPort());
        
        multicastSocket.send(packet);
    }

    /**
     * Envoie toute la grille à un client spécifique
     */
    private void sendGridToClient(InetAddress clientAddress, int clientPort) {
        try {
            // Sérialiser la grille en bytes
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
            objectStream.writeObject(tabPixel);
            objectStream.flush();
            byte[] gridData = byteStream.toByteArray();
            
            // Envoyer la grille
            DatagramPacket packet = new DatagramPacket(
                    gridData,
                    gridData.length,
                    clientAddress,
                    clientPort);
            
            multicastSocket.send(packet);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoi de la grille:");
            e.printStackTrace();
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
            
            if (multicastSocket != null) {
                multicastSocket.leaveGroup(multicastGroup, networkInterface);
                multicastSocket.close();
            }
            
            if (executorService != null) {
                executorService.shutdown();
            }
            
            System.out.println("Serveur arrêté.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Point d'entrée principal du serveur
     */
    public static void main(String[] args) {
        try {
            String multicastAddress = DEFAULT_MULTICAST_ADDRESS;
            int multicastPort = DEFAULT_MULTICAST_PORT;
            
            // Traiter les arguments en ligne de commande si présents
            if (args.length >= 1) {
                multicastAddress = args[0];
            }
            if (args.length >= 2) {
                multicastPort = Integer.parseInt(args[1]);
            }
            
            ServeurUDPMulticast serveur = new ServeurUDPMulticast(multicastAddress, multicastPort);
            
            // Le serveur est maintenant en attente de demandes multicast
            System.out.println("Serveur démarré. Appuyez sur Entrée pour quitter.");
            System.in.read(); // Attendre que l'utilisateur appuie sur Entrée
            
            serveur.close();
        } catch (Exception e) {
            System.err.println("Erreur du serveur:");
            e.printStackTrace();
        }
    }
}