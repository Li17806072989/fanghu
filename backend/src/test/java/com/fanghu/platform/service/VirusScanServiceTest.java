package com.fanghu.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VirusScanServiceTest {
    @Test
    void reportsCleanClamAvResponse() throws Exception {
        assertScanResult("stream: OK\0", VirusScanService.ScanStatus.PASSED);
    }

    @Test
    void reportsEicarAsInfected() throws Exception {
        assertScanResult("stream: Win.Test.EICAR_HDB-1 FOUND\0", VirusScanService.ScanStatus.INFECTED);
    }

    @Test
    void refusesToMarkFilesPassedWhenScannerIsDisabled() throws Exception {
        Path file = Files.createTempFile("virus-scan-disabled-", ".txt");
        try {
            Files.writeString(file, "content");
            VirusScanService service = new VirusScanService(false, "127.0.0.1", 3310, 1000);
            assertEquals(VirusScanService.ScanStatus.SKIPPED, service.scan(file).status());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private void assertScanResult(String response, VirusScanService.ScanStatus expected) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread clam = new Thread(() -> serveScan(server, response));
            clam.start();
            Path file = Files.createTempFile("virus-scan-", ".txt");
            try {
                Files.writeString(file, "test file");
                VirusScanService service = new VirusScanService(true, "127.0.0.1", server.getLocalPort(), 2000);
                assertEquals(expected, service.scan(file).status());
                clam.join(2000);
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

    private void serveScan(ServerSocket server, String response) {
        try (Socket socket = server.accept(); InputStream input = socket.getInputStream()) {
            input.readNBytes("zINSTREAM\0".length());
            while (true) {
                byte[] lengthBytes = input.readNBytes(4);
                int length = ByteBuffer.wrap(lengthBytes).getInt();
                if (length == 0) break;
                input.readNBytes(length);
            }
            socket.getOutputStream().write(response.getBytes());
            socket.getOutputStream().flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
