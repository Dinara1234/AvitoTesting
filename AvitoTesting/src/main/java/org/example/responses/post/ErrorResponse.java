package org.example.responses.post;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
public class ErrorResponse {
    private Result result;
    private String status;

    @Data
    @NoArgsConstructor
    public static class Result {
        private Map<String, String> messages;
        private String message;
    }
}