import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import javax.imageio.ImageIO;

public class Client {
    public static void main(String[] args) {
        String serverIP = ""; 
        int port = 0; //  captures d'écran
        int controlPort = 0; //  les commandes clavier et souris
        String ligne = "";

        // Lire le fichier de configuration
        try (BufferedReader lire = new BufferedReader(new FileReader("fichier.txt"))) {
            while ((ligne = lire.readLine()) != null) {
                String parts[] = ligne.split("=");
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    switch (key) {
                        case "ip":
                            serverIP = value;
                            break;
                        case "port":
                            port = Integer.parseInt(value);
                            break;
                        case "controlPort":
                            controlPort = Integer.parseInt(value);
                            break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return; // Sortir si le fichier ne peut pas être lu
        }

        try (Socket imageSocket = new Socket(serverIP, port);
             Socket controlSocket = new Socket(serverIP, controlPort);
             OutputStream imageOutputStream = imageSocket.getOutputStream();
             InputStream controlInputStream = controlSocket.getInputStream()) {

            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

            // Thread pour gérer les commandes clavier et souris
            new Thread(() -> {
                try (DataInputStream dataInputStream = new DataInputStream(controlInputStream)) {
                    while (true) {
                        String command = dataInputStream.readUTF();
                        String[] parts = command.split(":");

                        if (parts[0].equals("MOVE")) {
                            int x = Integer.parseInt(parts[1]);
                            int y = Integer.parseInt(parts[2]);
                            robot.mouseMove(x, y);
                        } else if (parts[0].equals("CLICK")) {
                            int button = Integer.parseInt(parts[1]);
                            robot.mousePress(button);
                            robot.mouseRelease(button);
                        } else if (parts[0].equals("SCROLL")) {
                            int scrollAmount = Integer.parseInt(parts[1]);
                            robot.mouseWheel(scrollAmount);
                        } else if (parts[0].equals("KEY_PRESS")) {
                            int keyCode = Integer.parseInt(parts[1]);
                            robot.keyPress(keyCode);
                        } else if (parts[0].equals("KEY_RELEASE")) {
                            int keyCode = Integer.parseInt(parts[1]);
                            robot.keyRelease(keyCode);
                        } else if (parts[0].equals("UPLOAD")) {
                            String fileName = parts[1];
                            long fileSize = Long.parseLong(parts[2]);
                            byte[] fileBytes = new byte[(int) fileSize];
                            DataInputStream controlDataIn = new DataInputStream(controlInputStream);
                            controlDataIn.readFully(fileBytes);
                            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                                fos.write(fileBytes);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            // Capture d’écran et envoi
            while (true) {
                BufferedImage screenCapture = robot.createScreenCapture(screenRect);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ImageIO.write(screenCapture, "jpg", byteArrayOutputStream);

                byte[] imageBytes = byteArrayOutputStream.toByteArray();
                DataOutputStream dataOutputStream = new DataOutputStream(imageOutputStream);
                dataOutputStream.writeInt(imageBytes.length);
                dataOutputStream.write(imageBytes);

                Thread.sleep(100);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}