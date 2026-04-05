package get;

import base.BaseTest;
import io.qameta.allure.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.example.models.Statistics;
import org.example.responses.post.ErrorResponse;
import org.example.models.CreateItemRequest;
import org.example.models.ItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@Epic("API тестирование Avito")
@Feature("Получение объявления по ID (GET /api/1/item/{id})")
public class GetItemTests extends BaseTest {

    private final int DEFAULT_SELLER_ID = 123456;
    private final String DEFAULT_NAME = "A";
    private final int DEFAULT_PRICE = 1;

    @Step("Отправить POST запрос на /api/1/item с телом: {requestBody}")
    private Response sendPostRequest(Object requestBody) {
        return given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/api/1/item")
                .then()
                .extract().response();
    }

    @Step("Выполнить GET запрос на /api/1/item/{id}")
    private Response sendGetRequest(String id) {
        return given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/1/item/" + id)
                .then()
                .extract().response();
    }

    @Step("Извлечь идентификатор из ответа POST (поддерживает оба формата)")
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

    @Step("Проверить, что GET ответ содержит корректные данные и соответствует созданному объявлению")
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

    @Step("Проверить структуру ответа при ошибке 400")
    private void assertError400Structure(Response response) {
        assertEquals(400, response.statusCode());
        ErrorResponse error = response.as(ErrorResponse.class);
        assertNotNull(error.getResult());
        assertNotNull(error.getResult().getMessage());
        assertNotNull(error.getStatus());
        assertNotNull(error.getResult().getMessages());
    }

    @Step("Проверить структуру ответа при ошибке 404")
    private void assertError404Structure(Response response) {
        assertEquals(404, response.statusCode());
        assertNotNull(response.jsonPath().getString("result"));
        assertNotNull(response.jsonPath().getString("status"));
    }

    @Test
    @DisplayName("TC-GET-ITEM-01: Можно получить ранее созданное объявление по его идентификатору")
    @Description("Создаётся объявление, затем выполняется GET /api/1/item/{id} и проверяется совпадение всех полей")
    void testGetExistingItem() {
        Statistics stats = new Statistics(5, 10, 2);
        CreateItemRequest createRequest = new CreateItemRequest(DEFAULT_SELLER_ID, "GetTest", 777, stats);
        Response createResponse = sendPostRequest(createRequest);
        assertEquals(200, createResponse.statusCode());
        String itemId = extractIdFromResponse(createResponse);
        Response getResponse = sendGetRequest(itemId);
        assertGetSuccessResponse(getResponse, createRequest, itemId);
    }

    @Test
    @DisplayName("TC-GET-ITEM-02: Запрос несуществующего объявления возвращает ошибку 404 с правильной структурой")
    @Description("Используется заведомо несуществующий UUID, ожидается 404 и корректное тело ошибки")
    void testGetNonExistentItem() {
        String fakeId = "00000000-0000-0000-0000-000000000000";
        Response response = sendGetRequest(fakeId);
        assertError404Structure(response);
    }

    @Test
    @DisplayName("TC-GET-ITEM-03: Идентификатор в неверном формате (например, просто строка) вызывает ошибку 400")
    @Description("Передаётся некорректный ID (не UUID), ожидается 400 с валидной структурой ошибки")
    void testGetWithInvalidFormatId() {
        Response response = sendGetRequest("abc");
        assertError400Structure(response);
    }

    @Test
    @DisplayName("TC-GET-ITEM-04: Пустой идентификатор (отсутствие id в URL) приводит к ошибке 400")
    @Description("Запрос без параметра id (GET /api/1/item/) должен вернуть 400")
    void testGetWithEmptyId() {
        Response response = given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/1/item/")
                .then()
                .extract().response();
        assertError400Structure(response);
    }

    @Test
    @DisplayName("TC-GET-ITEM-05: Идентификатор со спецсимволами (например, слэш) должен возвращать 400 или 404 с корректной структурой")
    @Description("Проверка устойчивости к спецсимволам в ID; допускается 400 или 404, но структура должна соответствовать")
    void testGetWithSpecialCharsInId() {
        Response response = sendGetRequest("test/1");
        int statusCode = response.statusCode();
        assertTrue(statusCode == 400 || statusCode == 404,
                "Ожидался код 400 или 404, получен " + statusCode);
        if (statusCode == 400) {
            assertError400Structure(response);
        } else {
            assertError404Structure(response);
        }
    }

    @Test
    @DisplayName("TC-GET-ITEM-06: После удаления объявления попытка его получить должна завершаться ошибкой 404")
    @Description("Создаётся объявление, удаляется через DELETE, затем GET должен вернуть 404")
    void testGetAfterDelete() {
        Statistics stats = new Statistics(1, 1, 1);
        CreateItemRequest createRequest = new CreateItemRequest(DEFAULT_SELLER_ID, "ToDelete", 100, stats);
        Response createResponse = sendPostRequest(createRequest);
        assertEquals(200, createResponse.statusCode());
        String itemId = extractIdFromResponse(createResponse);
        Response deleteResponse = given()
                .when()
                .delete("/api/2/item/" + itemId)
                .then()
                .extract().response();
        assertEquals(200, deleteResponse.statusCode());
        Response getResponse = sendGetRequest(itemId);
        assertError404Structure(getResponse);
    }

    @Test
    @DisplayName("TC-GET-ITEM-07: Поле createdAt должно содержать корректную дату и время создания, близкое к моменту запроса")
    @Description("Проверяется, что createdAt парсится в дату и отстаёт от текущего времени не более чем на 5 секунд")
    void testCreatedAtField() {
        Statistics stats = new Statistics(1, 2, 2);
        CreateItemRequest createRequest = new CreateItemRequest(DEFAULT_SELLER_ID, "DateTest", 123, stats);
        Response createResponse = sendPostRequest(createRequest);
        assertEquals(200, createResponse.statusCode());
        String itemId = extractIdFromResponse(createResponse);
        LocalDateTime beforeGet = LocalDateTime.now();
        Response getResponse = sendGetRequest(itemId);
        assertEquals(200, getResponse.statusCode());
        List<ItemResponse> items = getResponse.jsonPath().getList(".", ItemResponse.class);
        assertNotNull(items);
        assertEquals(1, items.size());
        String createdAtStr = items.get(0).getCreatedAt();
        assertNotNull(createdAtStr);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS Z", Locale.ENGLISH);
        LocalDateTime createdAt;
        try {
            String cleaned = createdAtStr.replaceAll("\\s\\+\\d{4}$", "");
            createdAt = LocalDateTime.parse(cleaned, formatter);
        } catch (Exception e) {
            fail("Не удалось распарсить createdAt: " + createdAtStr, e);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        long secondsBetween = ChronoUnit.SECONDS.between(createdAt, now);
        assertTrue(secondsBetween >= 0 && secondsBetween <= 5,
                String.format("createdAt (%s) отличается от текущего времени (%s) на %d секунд (допуск 5 сек)",
                        createdAt, now, secondsBetween));
    }
}