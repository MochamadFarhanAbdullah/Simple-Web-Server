package id.farhan.simplewebserver;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

public class App extends Application {
    // Config server
    private int port;
    private String webDirectory;
    private String logDirectory;
    private boolean serverRunning = false;

    private TextArea logTextArea;

    // Jalankan aplikasi javafx
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    // Method utama yang membuat UI aplikasi. Load config awal, tentukan judul utama aplikasi
    public void start(Stage primaryStage) {
        loadConfig();

        primaryStage.setTitle("Simple Web Server");

        // Configuration Panel/Component UI
        Label portLabel = new Label("Port:");
        TextField portField = new TextField();
        portField.setText(String.valueOf(port));

        Label webDirLabel = new Label("Web Directory:");
        TextField webDirField = new TextField();
        webDirField.setText(webDirectory);

        Button webDirButton = new Button("Choose...");
        webDirButton.setOnAction(e -> chooseDirectory("Choose Web Directory", webDirField));

        Label logDirLabel = new Label("Log Directory:");
        TextField logDirField = new TextField();
        logDirField.setText(logDirectory);

        Button logDirButton = new Button("Choose...");
        logDirButton.setOnAction(e -> chooseDirectory("Choose Log Directory", logDirField));

        Button startButton = new Button("Start");
        Button stopButton = new Button("Stop");
        stopButton.setDisable(true);

        // Menata config panel pake GridPane
        GridPane configGrid = new GridPane();
        configGrid.setHgap(10);
        configGrid.setVgap(5);
        configGrid.setPadding(new Insets(10));
        configGrid.add(portLabel, 0, 0);
        configGrid.add(portField, 1, 0);
        configGrid.add(webDirLabel, 0, 1);
        configGrid.add(webDirField, 1, 1);
        configGrid.add(webDirButton, 2, 1);
        configGrid.add(logDirLabel, 0, 2);
        configGrid.add(logDirField, 1, 2);
        configGrid.add(logDirButton, 2, 2);
        configGrid.add(startButton, 0, 3);
        configGrid.add(stopButton, 1, 3);

        // Log Panel
        logTextArea = new TextArea();
        logTextArea.setEditable(false);
        VBox.setVgrow(logTextArea, Priority.ALWAYS);

        VBox logLayout = new VBox(10);
        logLayout.getChildren().addAll(new Label("Log:"), logTextArea);

        // Main Layout
        // Semua panel digabung dalam satu main layout menggunakan VBox
        VBox mainLayout = new VBox(10);
        mainLayout.getChildren().addAll(configGrid, logLayout);

        // Event handling untuk tombol "Start"
        startButton.setOnAction(event -> {
            try {
                port = Integer.parseInt(portField.getText());
                webDirectory = webDirField.getText();
                logDirectory = logDirField.getText();

                saveConfig();
                startServer();
                startButton.setDisable(true);
                stopButton.setDisable(false);
            } catch (NumberFormatException e) {
                showAlert("Invalid port number!");
            } catch (IOException e) {
                showAlert("Failed to start server: " + e.getMessage());
            }
        });

        stopButton.setOnAction(event -> {
            stopServer();
            startButton.setDisable(false);
            stopButton.setDisable(true);
        });

        // Saat aplikasi ditutup dan jika server masih berjalan, server dihentikan sebelum keluar dari aplikasi
        primaryStage.setOnCloseRequest(event -> {
            if (serverRunning) {
                stopServer();
            }
            Platform.exit();
        });

        // Membuat scene dan menampilkannya di primaryStage
        Scene scene = new Scene(mainLayout, 600, 400);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Memuat config server dari file config.properties, klo tdk ketemu filenya maka pake default
    private void loadConfig() {
        try (InputStream input = new FileInputStream("config.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            port = Integer.parseInt(prop.getProperty("port", "8080"));
            webDirectory = prop.getProperty("webDirectory", "D:/Web/Files");
            logDirectory = prop.getProperty("logDirectory", "D:/Web/Logs");
        } catch (IOException e) {
            log("Error loading config: " + e.getMessage());
            // Use default values if config file doesn't exist
            port = 8080;
            webDirectory = "D:/Web/Files";
            logDirectory = "D:/Web/Logs";
        }
    }

    // Menyimpan config server pada file config.properties
    private void saveConfig() throws IOException {
        Properties prop = new Properties();
        prop.setProperty("port", String.valueOf(port));
        prop.setProperty("webDirectory", webDirectory);
        prop.setProperty("logDirectory", logDirectory);
        try (OutputStream output = new FileOutputStream("config.properties")) {
            prop.store(output, null);
        }
    }

    // Untuk memulai server. Server dijalankan dalam thread terpisah agar antarmuka pengguna tetap responsif (nerima koneksi tnp blokir UI)
    private void startServer() throws IOException {
        serverRunning = true;
        new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(port);
                log("Server started on port " + port);

                while (serverRunning) {
                    Socket clientSocket = serverSocket.accept();
                    handleRequest(clientSocket);
                }

                serverSocket.close();
            } catch (IOException e) {
                log("Error: " + e.getMessage());
            }
        }).start();
    }

    private void stopServer() {
        serverRunning = false;
    }

    // Menangani permintaan yang masuk dari client. Membaca permintaan, memprosesnya, dan memberikan respons yang sesuai
    private void handleRequest(Socket clientSocket) {
        try {
            // Tampilkan IP Address WiFi
            log("Connection from: " + getWifiIpAddress());

            /*
            // Tampilkan IP Address utama yg digunakan OS (ethernet 3)
            InetAddress localhost = InetAddress.getLocalHost();
            log("Connection from: " + localhost.getHostAddress());
            */

            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); // baca req client
            OutputStream out = clientSocket.getOutputStream(); // memberi balasan ke client

            String request = in.readLine();
            log("Request: " + request);

            if (request != null) {
                String[] requestParts = request.split(" ");
                String method = requestParts[0];
                String url = requestParts[1];

                /*
                //setting root ke index.html
                if (url.equals("/")) {
                    // Jika URL adalah root, tampilkan index.html
                    url = "/index.html";
                }
                */

                String filePath = webDirectory + url.replace("/", File.separator);
                File file = new File(filePath);

                if (method.equals("GET")) {
                    if (file.exists()) {
                        if (file.isDirectory()) {
                            // Jika yang diminta adalah direktori, tampilkan listing
                            sendDirectoryListing(out, url, file);
                        } else {
                            // Jika file ditemukan, kirim file tersebut
                            sendFile(out, file);
                        }
                    } else {
                        // Jika file tidak ditemukan, kirim respons error
                        sendErrorResponse(out);
                    }
                }
            }

            out.flush();
            out.close();
            in.close();
            clientSocket.close();
        } catch (IOException e) {
            log("Error: " + e.getMessage());
        }
    }

    // Mengirim daftar file dalam sebuah direktori sebagai respons HTTP
    private void sendDirectoryListing(OutputStream out, String url, File directory) throws IOException {
        File[] files = directory.listFiles();
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 200 OK\r\n\r\n");
        response.append("<html><head><link rel=\"stylesheet\" type=\"text/css\" href=\"/style.css\"></head><body><h1>Index of ").append(url).append("</h1><ul>");
        for (File f : files) {
            String fileUrl = url.equals("/") ? url + f.getName() : url + "/" + f.getName();
            response.append("<li><a href=\"").append(fileUrl).append("\">").append(f.getName()).append("</a></li>");
        }
        response.append("</ul></body></html>");
        out.write(response.toString().getBytes());
    }

    // Untuk mengirim file sebagai respons HTTP
    private void sendFile(OutputStream out, File file) throws IOException {
        String contentType = Files.probeContentType(file.toPath());
        byte[] data = Files.readAllBytes(file.toPath());
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes());
        out.write(data);
    }

    // Untuk mengirim respons error jika file tidak ditemukan (404)
    private void sendErrorResponse(OutputStream out) throws IOException {
        String errorResponse = "HTTP/1.1 404 Not Found\r\n\r\n<h1>404 Not Found</h1>";
        out.write(errorResponse.getBytes());
    }

    // Mencatat pesan ke dalam textarea dan menyimpannya ke dalam file log
    private void log(String message) {
        String logMessage = "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] " + message + "\n";
        logTextArea.appendText(logMessage);

        // Save log to file
        try {
            String logFileName = new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log";
            Path logPath = Paths.get(logDirectory, logFileName);
            Files.createDirectories(logPath.getParent());
            Files.write(logPath, logMessage.getBytes(), Files.exists(logPath) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException e) {
            log("Error saving log: " + e.getMessage());
        }
    }

    // Menampilkan eror di GUI
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Memilih directory menggunakan jendela dialog
    private void chooseDirectory(String title, TextField textField) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);
        File selectedDirectory = directoryChooser.showDialog(null);
        if (selectedDirectory != null) {
            textField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    // Mengambil alamat IP dari antarmuka Wi-Fi
    private InetAddress getWifiIpAddress() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isUp() && !iface.isLoopback() && !iface.isVirtual() && iface.getName().startsWith("w")) { // Menggunakan antarmuka Wi-Fi yang diawali dengan "w"
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) { // Hanya ambil alamat IPv4
                        return addr;
                    }
                }
            }
        }
        return null;
    }
}
