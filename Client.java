// Source code is decompiled from a .class file using FernFlower decompiler.
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Socket;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

public class Client {
   private JFrame frame = new JFrame("Video Client");
   private JLabel imageLabel;

   public Client() {
      this.frame.setDefaultCloseOperation(3);
      this.frame.setSize(640, 480);
      this.imageLabel = new JLabel();
      this.frame.add(this.imageLabel, "Center");
      this.frame.setVisible(true);
   }

   public void start(String serverAddress) {
      try {
         Socket socket = new Socket(serverAddress, 5000);

         try {
            InputStream in = socket.getInputStream();

            while(true) {
               BufferedImage image;
               do {
                  byte[] sizeAr = new byte[4];
                  in.read(sizeAr);
                  int size = byteArrayToInt(sizeAr);
                  byte[] imageAr = new byte[size];
                  in.read(imageAr);
                  image = ImageIO.read(new ByteArrayInputStream(imageAr));
               } while(image == null);

               ImageIcon icon = new ImageIcon(image);
               this.imageLabel.setIcon(icon);
               this.frame.repaint();
            }
         } catch (Throwable var10) {
            try {
               socket.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }

            throw var10;
         }
      } catch (Exception var11) {
         var11.printStackTrace();
      }
   }

   public static int byteArrayToInt(byte[] b) {
      return b[3] & 255 | (b[2] & 255) << 8 | (b[1] & 255) << 16 | (b[0] & 255) << 24;
   }

   public static void main(String[] args) {
      Client client = new Client();
      client.start("127.0.0.1");
   }
}
