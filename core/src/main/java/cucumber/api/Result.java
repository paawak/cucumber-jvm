package cucumber.api;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Result {
    @SuppressWarnings("unused")
	private static final long serialVersionUID = 2L;

    private final Result.Type status;
    private final Long duration;
    private final Throwable error;
    private final List<String> snippets;
    private final Optional<Object> returnValue; 
    
    public static final Result SKIPPED = new Result(Result.Type.SKIPPED, null, null);
    public static enum Type {
        PASSED,
        SKIPPED,
        PENDING,
        UNDEFINED,
        FAILED;

        public static Type fromLowerCaseName(String lowerCaseName) {
            return valueOf(lowerCaseName.toUpperCase());
        }

        public String lowerCaseName() {
            return name().toLowerCase();
        }

        public String firstLetterCapitalizedName() {
            return name().substring(0, 1) + name().substring(1).toLowerCase();
        }


    }
    
    /**
     * Used at runtime
     *
     * @param status
     * @param duration
     * @param error
     */
    public Result(Result.Type status, Long duration, Throwable error) {
        this(status, duration, error, Collections.<String>emptyList(), Optional.empty());
    }    

    /**
     * Used at runtime
     *
     * @param status
     * @param duration
     * @param error
     * @param snippets
     */
    public Result(Result.Type status, Long duration, Throwable error, List<String> snippets, Optional<Object> returnValue) {
        this.status = status;
        this.duration = duration;
        this.error = error;
        this.snippets = snippets;
        this.returnValue = returnValue;
    }

    public Result.Type getStatus() {
        return status;
    }

    public Long getDuration() {
        return duration;
    }

    public String getErrorMessage() {
        return error != null ? getErrorMessage(error) : null;
    }

    public Throwable getError() {
        return error;
    }

    public List<String> getSnippets() {
        return snippets;
    }

    public boolean is(Result.Type status) {
        return this.status == status;
    }

    public boolean isOk(boolean isStrict) {
        return hasAlwaysOkStatus() || !isStrict && hasOkWhenNotStrictStatus();
    }
    
    public Optional<Object> getReturnValue() {
    	return returnValue;
    }

    private boolean hasAlwaysOkStatus() {
        return is(Result.Type.PASSED) || is(Result.Type.SKIPPED);
    }

    private boolean hasOkWhenNotStrictStatus() {
        return is(Result.Type.UNDEFINED) || is(Result.Type.PENDING);
    }

    private String getErrorMessage(Throwable error) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        error.printStackTrace(printWriter);
        return stringWriter.getBuffer().toString();
    }
    
}