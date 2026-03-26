package com.huq.idea.flow.util;

/**
 * Exception thrown when PlantUML rendering fails
 *
 * @author huqiang
 * @since 2024/8/10
 */
public class PlantUmlRenderException extends Exception {
    private final int exitCode;
    private final String errorOutput;

    public PlantUmlRenderException(String message) {
        super(message);
        this.exitCode = -1;
        this.errorOutput = "";
    }

    public PlantUmlRenderException(String message, Throwable cause) {
        super(message, cause);
        this.exitCode = -1;
        this.errorOutput = "";
    }

    public PlantUmlRenderException(String message, int exitCode, String errorOutput) {
        super(message + (exitCode != -1 ? " (Exit code: " + exitCode + ")" : ""));
        this.exitCode = exitCode;
        this.errorOutput = errorOutput;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getErrorOutput() {
        return errorOutput;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        if (errorOutput != null && !errorOutput.isEmpty()) {
            sb.append("\nError output:\n").append(errorOutput);
        }
        return sb.toString();
    }
}
