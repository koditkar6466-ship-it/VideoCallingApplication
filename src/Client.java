
import javax.imageio.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import javax.sound.sampled.*;
import org.opencv.core.*;
import org.opencv.videoio.*;

public class Client extends JFrame {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private static final int AUDIO_PORT = 12345;
    private static final int VIDEO_PORT = 4040;
    private static final int BUFFER_SIZE = 4096;
    private static final String SERVER_IP = "192.168.139.53";

    private static JButton toggleButton;
    static JLabel localVideoLabel;
    static JLabel remoteVideoLabel;
    static VideoCapture camera;
    static boolean isStreaming;
    static Socket videoSocket;
    static ObjectOutputStream videoOutputStream;
    static ObjectInputStream videoInputStream;
    static DatagramSocket audioSocket;
    static TargetDataLine targetLine;
    static SourceDataLine sourceLine;

    public Client() {
        setTitle("Client Video Call");
        setSize(900, 600);
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

        toggleButton = new JButton("Start Call");
        toggleButton.setPreferredSize(new Dimension(150, 50));
        toggleButton.addActionListener(e -> {
            if (toggleButton.getText().equals("Start Call")) {
                toggleButton.setText("End Call");
                startClient();
            } else {
                toggleButton.setText("Start Call");
                stopClient();
            }
        });
        add(toggleButton, BorderLayout.SOUTH);

        camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            System.out.println("Error: Cannot open camera!");
            return;
        }

        setVisible(true);
    }

    public void startClient() {
        isStreaming = true;

        new Thread(this::startVideoStreaming).start();

        new Thread(this::startAudioStreaming).start();
        new Thread(this::receiveAudio).start();
    }

    public void startVideoStreaming() {
        try {
            videoSocket = new Socket(SERVER_IP, VIDEO_PORT);
            videoOutputStream = new ObjectOutputStream(videoSocket.getOutputStream());
            videoInputStream = new ObjectInputStream(videoSocket.getInputStream());

            new Thread(this::sendFrames).start();
            receiveFrames();

        } catch (IOException e) {
        } finally {
            stopClient();
        }
    }

    public void startAudioStreaming() {
        AudioFormat audioFormat = getAudioFormat();
        try {
            audioSocket = new DatagramSocket();
            targetLine = AudioSystem.getTargetDataLine(audioFormat);
            targetLine.open(audioFormat);
            targetLine.start();

            byte[] buffer = new byte[BUFFER_SIZE];

            System.out.println("Recording and sending audio...");

            while (isStreaming) {
                int bytesRead = targetLine.read(buffer, 0, buffer.length);
                DatagramPacket packet = new DatagramPacket(buffer, bytesRead, InetAddress.getByName(SERVER_IP), AUDIO_PORT);
                audioSocket.send(packet);
            }
        } catch (Exception e) {
        }
    }

    public void receiveAudio() {
        AudioFormat audioFormat = getAudioFormat();
        try {
            DatagramSocket receiveSocket = new DatagramSocket(AUDIO_PORT);
            byte[] buffer = new byte[BUFFER_SIZE];

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            sourceLine = (SourceDataLine) AudioSystem.getLine(info);
            sourceLine.open(audioFormat);
            sourceLine.start();

            if (sourceLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl volumeControl = (FloatControl) sourceLine.getControl(FloatControl.Type.MASTER_GAIN);
                float volume = 6.0f;
                volumeControl.setValue(volume);
            }

            System.out.println("Receiving and playing audio...");

            while (isStreaming) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiveSocket.receive(packet);

                if (packet.getLength() > 0) {
                    sourceLine.write(packet.getData(), 0, packet.getLength());
                }
            }

            sourceLine.drain();
            sourceLine.close();
            receiveSocket.close();
        } catch (Exception e) {
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
        }
    }

    public void stopClient() {
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
            if (camera.isOpened()) {
                camera.release();
            }
            if (audioSocket != null) {
                audioSocket.close();
            }
            if (targetLine != null) {
                targetLine.close();
            }
            if (sourceLine != null) {
                sourceLine.close();
            }
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
        new Client();
    }
}
