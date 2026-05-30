package logging_middleware;

/**
 * Reusable Logging Middleware Interface
 */
public interface LoggerService {
    void log(String stack, String level, String packageName, String message);
}

