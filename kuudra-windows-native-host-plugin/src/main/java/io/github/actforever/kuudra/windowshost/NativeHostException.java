package io.github.actforever.kuudra.windowshost;

public final class NativeHostException extends RuntimeException {
    private final String code;
    public NativeHostException(String code, String message) { super(message); this.code = code; }
    public NativeHostException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String code() { return code; }
}
