package org.manci

class WarningException extends RuntimeException {
    /**
     * Constructs a new WarningException with the specified detail message.
     *
     * @param message the detail message explaining the reason for the warning
     */
    WarningException(String message) {
        super(message)
    }

    /**
     * Constructs a new WarningException with the specified detail message and cause.
     *
     * @param message the detail message explaining the reason for the warning
     * @param cause   the underlying cause of the warning
     */
    WarningException(String message, Throwable cause) {
        super(message, cause)
    }
}
