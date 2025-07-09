module com.example.bmpfile_merger {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;


    opens com.example.bmpfile_merger to javafx.fxml;
    exports com.example.bmpfile_merger;
}