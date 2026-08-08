package com.example.roothidremote;

import java.io.OutputStream;
import java.net.Socket;

final class NetworkHidBackend {
    private Socket socket;
    private OutputStream out;
    private String lastStatus = "Ağ bağlantısı henüz kurulmadı.";

    boolean connect(String ip, int port) {
        if (ip == null || ip.trim().isEmpty() || port <= 0 || port > 65535) {
            lastStatus = "Geçersiz IP adresi veya Port numarası.";
            return false;
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            socket = new Socket(ip.trim(), port);
            out = socket.getOutputStream();
            lastStatus = "Ağ bağlantısı başarılı: " + ip + ":" + port;
            return true;
        } catch (Exception e) {
            lastStatus = "Ağ bağlantı hatası: " + e.getMessage();
            return false;
        }
    }

    boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    String status() {
        return lastStatus;
    }

    boolean sendText(String text) {
        if (!isConnected()) return false;
        if (text == null) text = "";
        try {
            for (char c : text.toCharArray()) {
                sendReport(HidReport.keyboard(c, true));
                sleepBetweenReports();
                sendReport(HidReport.keyboard(c, false));
                sleepBetweenReports();
            }
            return true;
        } catch (Exception e) {
            lastStatus = "Ağ metin gönderim hatası: " + e.getMessage();
            return false;
        }
    }

    boolean sendKey(int keyCode, int modifier) {
        if (!isConnected()) return false;
        try {
            sendReport(HidReport.keyReport(keyCode, modifier, true));
            sleepBetweenReports();
            sendReport(HidReport.keyReport(keyCode, modifier, false));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean moveMouse(int dx, int dy) {
        if (!isConnected()) return false;
        if (dx == 0 && dy == 0) return true;
        try {
            sendReport(HidReport.mouse(0, dx, dy, 0));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean click(int buttonMask) {
        if (!isConnected()) return false;
        try {
            sendReport(HidReport.mouse(buttonMask, 0, 0, 0));
            sleepBetweenReports();
            sendReport(HidReport.mouse(0, 0, 0, 0));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    void close() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
    }

    private void sendReport(byte[] report) throws Exception {
        if (out != null) {
            out.write(report);
            out.flush();
        }
    }

    private void sleepBetweenReports() {
        try { Thread.sleep(8); } catch (InterruptedException ignored) {}
    }
}
