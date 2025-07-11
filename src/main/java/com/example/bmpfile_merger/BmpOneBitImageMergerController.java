package com.example.bmpfile_merger;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.ImageView; // Added ImageView import
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class BmpOneBitImageMergerController {

    @FXML private Button saveButton;
    @FXML private ImageView mergedImageView;
    @FXML private Label statusLabel;

    // Store the loaded BMP images as custom BmpImage objects
    private BmpImage file1Bmp; // Corresponds to 'bodyImage' in original context, but now file1.bmp
    private BmpImage file2Bmp; // Corresponds to 'borderImage' in original context, but now file2.bmp

    // Store the merged 1-bit data (0s and 1s) for saving
    private byte[][] currentMerged1BitData;
    private int mergedWidth;
    private int mergedHeight;

    private Stage primaryStage;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    /**
     * Handles selecting file1.bmp (referred to as "Body Image" in the UI).
     */
    @FXML
    private void handleSelectBodyImage() {
        File file = chooseImageFile("Select File1.bmp (Body Image)");
        if (file != null) {
            try {
                file1Bmp = BmpImage.read(file);
                statusLabel.setText("File1.bmp Loaded: " + file.getName() + " (Dim: " + file1Bmp.width + "x" + file1Bmp.height + ")");
            } catch (IOException e) {
                showAlert("Error Loading Image", "Could not read File1.bmp. Please ensure it's a valid 1-bit BMP file.\n" + e.getMessage());
                file1Bmp = null;
            }
        }
    }

    /**
     * Handles selecting file2.bmp (referred to as "Border Image" in the UI).
     */
    @FXML
    private void handleSelectBorderImage() {
        File file = chooseImageFile("Select File2.bmp (Border Image)");
        if (file != null) {
            try {
                file2Bmp = BmpImage.read(file);
                statusLabel.setText("File2.bmp Loaded: " + file.getName() + " (Dim: " + file2Bmp.width + "x" + file2Bmp.height + ")");
            } catch (IOException e) {
                showAlert("Error Loading Image", "Could not read File2.bmp. Please ensure it's a valid 1-bit BMP file.\n" + e.getMessage());
                file2Bmp = null;
            }
        }
    }

    /**
     * Handles the merging logic based on the specified requirements.
     */

    @FXML
    private void handleMergeImages() {
        if (file1Bmp == null) {
            showAlert("Error", "Please select File1.bmp first.");
            return;
        }
        if (file2Bmp == null) {
            showAlert("Error", "Please select File2.bmp first.");
            return;
        }

        // 1. Check width condition
        if (file1Bmp.width != file2Bmp.width + 1) {
            showAlert("Error", "Condition Failed: File1.bmp width (" + file1Bmp.width + ") must be 1 pixel greater than File2.bmp width (" + file2Bmp.width + ").");
            return;
        }

        mergedWidth = file2Bmp.width;
        mergedHeight = file1Bmp.height;
        currentMerged1BitData = new byte[mergedHeight][mergedWidth]; // Stores 0s or 1s for each pixel

        int file2CurrentLineIndex = 0; // Tracks the current line in file2.bmp, with rollover

        // Iterate through each line of file1.bmp (from top to bottom, as per array indexing)
        for (int y = 0; y < mergedHeight; y++) {
            // Get the raw byte data for the current row of file1.bmp
            // Remember BMP pixel data is stored bottom-up. So, row 'y' in our top-down logic
            // corresponds to 'file1Bmp.height - 1 - y' in the raw BMP pixel data array.
            int file1BmpRawRowIndex = file1Bmp.height - 1 - y;
            byte[] file1RowBytes = BmpImage.getRowBytes(file1Bmp.pixelData, file1BmpRawRowIndex, file1Bmp.paddedRowSize);

            // Extract the last bit of the current line of file1.bmp
            // The last bit is at (file1Bmp.width - 1)
            int lastBitX = file1Bmp.width - 1;
            int lastBitByteIndex = lastBitX / 8;
            int lastBitOffsetInByte = 7 - (lastBitX % 8); // Bits are stored from MSB to LSB within a byte
            int lastBitOfFile1Row = (file1RowBytes[lastBitByteIndex] >> lastBitOffsetInByte) & 1;

            byte[] sourceRowBytesForMerge;

            if (lastBitOfFile1Row == 1) { // Last bit is WHITE (1), merge with file2.bmp
                // Determine the line from file2.bmp, handling rollover
                int file2BmpRawRowIndex = file2Bmp.height - 1 - (file2CurrentLineIndex % file2Bmp.height);
                sourceRowBytesForMerge = BmpImage.getRowBytes(file2Bmp.pixelData, file2BmpRawRowIndex, file2Bmp.paddedRowSize);
                file2CurrentLineIndex++; // Move to the next line in file2.bmp for the next potential merge
            } else { // Last bit is BLACK (0), keep file1.bmp line as is (without its last bit)
                sourceRowBytesForMerge = file1RowBytes;
            }

            // Populate currentMerged1BitData for the current row
            for (int x = 0; x < mergedWidth; x++) { // Iterate up to the new mergedWidth (file2.bmp width)
                int sourceBitX = x; // The pixel position in the source row
                int sourceByteIndex = sourceBitX / 8;
                int sourceBitOffsetInByte = 7 - (sourceBitX % 8);

                // Ensure we don't go out of bounds for sourceRowBytesForMerge
                // This check is important if sourceWidth (file2Bmp.width) is smaller than file1Bmp.width
                if (sourceByteIndex < sourceRowBytesForMerge.length) {
                    int bitValue = (sourceRowBytesForMerge[sourceByteIndex] >> sourceBitOffsetInByte) & 1;
                    currentMerged1BitData[y][x] = (byte) bitValue;
                } else {
                    // This should ideally not happen if sourceWidth is handled correctly, but as a fallback
                    currentMerged1BitData[y][x] = 0; // Default to black if data is unexpectedly missing
                }
            }
        }

        // Create WritableImage for preview from the merged 1-bit data
        WritableImage mergedOutputFxImage = new WritableImage(mergedWidth, mergedHeight);
        PixelWriter pixelWriter = mergedOutputFxImage.getPixelWriter();
        for (int y = 0; y < mergedHeight; y++) {
            for (int x = 0; x < mergedWidth; x++) {
                Color finalColor = (currentMerged1BitData[y][x] == 1) ? Color.WHITE : Color.BLACK;
                pixelWriter.setColor(x, y, finalColor);
            }
        }

        mergedImageView.setImage(mergedOutputFxImage);
        statusLabel.setText("Images merged. Click 'Save' to convert to 1-bit BMP.");
        saveButton.setVisible(true);
    }

    /**
     * Handles saving the merged image as a 1bpp BMP file.
     */
    @FXML
    private void handleSaveImage() {
        System.out.println(currentMerged1BitData + "currentMerged1BitData");
        if (currentMerged1BitData == null || mergedWidth == 0 || mergedHeight == 0) {
            showAlert("Error", "No merged image to save. Please merge images first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Merged 1-bit BMP");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("BMP Image", "*.bmp"));
        File outputFile = fileChooser.showSaveDialog(primaryStage);

        if (outputFile != null) {
            try {
                BmpImage.write(outputFile, currentMerged1BitData, mergedWidth, mergedHeight);
                statusLabel.setText("BMP saved: " + outputFile.getAbsolutePath());
                System.out.println("BMP saved: " + outputFile.getAbsolutePath());
                verifyBmpHeader(outputFile); // Verify the header of the saved file
            } catch (IOException ex) {
                statusLabel.setText("Error saving BMP: " + ex.getMessage());
                showAlert("Error", "Error saving BMP: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    /**
     * Helper method to open a file chooser dialog.
     * @param title The title for the file chooser dialog.
     * @return The selected File, or null if cancelled.
     */
    private File chooseImageFile(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("BMP Image", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        return fileChooser.showOpenDialog(primaryStage);
    }

    /**
     * Displays an alert dialog.
     * @param title The title of the alert.
     * @param message The message content of the alert.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Verifies the header of a saved BMP file to ensure it's 1bpp.
     * This is a utility for debugging and confirmation.
     * @param bmpFile The BMP file to verify.
     */
    private void verifyBmpHeader(File bmpFile) {
        try {
            byte[] header = new byte[54]; // Standard BMP header size
            try (FileInputStream fis = new FileInputStream(bmpFile)) {
                if (fis.read(header) != 54) {
                    System.err.println("Error: Could not read full BMP header for verification.");
                    return;
                }
            }

            ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);

            String signature = new String(new byte[]{buffer.get(0), buffer.get(1)});
            int fileSize = buffer.getInt(2);
            int pixelDataOffset = buffer.getInt(10);
            int infoHeaderSize = buffer.getInt(14);
            int width = buffer.getInt(18);
            int height = buffer.getInt(22);
            short bitsPerPixel = buffer.getShort(28);
            int compression = buffer.getInt(30);
            int colorsUsed = buffer.getInt(46);

            System.out.println("\n--- BMP Header Verification for: " + bmpFile.getName() + " ---");
            System.out.println("Signature (BF Type): " + signature);
            System.out.println("File Size: " + fileSize + " bytes (Expected: " + bmpFile.length() + ")");
            System.out.println("Pixel Data Offset: " + pixelDataOffset);
            System.out.println("Info Header Size (biSize): " + infoHeaderSize);
            System.out.println("Width (biWidth): " + width);
            System.out.println("Height (biHeight): " + height);
            System.out.println("Bits Per Pixel (biBitCount): " + bitsPerPixel);
            System.out.println("Compression (biCompression): " + compression + " (0 = BI_RGB)");
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
            if (compression != 0) {
                System.err.println("WARNING: BMP is compressed. Expected uncompressed (BI_RGB).");
            }
        } catch (IOException e) {
            System.err.println("Error reading BMP header for verification: " + e.getMessage());
        }
    }

    /**
     * Inner static class to handle reading and writing of 1bpp BMP files.
     */
    private static class BmpImage {
        public int width;
        public int height;
        public byte[] pixelData; // Raw 1bpp pixel data (bottom-up, padded)
        public int paddedRowSize; // Size of a row including padding bytes

        /**
         * Constructor for creating a BmpImage object from raw data.
         * Used internally when reading a file or preparing data for writing.
         * @param width The width of the image in pixels.
         * @param height The height of the image in pixels.
         * @param pixelData The raw 1bpp pixel data.
         */
        public BmpImage(int width, int height, byte[] pixelData) {
            this.width = width;
            this.height = height;
            this.paddedRowSize = ((width * 1 + 31) / 32) * 4; // Calculate padded row size for 1bpp (1 bit per pixel)
            this.pixelData = pixelData;
        }

        /**
         * Reads a 1bpp BMP file from the given File object.
         * @param file The BMP file to read.
         * @return A BmpImage object containing the image data.
         * @throws IOException If there's an error reading the file or if it's not a valid 1bpp BMP.
         */
        public static BmpImage read(File file) throws IOException {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] header = new byte[54]; // Standard BMP header size
                if (fis.read(header) != 54) {
                    throw new IOException("Invalid BMP file: header too short (expected 54 bytes).");
                }

                ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);

                // Verify BMP signature "BM" (0x4D42)
                if (buffer.getShort(0) != 0x4D42) {
                    throw new IOException("Not a valid BMP file (signature 'BM' not found).");
                }

                int dataOffset = buffer.getInt(10); // Offset to pixel data
                int infoHeaderSize = buffer.getInt(14); // Size of info header (expected 40 for BITMAPINFOHEADER)
                int width = buffer.getInt(18);
                int height = buffer.getInt(22);
                short bitsPerPixel = buffer.getShort(28);
                int compression = buffer.getInt(30); // Compression method (0 for BI_RGB)

                if (bitsPerPixel != 1) {
                    throw new IOException("Only 1-bit BMP files are supported. Found: " + bitsPerPixel + " bpp.");
                }
                if (compression != 0) {
                    throw new IOException("Compressed BMP files are not supported (only BI_RGB = 0).");
                }

                // Read color palette (2 entries for 1bpp: Black and White)
                byte[] palette = new byte[8];
                if (fis.read(palette) != 8) {
                    throw new IOException("Invalid BMP file: palette too short (expected 8 bytes for 1bpp).");
                }
                // We expect palette to be 0x00000000 (black) and 0x00FFFFFF (white)

                // Calculate padded row size for 1bpp
                int paddedRowSize = ((width * 1 + 31) / 32) * 4;
                int totalPixelDataSize = paddedRowSize * height;
                byte[] pixelData = new byte[totalPixelDataSize];

                // Seek to pixel data start and read
                fis.getChannel().position(dataOffset);
                if (fis.read(pixelData) != totalPixelDataSize) {
                    throw new IOException("Invalid BMP file: pixel data too short (expected " + totalPixelDataSize + " bytes).");
                }

                return new BmpImage(width, height, pixelData);
            }
        }

        /**
         * Writes 1bpp pixel data to a BMP file.
         * @param file The output BMP file.
         * @param pixelBitData A 2D array of bytes (0 or 1) representing the pixels (top-down).
         * @param width The width of the image.
         * @param height The height of the image.
         * @throws IOException If there's an error writing the file.
         */
        public static void write(File file, byte[][] pixelBitData, int width, int height) throws IOException {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                int paddedRowSize = ((width * 1 + 31) / 32) * 4; // Calculate padded row size for 1bpp
                int pixelDataSize = paddedRowSize * height;
                int fileSize = 14 + 40 + 8 + pixelDataSize; // File Header + Info Header + Palette + Pixel Data

                ByteBuffer buffer = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN);

                // BMP File Header (14 bytes)
                buffer.putShort((short) 0x4D42); // Signature "BM"
                buffer.putInt(fileSize);         // Total file size
                buffer.putInt(0);                // Reserved (set to 0)
                buffer.putInt(14 + 40 + 8);      // Offset to pixel data (File Header + Info Header + Palette)

                // BMP Info Header (40 bytes - BITMAPINFOHEADER)
                buffer.putInt(40);               // Size of info header (40 bytes)
                buffer.putInt(width);            // Image width
                buffer.putInt(height);           // Image height
                buffer.putShort((short) 1);      // Planes (must be 1)
                buffer.putShort((short) 1);      // Bits per pixel (1 for 1bpp)
                buffer.putInt(0);                // Compression method (0 = BI_RGB, no compression)
                buffer.putInt(pixelDataSize);    // Image size (can be 0 for BI_RGB, but good practice to set)
                buffer.putInt(2835);             // X pixels per meter (72 DPI = 2835 ppm)
                buffer.putInt(2835);             // Y pixels per meter (72 DPI = 2835 ppm)
                buffer.putInt(2);                // Colors used (2 for 1bpp: black and white)
                buffer.putInt(0);                // Important colors (0 means all are important)

                // Color Palette (8 bytes for 1bpp: Black and White)
                // Entry 0: Black (0x00000000 BGRA)
                buffer.putInt(0x00000000);
                // Entry 1: White (0x00FFFFFF BGRA)
                buffer.putInt(0x00FFFFFF);

                // Pixel Data (bottom-up, padded)
                // Iterate rows from bottom to top of our pixelBitData array
                for (int y = height - 1; y >= 0; y--) {
                    byte[] rowBytes = new byte[paddedRowSize];
                    for (int x = 0; x < width; x++) {
                        int byteIndex = x / 8;
                        int bitIndex = 7 - (x % 8); // Bits are stored from MSB (leftmost pixel) to LSB (rightmost pixel) within a byte
                        if (pixelBitData[y][x] == 1) { // If pixel is white (1)
                            rowBytes[byteIndex] |= (1 << bitIndex); // Set the corresponding bit
                        }
                        // If pixelBitData[y][x] is 0 (black), the bit remains 0, which is the default for a new byte array
                    }
                    buffer.put(rowBytes); // Write the whole padded row
                }

                fos.write(buffer.array()); // Write the entire buffer to the file
            }
        }

        /**
         * Helper method to extract a specific row's byte data from the raw pixelData array.
         * @param fullPixelData The complete raw pixel data array from a BmpImage.
         * @param rowIndex The 0-based index of the row to extract (in BMP's bottom-up order).
         * @param paddedRowSize The padded size of a single row in bytes.
         * @return A byte array representing the specified row's pixel data.
         */
        public static byte[] getRowBytes(byte[] fullPixelData, int rowIndex, int paddedRowSize) {
            int startByte = rowIndex * paddedRowSize;
            return Arrays.copyOfRange(fullPixelData, startByte, startByte + paddedRowSize);
        }
    }
}
