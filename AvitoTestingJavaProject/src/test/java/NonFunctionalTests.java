package base;

import base.BaseTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.example.models.CreateItemRequest;
import org.example.models.ItemResponse;
import org.example.models.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

public class NonFunctionalTests extends BaseTest {

    private static final String XSS_PAYLOAD = "<script>alert(1)</script>";
    private static final String SQL_INJECTION_PAYLOAD = "'; DROP TABLE items; --";

    private Response sendGetRequest(String id) {
        return given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/1/item/" + id)
                .then()
                .extract().response();
    }

    private String extractIdFromResponse(Response response) {
        String id = response.jsonPath().getString("id");
        if (id != null && !id.isEmpty()) {
            return id;
        }
        String status = response.jsonPath().getString("status");
        if (status != null && status.startsWith("Сохранили объявление - ")) {
            return status.replace("Сохранили объявление - ", "");
        }
        fail("Не удалось извлечь ID из ответа: " + response.asString());
        return null;
    }

    private void assertGetSuccessResponse(Response response, CreateItemRequest expectedRequest, String expectedId) {
        assertEquals(200, response.statusCode());
        List<ItemResponse> items = response.jsonPath().getList(".", ItemResponse.class);
        assertNotNull(items);
        assertEquals(1, items.size());
        ItemResponse item = items.get(0);
        assertEquals(expectedId, item.getId());
        assertEquals(expectedRequest.getSellerId(), item.getSellerId());
        assertEquals(expectedRequest.getName(), item.getName());
        assertEquals(expectedRequest.getPrice(), item.getPrice());
        assertNotNull(item.getStatistics());
        assertEquals(expectedRequest.getStatistics().getLikes(), item.getStatistics().getLikes());
        assertEquals(expectedRequest.getStatistics().getViewCount(), item.getStatistics().getViewCount());
        assertEquals(expectedRequest.getStatistics().getContacts(), item.getStatistics().getContacts());
        assertNotNull(item.getCreatedAt());
        assertTrue(item.getCreatedAt().matches(".*\\d{4}-\\d{2}-\\d{2}.*"),
                "createdAt не содержит дату: " + item.getCreatedAt());
    }

    @Test
    @DisplayName("NF-03: SQL-инъекция в поле name – сервер должен экранировать данные и вернуть 200")
    void testSqlInjection() {
        Statistics stats = new Statistics(1, 1, 1);
        CreateItemRequest request = new CreateItemRequest(123456, SQL_INJECTION_PAYLOAD, 100, stats);

        Response createResponse = given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/1/item");

        assertEquals(200, createResponse.statusCode(), "Сервер не принял объявление с потенциально опасным name");
        String itemId = extractIdFromResponse(createResponse);
        Response getResponse = sendGetRequest(itemId);
        assertGetSuccessResponse(getResponse, request, itemId);
    }

    @Test
    @DisplayName("NF-04: XSS-атака через name – сервер должен экранировать вывод, браузер не должен исполнять скрипт")
    void testXssInjection() {
        Statistics stats = new Statistics(1, 1, 1);
        CreateItemRequest request = new CreateItemRequest(123456, XSS_PAYLOAD, 100, stats);

        Response createResponse = given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/1/item");
        assertEquals(200, createResponse.statusCode());
        String itemId = extractIdFromResponse(createResponse);

        Response getResponse = given()
                .accept(ContentType.JSON)
                .get("/api/1/item/" + itemId);
        assertEquals(200, getResponse.statusCode());

        String responseBody = getResponse.asString();
        assertFalse(responseBody.contains("<script>"), "Ответ содержит непроэкранированный тег <script>");
        String returnedName = getResponse.jsonPath().getString("[0].name");
        assertEquals(XSS_PAYLOAD, returnedName, "Имя должно сохраниться в исходном виде");
    }
}