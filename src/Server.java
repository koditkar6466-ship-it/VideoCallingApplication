import org.opencv.core.*;
import org.opencv.videoio.*;
import javax.imageio.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import javax.sound.sampled.*;

public class Server extends JFrame {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    static JLabel localVideoLabel;
    static JLabel remoteVideoLabel;
    static VideoCapture camera;
    static boolean isStreaming;
    static ServerSocket videoServerSocket;
    static Socket videoSocket;
    static ObjectOutputStream videoOutputStream;
    static ObjectInputStream videoInputStream;
    static DatagramSocket audioSocket;
    static final int VIDEO_PORT = 4040;
    static final int AUDIO_PORT = 12345;
    static final int BUFFER_SIZE = 4096; 
    static SourceDataLine speakers;
    static DatagramSocket sendAudioSocket;
    static InetAddress clientAddress;
    static int clientPort;

    public Server() {
        setTitle("Server Video Call");
        
        setSize(900, 600);
        setVisible(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel videoPanel = new JPanel(new GridBagLayout());
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        localVideoLabel = new JLabel();
        localVideoLabel.setPreferredSize(new Dimension(300, 300));
        gbc.gridx = 0;
        gbc.gridy = 0;
        videoPanel.add(localVideoLabel, gbc);

        remoteVideoLabel = new JLabel();
        remoteVideoLabel.setPreferredSize(new Dimension(300, 300));
        gbc.gridx = 1;
        gbc.gridy = 0;
        videoPanel.add(remoteVideoLabel, gbc);
        add(videoPanel, BorderLayout.CENTER);

        JButton toggleButton = new JButton("Start Call");
        toggleButton.setBackground(Color.green);
        toggleButton.setPreferredSize(new Dimension(150, 50));  
        toggleButton.addActionListener(e -> {
            if (toggleButton.getText().equals("Start Call")) {
                toggleButton.setText("Stop Call");
                toggleButton.setBackground(Color.red);
                startServer();
            } else {
                toggleButton.setText("Start Call");
                toggleButton.setBackground(Color.green);
                stopServer();
            }
        });
        add(toggleButton, BorderLayout.SOUTH);

        camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            System.out.println("Error: Cannot open camera!");
            return;
        }

    }

    public void startServer() {
        isStreaming = true;

        new Thread(this::startVideoStreaming).start();
        new Thread(this::startAudioStreaming).start();
    }

    public void startVideoStreaming() {
        try {
            videoServerSocket = new ServerSocket(VIDEO_PORT);
            videoSocket = videoServerSocket.accept();
            videoOutputStream = new ObjectOutputStream(videoSocket.getOutputStream());
            videoInputStream = new ObjectInputStream(videoSocket.getInputStream());

            new Thread(this::receiveFrames).start();

            sendFrames();

        } catch (IOException e) {

        } finally {
            stopServer();
        }
    }

    public void sendFrames() {
        try {
            while (isStreaming) {
                Mat frame = new Mat();
                if (camera.read(frame)) {
                    BufferedImage localImage = matToBufferedImage(frame);
                    BufferedImage resizedLocalImage = resizeImage(localImage, 300, 300);
                    localVideoLabel.setIcon(new ImageIcon(resizedLocalImage));

                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    ImageIO.write(resizedLocalImage, "jpg", byteArrayOutputStream);
                    byte[] imageBytes = byteArrayOutputStream.toByteArray();
                    videoOutputStream.writeObject(imageBytes);
                    videoOutputStream.flush();
                }
                Thread.sleep(33);
            }
        } catch (IOException | InterruptedException e) {

        }
    }

    public void receiveFrames() {
        try {
            while (isStreaming) {
                byte[] imageBytes = (byte[]) videoInputStream.readObject();
                BufferedImage remoteImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
                BufferedImage resizedRemoteImage = resizeImage(remoteImage, 300, 300);
                remoteVideoLabel.setIcon(new ImageIcon(resizedRemoteImage));
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void startAudioStreaming() {
        AudioFormat audioFormat = getAudioFormat();
        try {

            audioSocket = new DatagramSocket(AUDIO_PORT);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(audioFormat);
            speakers.start();

            sendAudioSocket = new DatagramSocket();
            clientAddress = InetAddress.getByName("192.168.139.46");
            clientPort = AUDIO_PORT;

            byte[] buffer = new byte[BUFFER_SIZE];

            System.out.println("Listening for audio...");

            new Thread(() -> {
                try (TargetDataLine microphone = AudioSystem.getTargetDataLine(audioFormat)) {
                    microphone.open(audioFormat);
                    microphone.start();

                    while (isStreaming) {
                        int bytesRead = microphone.read(buffer, 0, buffer.length);
                        sendAudio(sendAudioSocket, buffer, bytesRead);
                    }
                } catch (Exception e) {

                }
            }).start();

            new Thread(() -> {
                try {
                    while (isStreaming) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        audioSocket.receive(packet);
                        speakers.write(packet.getData(), 0, packet.getLength());
                    }
                } catch (IOException e) {

                }
            }).start();
        } catch (Exception e) {

        }
    }

    private void sendAudio(DatagramSocket socket, byte[] audioData, int length) {
        try {
            DatagramPacket packet = new DatagramPacket(audioData, length, clientAddress, clientPort);
            socket.send(packet);
        } catch (IOException e) {

        }
    }

    private static AudioFormat getAudioFormat() {
        float sampleRate = 32000.0F;
        int sampleSizeInBits = 16;
        int channels = 2;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    public void stopServer() {
        isStreaming = false;
        try {
            if (videoOutputStream != null) {
                videoOutputStream.close();
            }
            if (videoInputStream != null) {
                videoInputStream.close();
            }
            if (videoSocket != null) {
                videoSocket.close();
            }
            if (videoServerSocket != null) {
                videoServerSocket.close();
            }
            if (camera.isOpened()) {
                camera.release();
            }
            if (audioSocket != null) {
                audioSocket.close();
            }
            if (sendAudioSocket != null) {
                sendAudioSocket.close();
            }
            if (speakers != null) {
                speakers.close();
            }
        } catch (IOException e) {

        }
    }

    public static BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] buffer = new byte[bufferSize];
        mat.get(0, 0, buffer);
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(buffer, 0, targetPixels, 0, buffer.length);
        return image;
    }

    public static BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
        Image scaledImage = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(scaledImage, 0, 0, null);
        g2d.dispose();
        return resizedImage;
    }

    public static void main(String[] args) {
        new Server();
    }
}
