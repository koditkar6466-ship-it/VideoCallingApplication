// Source code is decompiled from a .class file using FernFlower decompiler.
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import javax.imageio.ImageIO;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

public class Server {
   public Server() {
   }

   public static void main(String[] args) {
      try {
         ServerSocket serverSocket = new ServerSocket(5000);

         label56: {
            try {
               System.out.println("Server started, waiting for a connection...");
               Socket socket = serverSocket.accept();
               System.out.println("Client connected.");
               OutputStream out = socket.getOutputStream();
               VideoCapture camera = new VideoCapture(0);
               if (!camera.isOpened()) {
                  System.out.println("Error: Camera not found.");
                  break label56;
               }

               Mat frame = new Mat();

               while(true) {
                  if (!camera.read(frame)) {
                     camera.release();
                     socket.close();
                     break;
                  }

                  BufferedImage image = matToBufferedImage(frame);
                  ByteArrayOutputStream baos = new ByteArrayOutputStream();
                  ImageIO.write(image, "jpg", baos);
                  byte[] imageBytes = baos.toByteArray();
                  out.write(intToByteArray(imageBytes.length));
                  out.write(imageBytes);
                  out.flush();
                  Thread.sleep(100L);
               }
            } catch (Throwable var10) {
               try {
                  serverSocket.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }

               throw var10;
            }

            serverSocket.close();
            return;
         }

         serverSocket.close();
      } catch (Exception var11) {
         var11.printStackTrace();
      }
   }

   public static BufferedImage matToBufferedImage(Mat mat) {
      int type = 10;
      if (mat.channels() > 1) {
         type = 5;
      }

      byte[] b = new byte[mat.channels() * mat.cols() * mat.rows()];
      mat.get(0, 0, b);
      BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
      image.getRaster().setDataElements(0, 0, mat.cols(), mat.rows(), b);
      return image;
   }

   public static byte[] intToByteArray(int value) {
      return new byte[]{(byte)(value >> 24), (byte)(value >> 16), (byte)(value >> 8), (byte)value};
   }

   static {
      System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
   }
}
