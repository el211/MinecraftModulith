package dev.oreo.modulith.core;

public class ModulithException extends RuntimeException {
    public ModulithException(String message) {
        super(message);
    }

    public ModulithException(String message, Throwable cause) {
        super(message, cause);
    }
}
