package com.example.bmpfile_merger;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class BmpOneBitImageMergerController {

  
    @FXML private Button saveButton;
    @FXML private ImageView mergedImageView;
    @FXML private Label statusLabel;

    private Image bodyImage;
    private Image borderImage;
    private Stage primaryStage;
    private WritableImage currentMergedFxImage;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    @FXML
    private void handleSelectBodyImage() {
        File file = chooseImageFile("Select Body Image");
        if (file != null) {
            bodyImage = new Image(file.toURI().toString());
            statusLabel.setText("Body Image Loaded: " + file.getName() + " (Dim: " + (int)bodyImage.getWidth() + "x" + (int)bodyImage.getHeight() + ")");
        }
    }

    @FXML
    private void handleSelectBorderImage() {
        File file = chooseImageFile("Select Border Image");
        if (file != null) {
            borderImage = new Image(file.toURI().toString());
            statusLabel.setText("Border Image Loaded: " + file.getName() + " (Dim: " + (int)borderImage.getWidth() + "x" + (int)borderImage.getHeight() + ")");
        }
    }

    @FXML
    private void handleMergeImages() {
        if (bodyImage == null) {
            showAlert("Error", "Please select a Body Image first.");
            return;
        }
        if (borderImage == null) {
            showAlert("Error", "Please select a Border Image first.");
            return;
        }

        int bodyWidth = (int) bodyImage.getWidth();
        int bodyHeight = (int) bodyImage.getHeight();
        int borderHeight = (int) borderImage.getHeight();
        int borderWidth = (int) borderImage.getWidth();

        int mergedWidth = bodyWidth;
        int mergedHeight = bodyHeight;

        // Perform the pixel-by-pixel merge into a temporary 2D array of 0s and 1s
        // This is done before dithering the final image for display/save
        int[][] mergedIntermediate1BitData = new int[mergedHeight][mergedWidth];

        PixelReader bodyPixelReader = bodyImage.getPixelReader();
        PixelReader borderPixelReader = borderImage.getPixelReader();

        // Convert body and border pixels to 0-255 grayscale values first, for better dithering
        // JavaFX Color.getBrightness() returns 0.0-1.0
        double[][] bodyGrayData = convertToGrayscale(bodyPixelReader, bodyWidth, bodyHeight);
        double[][] borderGrayData = convertToGrayscale(borderPixelReader, borderWidth, borderHeight);

        // Perform pixel-by-pixel merge on grayscale data
        for (int y = 0; y < mergedHeight; y++) {
            for (int x = 0; x < mergedWidth; x++) {
                double finalGrayValue = bodyGrayData[y][x];

                // Assuming border overlays the leftmost 'borderWidth' pixels of the body:
                if (x < borderWidth) {
                    int borderY = y % borderHeight;
                    int borderX = x; // Maps body X directly to border X

                    if (borderX < borderWidth && borderY < borderHeight) {
                        double borderGrayValue = borderGrayData[borderY][borderX];

                        // Merging logic (still logical AND equivalent on 1-bit values,
                        // but now applied to grayscale for better dithering)
                        // If border pixel is dark (close to 0.0), make the final value darker.
                        // If border pixel is light (close to 1.0), keep the body value.
                        // This approximates an AND operation.
                        finalGrayValue = Math.min(finalGrayValue, borderGrayValue); // Use min for "darkest pixel wins"
                    }
                }
                // Store the merged grayscale value (0.0-1.0)
                mergedIntermediate1BitData[y][x] = (int)(finalGrayValue * 255.0); // Convert to 0-255 for dithering
            }
        }

        // Now, apply Floyd-Steinberg dithering to the merged grayscale data
        // This array will hold the final 1-bit (0 or 1) values
        int[][] dithered1BitData = floydSteinbergDither(mergedIntermediate1BitData, mergedWidth, mergedHeight);

        // Create WritableImage for preview from dithered data
        WritableImage mergedOutputFxImage = new WritableImage(mergedWidth, mergedHeight);
        for (int y = 0; y < mergedHeight; y++) {
            for (int x = 0; x < mergedWidth; x++) {
                Color finalColor = (dithered1BitData[y][x] == 1) ? Color.WHITE : Color.BLACK;
                mergedOutputFxImage.getPixelWriter().setColor(x, y, finalColor);
            }
        }

        currentMergedFxImage = mergedOutputFxImage;
        mergedImageView.setImage(currentMergedFxImage);
        statusLabel.setText("Images merged and dithered. Click 'Save' to convert to 1-bit BMP.");
        saveButton.setVisible(true);
    }

    // New helper to convert to grayscale 0.0-1.0
    private double[][] convertToGrayscale(PixelReader pixelReader, int width, int height) {
        double[][] grayData = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grayData[y][x] = pixelReader.getColor(x, y).getBrightness(); // Returns 0.0 to 1.0
            }
        }
        return grayData;
    }

    // New Dithering Algorithm
    private int[][] floydSteinbergDither(int[][] grayData255, int width, int height) {
        int[][] output1Bit = new int[height][width];
        // Create a copy to modify during error diffusion
        double[][] pixelValues = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixelValues[y][x] = grayData255[y][x]; // Convert back to 0-255 for processing
            }
        }

        double threshold = 128.0; // Midpoint for black/white (0-255 scale)

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double oldPixel = pixelValues[y][x];
                int newPixel = (oldPixel > threshold) ? 255 : 0; // Quantize to black or white (0 or 255)
                output1Bit[y][x] = (newPixel == 255) ? 1 : 0; // Store 1-bit value (1 for white, 0 for black)

                double error = oldPixel - newPixel;

                // Distribute error to neighbors (Floyd-Steinberg coefficients)
                //      X   7/16
                // 3/16 5/16 1/16

                // (x+1, y)
                if (x + 1 < width) {
                    pixelValues[y][x + 1] += error * 7.0 / 16.0;
                }
                // (x-1, y+1)
                if (x - 1 >= 0 && y + 1 < height) {
                    pixelValues[y + 1][x - 1] += error * 3.0 / 16.0;
                }
                // (x, y+1)
                if (y + 1 < height) {
                    pixelValues[y + 1][x] += error * 5.0 / 16.0;
                }
                // (x+1, y+1)
                if (x + 1 < width && y + 1 < height) {
                    pixelValues[y + 1][x + 1] += error * 1.0 / 16.0;
                }
            }
        }
        return output1Bit;
    }

    @FXML
    private void handleSaveImage() {
        if (currentMergedFxImage == null) {
            showAlert("Error", "No merged image to save. Please merge images first.");
            return;
        }

        int width = (int) currentMergedFxImage.getWidth();
        int height = (int) currentMergedFxImage.getHeight();

        saveAsOneBitBmp(primaryStage, currentMergedFxImage, width, height);
    }

    private File chooseImageFile(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files (BMP, PNG, JPG)", "*.bmp", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        return fileChooser.showOpenDialog(primaryStage);
    }

    private void saveAsOneBitBmp(Stage stage, WritableImage fxImage, int width, int height) {
        try {
            byte[] r = { (byte)0x00, (byte)0xFF };
            byte[] g = { (byte)0x00, (byte)0xFF };
            byte[] b = { (byte)0x00, (byte)0xFF };
            IndexColorModel colorModel = new IndexColorModel(1, 2, r, g, b);

            int bytesPerRow = (width + 7) / 8;
            int paddedBytesPerRow = ((bytesPerRow + 3) / 4) * 4;
            byte[] pixelDataBuffer = new byte[paddedBytesPerRow * height];

            PixelReader pixelReader = fxImage.getPixelReader();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Color color = pixelReader.getColor(x, y);
                    boolean isWhite = color.getBrightness() > 0.5; // This threshold is applied to the already dithered image

                    int byteIndex = y * paddedBytesPerRow + (x / 8);
                    int bitIndex = 7 - (x % 8);

                    if (byteIndex >= pixelDataBuffer.length || byteIndex < 0) {
                        throw new IndexOutOfBoundsException("Calculated byteIndex " + byteIndex + " is out of bounds for pixelDataBuffer length " + pixelDataBuffer.length + " at (x=" + x + ", y=" + y + ")");
                    }

                    if (isWhite) {
                        pixelDataBuffer[byteIndex] |= (1 << bitIndex);
                    } else {
                        pixelDataBuffer[byteIndex] &= ~(1 << bitIndex);
                    }
                }
            }

            BufferedImage oneBitImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY, colorModel);
            DataBufferByte dataBuffer = (DataBufferByte) oneBitImage.getRaster().getDataBuffer();
            byte[] rasterInternalArray = dataBuffer.getData();

            if (pixelDataBuffer.length != rasterInternalArray.length) {
                System.err.println("Warning: Calculated pixelDataBuffer size (" + pixelDataBuffer.length + ") does not match BufferedImage internal array size (" + rasterInternalArray.length + "). Copying min.");
            }
            System.arraycopy(pixelDataBuffer, 0, rasterInternalArray, 0, Math.min(pixelDataBuffer.length, rasterInternalArray.length));


            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Merged 1-bit BMP");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("BMP Image", "*.bmp"));
            File outputFile = fileChooser.showSaveDialog(stage);

            if (outputFile != null) {
                boolean success = ImageIO.write(oneBitImage, "BMP", outputFile);
                if (success) {
                    statusLabel.setText("Merged 1-bit BMP saved to: " + outputFile.getAbsolutePath());
                    System.out.println("Merged 1-bit BMP saved to: " + outputFile.getAbsolutePath());
                    verifyBmpHeader(outputFile);
                } else {
                    statusLabel.setText("Error: Could not save 1-bit BMP. ImageIO writer not found or error.");
                    showAlert("Error", "Could not save 1-bit BMP. Ensure 'BMP' format is supported by ImageIO.");
                }
            }
        } catch (IOException ex) {
            statusLabel.setText("Error saving image: " + ex.getMessage());
            showAlert("Error", "Error saving image: " + ex.getMessage());
            ex.printStackTrace();
        } catch (Exception ex) {
            statusLabel.setText("An unexpected error occurred: " + ex.getMessage());
            showAlert("Error", "An unexpected error occurred during 1-bit conversion: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void verifyBmpHeader(File bmpFile) {
        // ... (Header verification logic remains the same)
        try {
            byte[] header = new byte[54]; // Standard BMP header size
            try (var fis = Files.newInputStream(bmpFile.toPath(), StandardOpenOption.READ)) {
                fis.read(header);
            }

            String signature = new String(Arrays.copyOfRange(header, 0, 2));
            int fileSize = ByteBuffer.wrap(Arrays.copyOfRange(header, 2, 6)).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int pixelDataOffset = ByteBuffer.wrap(Arrays.copyOfRange(header, 10, 14)).order(ByteOrder.LITTLE_ENDIAN).getInt();

            int infoHeaderSize = ByteBuffer.wrap(Arrays.copyOfRange(header, 14, 18)).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int width = ByteBuffer.wrap(Arrays.copyOfRange(header, 18, 22)).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int height = ByteBuffer.wrap(Arrays.copyOfRange(header, 22, 26)).order(ByteOrder.LITTLE_ENDIAN).getInt();
            short bitsPerPixel = ByteBuffer.wrap(Arrays.copyOfRange(header, 28, 30)).order(ByteOrder.LITTLE_ENDIAN).getShort();
            int colorsUsed = ByteBuffer.wrap(Arrays.copyOfRange(header, 46, 50)).order(ByteOrder.LITTLE_ENDIAN).getInt();

            System.out.println("\n--- BMP Header Verification for: " + bmpFile.getName() + " ---");
            System.out.println("Signature (BF Type): " + signature);
            System.out.println("File Size: " + fileSize + " bytes (Expected: " + bmpFile.length() + ")");
            System.out.println("Pixel Data Offset: " + pixelDataOffset);
            System.out.println("Info Header Size (biSize): " + infoHeaderSize);
            System.out.println("Width (biWidth): " + width);
            System.out.println("Height (biHeight): " + height);
            System.out.println("Bits Per Pixel (biBitCount): " + bitsPerPixel);
            System.out.println("Colors Used (biClrUsed): " + colorsUsed);
            System.out.println("----------------------------------------------");

            if (!"BM".equals(signature)) {
                System.err.println("WARNING: BMP signature is not 'BM'.");
            }
            if (bitsPerPixel != 1) {
                System.err.println("WARNING: Output BMP is not 1-bit per pixel. Found: " + bitsPerPixel);
            }
            if (colorsUsed != 2) {
                System.err.println("WARNING: Expected 2 colors in palette for 1-bit BMP. Found: " + colorsUsed);
            }
        } catch (IOException e) {
            System.err.println("Error reading BMP header for verification: " + e.getMessage());
        }
    }
}