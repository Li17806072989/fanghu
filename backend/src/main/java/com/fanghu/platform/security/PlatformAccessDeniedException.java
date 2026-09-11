package com.fanghu.platform.security;

public class PlatformAccessDeniedException extends RuntimeException {
    public PlatformAccessDeniedException(String message) {
        super(message);
    }
}
