import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import javax.imageio.ImageIO;
import javax.swing.*;

public class Serveur {
    public static void main(String[] args) {
        int port = 0; // Port pour les captures d'écran
        int controlPort = 0; // Port pour les commandes clavier et souris
        String ligneFichier = "";

        try (BufferedReader lireFichier = new BufferedReader(new FileReader("fichier.txt"))) {
            while ((ligneFichier = lireFichier.readLine()) != null) {
                String parts[] = ligneFichier.split("=");
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    switch (key) {
                        case "port":
                            port = Integer.parseInt(value);
                            break;
                        case "controlPort":
                            controlPort = Integer.parseInt(value);
                            break;
                    }
                }
            }

            // Déclaration des ServerSocket à l'intérieur du bloc try
            try (ServerSocket serverSocket = new ServerSocket(port);
                 ServerSocket controlSocket = new ServerSocket(controlPort)) {

                System.out.println("Serveur en attente de connexion...");

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    Socket controlClientSocket = controlSocket.accept();
                    System.out.println("Connexion établie avec un client : " + clientSocket.getInetAddress());

                    // Lancer un nouveau thread pour gérer la session du client
                    new Thread(new ClientHandler(clientSocket, controlClientSocket)).start();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;
    private Socket controlClientSocket;
    private JLabel imageLabel;

    public ClientHandler(Socket clientSocket, Socket controlClientSocket) {
        this.clientSocket = clientSocket;
        this.controlClientSocket = controlClientSocket;
        this.imageLabel = new JLabel();
    }

    @Override
    public void run() {
        JFrame frame = new JFrame("Affichage distant");
        JButton uploadButton = new JButton("Envoyer un fichier");

        // Utiliser un BorderLayout pour organiser les composants
        frame.setLayout(new BorderLayout());
        frame.add(imageLabel, BorderLayout.CENTER);
        frame.add(uploadButton, BorderLayout.SOUTH); // Ajouter le bouton en bas

        frame.setSize(1366, 768);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // Assurez-vous que le cadre a le focus
        frame.requestFocusInWindow();

        try {
            InputStream inputStream = clientSocket.getInputStream();
            OutputStream controlOutputStream = controlClientSocket.getOutputStream();
            DataOutputStream controlDataOutputStream = new DataOutputStream(controlOutputStream);

            // Mouse Listeners
            imageLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    try {
                        int button = e.getButton() == MouseEvent.BUTTON1 ? InputEvent.BUTTON1_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK;
                        controlDataOutputStream.writeUTF("CLICK:" + button);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });

            imageLabel.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    try {
                        int x = e.getX();
                        int y = e.getY();
                        controlDataOutputStream.writeUTF("MOVE:" + x + ":" + y);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });

            imageLabel.addMouseWheelListener(e -> {
                try {
                    int scrollAmount = e.getWheelRotation();
                    controlDataOutputStream.writeUTF("SCROLL:" + scrollAmount);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });

            // Key Listener
            frame.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed (KeyEvent e) {
                    try {
                        controlDataOutputStream.writeUTF("KEY_PRESS:" + e.getKeyCode());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    try {
                        controlDataOutputStream.writeUTF("KEY_RELEASE:" + e.getKeyCode());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });

            uploadButton.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setMultiSelectionEnabled(true); // Permettre la sélection de plusieurs fichiers
                if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    File[] files = fileChooser.getSelectedFiles();
                    sendFiles(files, controlDataOutputStream);
                }
            });

            // Receiving and displaying images
            while (true) {
                DataInputStream dataInputStream = new DataInputStream(inputStream);

                int imageSize = dataInputStream.readInt();
                byte[] imageBytes = new byte[imageSize];
                dataInputStream.readFully(imageBytes);

                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(imageBytes);
                BufferedImage image = ImageIO.read(byteArrayInputStream);

                if (image != null) {
                    imageLabel.setIcon(new ImageIcon(image));
                    frame.repaint();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
                controlClientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendFiles(File[] files, DataOutputStream controlDataOutputStream) {
        try {
            for (File file : files) {
                controlDataOutputStream.writeUTF("UPLOAD:" + file.getName() + ":" + file.length());
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        controlDataOutputStream.write(buffer, 0, bytesRead);
                    }
                }
            }
            controlDataOutputStream.writeUTF("FIN"); // Indiquer la fin de l'envoi
            System.out.println("Tous les fichiers ont été envoyés.");
        } catch (IOException e) {
            System.out.println("Erreur lors de l'envoi des fichiers : " + e.getMessage());
        }
    }
}