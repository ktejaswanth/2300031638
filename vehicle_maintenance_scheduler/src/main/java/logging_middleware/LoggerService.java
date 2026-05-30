package logging_middleware;

public interface LoggerService {
    void log(String stack, String level, String packageName, String message);
}
