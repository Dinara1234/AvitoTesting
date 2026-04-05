package org.example.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemResponse {
    private String id;
    @JsonProperty("sellerId")
    private int sellerId;
    private String name;
    private int price;
    private Statistics statistics;
    private String createdAt;
}