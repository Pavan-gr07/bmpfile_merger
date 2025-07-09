package com.example.bmpfile_merger;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class BMPMerger {
    @FXML
    private Label welcomeText;

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
    }
}