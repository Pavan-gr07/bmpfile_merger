package com.example.bmpfile_merger;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class BmpOneBitImageMergerController {

    @FXML private Button selectBodyBtn;
    @FXML private Button selectBorderBtn;
    @FXML private Button mergeButton;
    @FXML private Button saveButton;
    @FXML private ImageView mergedImageView;
    @FXML private Label statusLabel;

    private Image bodyImage;
    private Image borderImage;
    private Stage primaryStage; // To get the primary stage for file choosers

    // Method to set the primary stage (called from the main Application class)
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    @FXML
    private void handleSelectBodyImage() {
        File file = chooseImageFile("Select Body Image");
        if (file != null) {
            bodyImage = new Image(file.toURI().toString());
            statusLabel.setText("Body Image Loaded: " + file.getName());
        }
    }

    @FXML
    private void handleSelectBorderImage() {
        File file = chooseImageFile("Select Border Image");
        if (file != null) {
            borderImage = new Image(file.toURI().toString());
            statusLabel.setText("Border Image Loaded: " + file.getName());
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

        // Calculate dimensions for the merged image (body on left, border on right)
        int mergedWidth = bodyWidth + borderWidth;
        int mergedHeight = bodyHeight;

        // Create a Canvas to draw on
        Canvas canvas = new Canvas(mergedWidth, mergedHeight);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // 1. Draw the Body Image (on the left side)
        gc.drawImage(bodyImage, 0, 0, bodyWidth, bodyHeight);

        // 2. Draw the Repeating Border Image (on the right side)
        double currentBorderY = 0;
        while (currentBorderY < mergedHeight) {
            double drawHeight = Math.min(borderHeight, mergedHeight - currentBorderY);
            gc.drawImage(borderImage,
                    bodyWidth,           // X position: right after the body image
                    currentBorderY,      // Y position: current position
                    borderWidth,         // Width: full border width
                    drawHeight           // Height: full border height or clipped
            );
            currentBorderY += borderHeight;
        }

        // Take a snapshot of the canvas to get a WritableImage
        WritableImage mergedFxImage = canvas.snapshot(null, null);
        mergedImageView.setImage(mergedFxImage);
        statusLabel.setText("Images merged. Click 'Save' to convert to 1-bit BMP.");

        // Make the save button visible
        saveButton.setVisible(true);
    }

    @FXML
    private void handleSaveImage() {
        if (mergedImageView.getImage() == null) {
            showAlert("Error", "No merged image to save. Please merge images first.");
            return;
        }

        WritableImage fxImage = (WritableImage) mergedImageView.getImage();
        int width = (int) fxImage.getWidth();
        int height = (int) fxImage.getHeight();

        saveAsOneBitBmp(primaryStage, fxImage, width, height);
    }

    private File chooseImageFile(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files (BMP, PNG, JPG)", "*.bmp", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        return fileChooser.showOpenDialog(primaryStage); // Use the stored primary stage
    }

    private void saveAsOneBitBmp(Stage stage, WritableImage fxImage, int width, int height) {
        try {
            // Create a 1-bit (monochrome) BufferedImage
            byte[] bwPalette = {(byte) 0x00, (byte) 0xFF}; // Black (0) and White (255)
            IndexColorModel colorModel = new IndexColorModel(1, 2, bwPalette, bwPalette, bwPalette);

            // Calculate bytes per row for 1-bit, padded to 4-byte boundary
            int bytesPerRow = (width + 7) / 8; // Pixels per byte, rounded up
            int paddedBytesPerRow = (bytesPerRow % 4 == 0) ? bytesPerRow : ((bytesPerRow / 4) + 1) * 4;

            byte[] pixelData = new byte[paddedBytesPerRow * height];
            WritableRaster raster = colorModel.createCompatibleWritableRaster(width, height);
            BufferedImage oneBitImage = new BufferedImage(colorModel, raster, false, null);

            // Iterate over pixels of the source image and convert to 1-bit
            PixelReader pixelReader = fxImage.getPixelReader();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Color color = pixelReader.getColor(x, y);
                    // Simple thresholding: if brighter than 0.5, it's white (1), otherwise black (0)
                    boolean isWhite = color.getBrightness() > 0.5;

                    int byteIndex = y * paddedBytesPerRow + (x / 8);
                    int bitIndex = 7 - (x % 8); // BMP stores bits from MSB to LSB within a byte

                    if (isWhite) {
                        pixelData[byteIndex] |= (1 << bitIndex); // Set the bit to 1 (white)
                    } else {
                        pixelData[byteIndex] &= ~(1 << bitIndex); // Set the bit to 0 (black)
                    }
                }
            }

            // Set the pixel data to the raster
            raster.setDataElements(0, 0, width, height, pixelData);


            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Merged 1-bit BMP");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("BMP Image", "*.bmp"));
            File outputFile = fileChooser.showSaveDialog(stage);

            if (outputFile != null) {
                boolean success = ImageIO.write(oneBitImage, "BMP", outputFile);
                if (success) {
                    statusLabel.setText("Merged 1-bit BMP saved to: " + outputFile.getAbsolutePath());
                    System.out.println("Merged 1-bit BMP saved to: " + outputFile.getAbsolutePath());
                    verifyBmpHeader(outputFile); // Optional verification
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

    // --- Optional: Verify BMP Header (for debugging/learning) ---
    private void verifyBmpHeader(File bmpFile) {
        try {
            byte[] header = new byte[54]; // Standard BMP header size
            try (var fis = Files.newInputStream(bmpFile.toPath(), StandardOpenOption.READ)) {
                fis.read(header);
            }

            // Read relevant fields (little-endian)
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