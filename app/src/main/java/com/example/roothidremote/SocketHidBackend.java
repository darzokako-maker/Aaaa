package com.example.roothidremote;

import android.util.Base64;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class SocketHidBackend {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int WRITE_TIMEOUT_MS = 5000;

    private Socket socket;
    private BufferedWriter writer;
    private String lastStatus = "Socket bağlantısı yok.";

    synchronized boolean connect(String host, String portText) {
        String cleanHost = host == null ? "" : host.trim();
        int port = parsePort(portText);
        if (cleanHost.isEmpty()) {
            lastStatus = "PC IP/host adresi boş.";
            return false;
        }
        if (port <= 0) return false;
        close();
        try {
            Socket newSocket = new Socket();
            newSocket.connect(new InetSocketAddress(cleanHost, port), CONNECT_TIMEOUT_MS);
            newSocket.setTcpNoDelay(true);
            newSocket.setSoTimeout(WRITE_TIMEOUT_MS);
            socket = newSocket;
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            lastStatus = "Socket bağlı: " + cleanHost + ":" + port;
            return true;
        } catch (IOException e) {
            lastStatus = "Socket bağlantı hatası: " + e.getMessage();
            close();
            return false;
        }
    }

    synchronized boolean sendText(String text) {
        String value = text == null ? "" : text;
        String encoded = Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return sendLine("TEXT " + encoded, "Socket klavye metni gönderildi.");
    }

    synchronized boolean moveMouse(int dx, int dy) {
        return sendLine(String.format(Locale.US, "MOUSE %d %d", dx, dy), "Socket mouse hareketi gönderildi.");
    }

    synchronized boolean click(int buttonMask) {
        return sendLine("CLICK " + buttonMask, "Socket mouse tıklaması gönderildi.");
    }

    synchronized void close() {
        if (writer != null) {
            try { writer.close(); } catch (IOException ignored) {}
        }
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
        }
        writer = null;
        socket = null;
    }

    synchronized String status() {
        return lastStatus;
    }

    private int parsePort(String portText) {
        try {
            int port = Integer.parseInt(portText == null ? "" : portText.trim());
            if (port < 1 || port > 65535) {
                lastStatus = "Port 1-65535 arasında olmalı.";
                return -1;
            }
            return port;
        } catch (NumberFormatException e) {
            lastStatus = "Geçersiz port. Örnek: 5050";
            return -1;
        }
    }

    private boolean sendLine(String line, String successStatus) {
        if (writer == null || socket == null || socket.isClosed() || !socket.isConnected()) {
            lastStatus = "Socket bağlı değil. Önce PC IP ve port ile bağlanın.";
            return false;
        }
        try {
            writer.write(line);
            writer.write('\n');
            writer.flush();
            lastStatus = successStatus;
            return true;
        } catch (IOException e) {
            lastStatus = "Socket gönderim hatası: " + e.getMessage();
            close();
            return false;
        }
    }
}
