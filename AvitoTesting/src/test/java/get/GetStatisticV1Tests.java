package get;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.example.models.CreateItemRequest;
import org.example.models.Statistics;
import org.example.responses.post.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import base.BaseTest;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

public class GetStatisticV1Tests extends BaseTest {

    private final int DEFAULT_SELLER_ID = 123456;
    private final String DEFAULT_NAME = "StatTest";
    private final int DEFAULT_PRICE = 500;

    private Response sendPostRequest(Object requestBody) {
        return given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/api/1/item")
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

    private Response sendGetStatisticRequest(String id) {
        return given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/1/statistic/" + id)
                .then()
                .extract().response();
    }

    private void assertError400Structure(Response response) {
        assertEquals(400, response.statusCode());
        ErrorResponse error = response.as(ErrorResponse.class);
        assertNotNull(error.getResult());
        assertNotNull(error.getResult().getMessage());
        assertNotNull(error.getStatus());
        assertNotNull(error.getResult().getMessages());
    }

    private void assertError404Structure(Response response) {
        assertEquals(404, response.statusCode());
        assertNotNull(response.jsonPath().getString("result"));
        assertNotNull(response.jsonPath().getString("status"));
    }

    @Test
    @DisplayName("TC-STAT-V1-01: Можно получить статистику для существующего объявления — она совпадает с переданной при создании")
    void testGetStatisticForExistingItem() {
        Statistics expectedStats = new Statistics(42, 100, 7);
        CreateItemRequest createRequest = new CreateItemRequest(
                DEFAULT_SELLER_ID, DEFAULT_NAME, DEFAULT_PRICE, expectedStats
        );
        Response createResponse = sendPostRequest(createRequest);
        assertEquals(200, createResponse.statusCode());
        String itemId = extractIdFromResponse(createResponse);
        Response getResponse = sendGetStatisticRequest(itemId);
        assertEquals(200, getResponse.statusCode());
        List<Statistics> statisticsList = getResponse.jsonPath().getList(".", Statistics.class);
        assertNotNull(statisticsList);
        assertEquals(1, statisticsList.size(), "Должен вернуться массив с одним объектом статистики");
        Statistics actualStats = statisticsList.get(0);
        assertEquals(expectedStats.getLikes(), actualStats.getLikes());
        assertEquals(expectedStats.getViewCount(), actualStats.getViewCount());
        assertEquals(expectedStats.getContacts(), actualStats.getContacts());
    }

    @Test
    @DisplayName("TC-STAT-V1-02: Запрос статистики для несуществующего идентификатора возвращает ошибку 404")
    void testGetStatisticForNonExistentId() {
        String fakeId = "00000000-0000-0000-0000-000000000000";
        Response response = sendGetStatisticRequest(fakeId);
        assertError404Structure(response);
    }

    @Test
    @DisplayName("TC-STAT-V1-03: Идентификатор в неверном формате (строка) вызывает ошибку 400")
    void testGetStatisticWithInvalidFormatId() {
        Response response = sendGetStatisticRequest("invalid");
        assertError400Structure(response);
    }

    @Test
    @DisplayName("TC-STAT-V1-04: После удаления объявления статистика по его идентификатору больше не доступна (ошибка 404)")
    void testGetStatisticAfterDelete() {
        Statistics stats = new Statistics(1, 2, 3);
        CreateItemRequest createRequest = new CreateItemRequest(
                DEFAULT_SELLER_ID, "ToDeleteStat", 777, stats
        );
        Response createResponse = sendPostRequest(createRequest);
        assertEquals(200, createResponse.statusCode());
        String itemId = extractIdFromResponse(createResponse);
        Response deleteResponse = given()
                .when()
                .delete("/api/2/item/" + itemId)
                .then()
                .extract().response();
        assertTrue(deleteResponse.statusCode() == 200 || deleteResponse.statusCode() == 404,
                "Удаление не выполнено, код: " + deleteResponse.statusCode());
        Response getResponse = sendGetStatisticRequest(itemId);
        assertError404Structure(getResponse);
    }
}