package com.fanghu.platform.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VirusScanService {
    private static final int CHUNK_SIZE = 8192;
    private final boolean enabled;
    private final String host;
    private final int port;
    private final int timeoutMillis;

    public VirusScanService(@Value("${fanghu.clamav.enabled}") boolean enabled,
                            @Value("${fanghu.clamav.host}") String host,
                            @Value("${fanghu.clamav.port}") int port,
                            @Value("${fanghu.clamav.timeout-millis:30000}") int timeoutMillis) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
    }

    public ScanResult scan(Path path) {
        if (!enabled) {
            return new ScanResult(ScanStatus.SKIPPED, "病毒扫描已停用");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            try (InputStream input = new BufferedInputStream(Files.newInputStream(path));
                 OutputStream output = socket.getOutputStream()) {
                output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                byte[] chunk = new byte[CHUNK_SIZE];
                int length;
                while ((length = input.read(chunk)) >= 0) {
                    if (length == 0) continue;
                    output.write(ByteBuffer.allocate(4).putInt(length).array());
                    output.write(chunk, 0, length);
                }
                output.write(new byte[4]);
                output.flush();
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (response.endsWith("OK")) return new ScanResult(ScanStatus.PASSED, response);
                if (response.contains("FOUND")) return new ScanResult(ScanStatus.INFECTED, response);
                return new ScanResult(ScanStatus.ERROR, response);
            }
        } catch (IOException e) {
            throw new IllegalStateException("病毒扫描服务不可用，文件未保存", e);
        }
    }

    public enum ScanStatus { PASSED, INFECTED, ERROR, SKIPPED }
    public record ScanResult(ScanStatus status, String detail) { }
}
