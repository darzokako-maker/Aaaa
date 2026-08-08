package com.example.roothidremote;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class NetworkHidBackend {
    private Socket socket;
    private BufferedReader in;
    private String lastStatus = "Ağ bağlantısı henüz kurulmadı.";
    private boolean isRunning = false;
    private final ExecutorService listenerThread = Executors.newSingleThreadExecutor();

    // Köprü (Bridge) için diğer backend'lerin referansları
    private RootHidBackend rootBackend;
    private BluetoothHidBackend bluetoothBackend;

    // Hedef olarak hangisinin kullanılacağını seçmek için
    private boolean useRoot = true; 

    void setBackends(RootHidBackend root, BluetoothHidBackend bt) {
        this.rootBackend = root;
        this.bluetoothBackend = bt;
    }

    void setUseRoot(boolean useRoot) {
        this.useRoot = useRoot;
    }

    boolean connect(String ip, int port) {
        if (ip == null || ip.trim().isEmpty() || port <= 0 || port > 65535) {
            lastStatus = "Geçersiz IP veya Port.";
            return false;
        }
        try {
            close(); // Varsa eski bağlantıyı kapat
            socket = new Socket(ip.trim(), port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isRunning = true;
            lastStatus = "PC'ye bağlanıldı. Komutlar bekleniyor...";
            
            // Arka planda PC'den gelen string komutları dinlemeye başla
            startListening();
            return true;
        } catch (Exception e) {
            lastStatus = "Ağ bağlantı hatası: " + e.getMessage();
            return false;
        }
    }

    private void startListening() {
        listenerThread.execute(() -> {
            try {
                String line;
                // PC'den "MOUSE dx dy" veya "CLICK" gibi veriler geldikçe satır satır okur
                while (isRunning && (line = in.readLine()) != null) {
                    processCommandFromPC(line.trim());
                }
            } catch (Exception e) {
                lastStatus = "Bağlantı koptu: " + e.getMessage();
            } finally {
                close();
            }
        });
    }

    // PC'den gelen metin tabanlı komutu işleyip donanıma ileten "Pasif Köprü"
    private void processCommandFromPC(String command) {
        try {
            if (command.startsWith("MOUSE ")) {
                String[] parts = command.split(" ");
                if (parts.length == 3) {
                    int dx = Integer.parseInt(parts[1]);
                    int dy = Integer.parseInt(parts[2]);
                    
                    if (useRoot && rootBackend != null) {
                        rootBackend.moveMouse(dx, dy);
                    } else if (!useRoot && bluetoothBackend != null) {
                        bluetoothBackend.moveMouse(dx, dy);
                    }
                }
            } 
            else if (command.equals("CLICK")) {
                if (useRoot && rootBackend != null) rootBackend.click(1);
                else if (!useRoot && bluetoothBackend != null) bluetoothBackend.click(1);
            }
            else if (command.equals("RCLICK")) {
                if (useRoot && rootBackend != null) rootBackend.click(2);
                else if (!useRoot && bluetoothBackend != null) bluetoothBackend.click(2);
            }
        } catch (Exception ignored) {
            // Hatalı gelen (parse edilemeyen) paketleri yoksay (Aimbot gecikmesin diye)
        }
    }

    boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    String status() {
        return lastStatus;
    }

    void close() {
        isRunning = false;
        try {
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
    }
}
