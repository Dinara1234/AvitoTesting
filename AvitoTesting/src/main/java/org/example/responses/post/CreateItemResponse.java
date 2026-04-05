package org.example.responses.post;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.models.Statistics;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateItemResponse {
    private String id;
    @JsonProperty("sellerId")
    private int sellerId;
    private String name;
    private int price;
    private Statistics statistics;
    private String createdAt;
}