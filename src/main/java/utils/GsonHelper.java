package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class to provide a pre-configured Gson instance
 * with support for LocalDateTime serialization (Java 11+ module system compatibility)
 */
public class GsonHelper {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class,
                    (com.google.gson.JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) ->
                            new com.google.gson.JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .create();

    private GsonHelper() {
    }

    /**
     * Get the pre-configured Gson instance
     */
    public static Gson getInstance() {
        return GSON;
    }
}
