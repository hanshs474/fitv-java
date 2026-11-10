package io.github.hanshs474.freeimagetovideo;

/** The only exception this library throws on purpose. Check {@link #kind()} to decide what to do next. */
public final class FreeImageToVideoException extends RuntimeException {
    /** Why a call failed, split by what to do next. */
    public enum Kind {
        /** No API key, or the key is invalid or deleted. */
        AUTH,
        /** Not enough credits for this run. */
        QUOTA,
        /** API runs need a paid plan. */
        PLAN,
        /** Too many runs generating at once on this account. */
        BUSY,
        /** The content filter refused the prompt, or a cooldown is active. */
        REJECTED,
        /** The run failed or was canceled; {@link #errorMessage()} says why when known. */
        GENERATION,
        /** Still generating when the deadline passed; check the run later. */
        TIMEOUT,
        /** Any other failed request, or a transport failure. */
        API
    }

    private static final long serialVersionUID = 1L;

    private final Kind kind;
    private final int httpStatus;
    private final int code;
    private final String reason;
    private final String taskId;
    private final String errorMessage;
    private final String failureKind;

    FreeImageToVideoException(Kind kind, String message) {
        this(kind, message, 0, 0, null, null, null, null);
    }

    FreeImageToVideoException(Kind kind, String message, int httpStatus, int code, String reason,
                              String taskId, String errorMessage, String failureKind) {
        super(message);
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.code = code;
        this.reason = reason;
        this.taskId = taskId;
        this.errorMessage = errorMessage;
        this.failureKind = failureKind;
    }

    /** What went wrong. */
    public Kind kind() {
        return kind;
    }

    /** The HTTP status the service answered with, or 0 when there was no response. */
    public int httpStatus() {
        return httpStatus;
    }

    /** The envelope {@code code}: 0 on success, -1, -2 or -3 on a refusal. */
    public int code() {
        return code;
    }

    /** The envelope's {@code data.reason}, e.g. {@code insufficient_credits}, or null. */
    public String reason() {
        return reason;
    }

    /** The run this error belongs to, or null. */
    public String taskId() {
        return taskId;
    }

    /** For {@link Kind#GENERATION}: the run's {@code errorMessage}, or null. */
    public String errorMessage() {
        return errorMessage;
    }

    /** For {@link Kind#GENERATION}: the run's {@code failureKind}, or null. */
    public String failureKind() {
        return failureKind;
    }
}
