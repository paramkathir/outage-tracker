package io.github.paramkathir.outagetracker.service;

/** Domain exceptions mapped to HTTP status codes by the global exception handler. */
public final class Exceptions {

    private Exceptions() {
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class DuplicateReportException extends RuntimeException {
        public DuplicateReportException(String message) {
            super(message);
        }
    }

    public static class InvalidStateException extends RuntimeException {
        public InvalidStateException(String message) {
            super(message);
        }
    }
}
