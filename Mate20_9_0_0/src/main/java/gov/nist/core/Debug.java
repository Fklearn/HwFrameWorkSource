package gov.nist.core;

public class Debug {
    public static boolean debug = false;
    public static boolean parserDebug = false;
    static StackLogger stackLogger;

    public static void setStackLogger(StackLogger stackLogger) {
        stackLogger = stackLogger;
    }

    public static void println(String s) {
        if ((parserDebug || debug) && stackLogger != null) {
            StackLogger stackLogger = stackLogger;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(s);
            stringBuilder.append(Separators.RETURN);
            stackLogger.logDebug(stringBuilder.toString());
        }
    }

    public static void printStackTrace(Exception ex) {
        if ((parserDebug || debug) && stackLogger != null) {
            stackLogger.logError("Stack Trace", ex);
        }
    }

    public static void logError(String message, Exception ex) {
        if ((parserDebug || debug) && stackLogger != null) {
            stackLogger.logError(message, ex);
        }
    }
}
