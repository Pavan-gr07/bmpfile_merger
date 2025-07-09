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
import java.awt.*;
import java.awt.image.BufferedImage;
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

    // Add this as a class field to store the dithered data
    private int[][] currentDithered1BitData;
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
        int[][] mergedIntermediate1BitData = new int[mergedHeight][mergedWidth];

        PixelReader bodyPixelReader = bodyImage.getPixelReader();
        PixelReader borderPixelReader = borderImage.getPixelReader();

        // Convert body and border pixels to 0-255 grayscale values first, for better dithering
        double[][] bodyGrayData = convertToGrayscale(bodyPixelReader, bodyWidth, bodyHeight);
        double[][] borderGrayData = convertToGrayscale(borderPixelReader, borderWidth, borderHeight);

        // Perform pixel-by-pixel merge on grayscale data
        for (int y = 0; y < mergedHeight; y++) {
            for (int x = 0; x < mergedWidth; x++) {
                double finalGrayValue = bodyGrayData[y][x];

                // Assuming border overlays the leftmost 'borderWidth' pixels of the body:
                if (x < borderWidth) {
                    int borderY = y % borderHeight;
                    int borderX = x;

                    if (borderX < borderWidth && borderY < borderHeight) {
                        double borderGrayValue = borderGrayData[borderY][borderX];
                        finalGrayValue = Math.min(finalGrayValue, borderGrayValue);
                    }
                }
                mergedIntermediate1BitData[y][x] = (int)(finalGrayValue * 255.0);
            }
        }

        // Apply Floyd-Steinberg dithering
        int[][] dithered1BitData = floydSteinbergDither(mergedIntermediate1BitData, mergedWidth, mergedHeight);

        // STORE the dithered data for use in saving
        currentDithered1BitData = dithered1BitData;

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


    private void saveAsOneBitBmp(Stage stage, WritableImage currentMergedFxImage, int width, int height) {
        try {
            if (this.currentMergedFxImage == null) {
                showAlert("Error", "No merged image to save. Please merge images first.");
                return;
            }

            // Convert JavaFX image to BufferedImage first
            BufferedImage rgbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            PixelReader pixelReader = this.currentMergedFxImage.getPixelReader();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Color fxColor = pixelReader.getColor(x, y);
                    // Direct conversion - if it's white in JavaFX, make it white in BMP
                    int rgb = fxColor.equals(Color.WHITE) ? 0xFFFFFF : 0x000000;
                    rgbImage.setRGB(x, y, rgb);
                }
            }

            // Convert RGB to 1-bit BMP
            BufferedImage oneBitImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
            Graphics2D g2d = oneBitImage.createGraphics();
            g2d.drawImage(rgbImage, 0, 0, null);
            g2d.dispose();

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Merged 1-bit BMP");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("BMP Image", "*.bmp"));
            File outputFile = fileChooser.showSaveDialog(stage);

            if (outputFile != null) {
                boolean success = ImageIO.write(oneBitImage, "BMP", outputFile);
                if (success) {
                    statusLabel.setText("BMP saved (Method 3): " + outputFile.getAbsolutePath());
                    System.out.println("BMP saved (Method 3): " + outputFile.getAbsolutePath());
                    verifyBmpHeader(outputFile);
                } else {
                    showAlert("Error", "Failed to save BMP image.");
                }
            }

        } catch (Exception ex) {
            statusLabel.setText("Error saving BMP: " + ex.getMessage());
            showAlert("Error", "Error saving BMP: " + ex.getMessage());
            ex.printStackTrace();
        }
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