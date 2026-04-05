package post;

import io.qameta.allure.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.example.models.Statistics;
import org.example.responses.post.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import base.BaseTest;
import org.example.models.CreateItemRequest;
import org.example.responses.post.CreateItemResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@Epic("API тестирование Avito")
@Feature("Создание объявления (POST /api/1/item)")
public class CreateItemTests extends BaseTest {

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

    @Step("Проверить, что ответ имеет статус 200 (без проверки тела)")
    private void assertStatus200Only(Object requestBody) {
        Response response = sendPostRequest(requestBody);
        assertEquals(200, response.statusCode());
    }

    @Step("Проверить, что ответ полностью соответствует документации (поля id, sellerId, name, price, statistics, createdAt)")
    private void assertFullSuccessResponse(CreateItemResponse response, CreateItemRequest request) {
        assertNotNull(response.getId());
        assertFalse(response.getId().isEmpty());
        assertEquals(request.getSellerId(), response.getSellerId());
        assertEquals(request.getName(), response.getName());
        assertEquals(request.getPrice(), response.getPrice());
        assertNotNull(response.getStatistics());
        assertEquals(request.getStatistics().getLikes(), response.getStatistics().getLikes());
        assertEquals(request.getStatistics().getViewCount(), response.getStatistics().getViewCount());
        assertEquals(request.getStatistics().getContacts(), response.getStatistics().getContacts());
        assertNotNull(response.getCreatedAt());
        assertTrue(response.getCreatedAt().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z?"));
    }

    @Step("Извлечь идентификатор из ответа (поддерживает оба формата: id или status)")
    private String extractIdFromResponse(Response response) {
        String id = response.jsonPath().getString("id");
        if (id != null && !id.isEmpty()) {
            return id;
        }
        String status = response.jsonPath().getString("status");
        if (status != null && status.startsWith("Сохранили объявление - ")) {
            return status.replace("Сохранили объявление - ", "");
        }
        fail("Не удалось извлечь идентификатор из ответа: " + response.asString());
        return null;
    }

    @Test
    @DisplayName("TC-POST-структура: При успешном создании объявления ответ должен полностью соответствовать документации")
    @Description("Проверяет, что при успешном POST /api/1/item возвращается JSON со всеми обязательными полями и корректными типами")
    void testSuccessResponseStructure() {
        Statistics stats = new Statistics(5, 10, 2);
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, "ContractTest", 999, stats);
        Response response = sendPostRequest(request);
        assertEquals(200, response.statusCode());
        CreateItemResponse actualResponse = response.as(CreateItemResponse.class);
        assertFullSuccessResponse(actualResponse, request);
    }

    @Test
    @DisplayName("TC-POST-01: Можно создать объявление с нулевыми значениями лайков, просмотров и контактов")
    @Description("Проверяет, что статистика может быть (0,0,0) – сервер должен принять такие значения")
    void testCreateItemWithZeroStatistics() {
        Statistics stats = new Statistics(0, 0, 0);
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, DEFAULT_NAME, DEFAULT_PRICE, stats);
        assertStatus200Only(request);
    }

    @Test
    @DisplayName("TC-POST-02: Можно создать объявление с положительной статистикой")
    @Description("Проверяет, что можно передать ненулевые значения лайков, просмотров и контактов")
    void testCreateItemWithNonZeroStatistics() {
        Statistics stats = new Statistics(10, 100, 5);
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, "Товар", 5000, stats);
        assertStatus200Only(request);
    }

    @Test
    @DisplayName("TC-POST-03: Название объявления может содержать до 255 символов")
    @Description("Проверка граничного значения длины поля name – 255 символов")
    void testCreateItemWithMaxNameLength() {
        String longName = "A".repeat(255);
        Statistics stats = new Statistics(10, 100, 5);
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, longName, DEFAULT_PRICE, stats);
        assertStatus200Only(request);
    }

    @Test
    @DisplayName("TC-POST-04: Цена может быть равна нулю")
    @Description("Проверка минимально допустимого значения price = 0")
    void testCreateItemWithMinPrice() {
        Statistics stats = new Statistics(10, 100, 5);
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, DEFAULT_NAME, 0, stats);
        assertStatus200Only(request);
    }

    @Test
    @DisplayName("TC-POST-05: Цена может быть максимально возможным целым числом (Long.MAX_VALUE)")
    @Description("Проверка граничного значения price = Long.MAX_VALUE")
    void testCreateItemWithMaxPrice() {
        Long maxPrice = Long.MAX_VALUE;
        Statistics stats = new Statistics(10, 100, 5);
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, DEFAULT_NAME, maxPrice, stats);
        assertStatus200Only(request);
    }

    @Test
    @DisplayName("TC-POST-06: При создании нескольких одинаковых объявлений каждое получает уникальный идентификатор в формате UUID")
    @Description("Проверяет, что id каждого созданного объявления уникален и соответствует формату UUID")
    void testCreateMultipleIdenticalItems() {
        Statistics stats = new Statistics(1, 2, 3);
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, "Same", 100, stats);
        Pattern uuidPattern = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Response response = sendPostRequest(request);
            assertEquals(200, response.statusCode(), "Запрос №" + (i+1) + " не вернул 200");
            String id = extractIdFromResponse(response);
            assertNotNull(id);
            assertFalse(id.isEmpty());
            assertTrue(uuidPattern.matcher(id).matches(), "ID не соответствует формату UUID: " + id);
            ids.add(id);
        }
        long uniqueCount = ids.stream().distinct().count();
        assertEquals(ids.size(), uniqueCount, "Среди созданных объявлений есть дубликаты id");
    }

    @Test
    @DisplayName("TC-POST-17: Лишние поля в запросе не должны мешать успешному созданию объявления")
    @Description("Проверяет толерантность API к дополнительным недокументированным полям")
    void testCreateItemWithExtraField() {
        String json = "{\"sellerID\": 123456, \"name\": \"A\", \"price\": 1, \"statistics\": {\"likes\":1,\"viewCount\":1,\"contacts\":1}, \"extra\": \"field\"}";
        Response response = given()
                .contentType(ContentType.JSON)
                .body(json)
                .when()
                .post("/api/1/item");
        assertEquals(200, response.statusCode());
    }

    @Test
    @DisplayName("TC-POST-07: Блок статистики (statistics) обязателен — без него сервер вернёт ошибку 400")
    @Description("Негативный тест: отсутствие обязательного поля statistics")
    void testCreateItemWithoutStatistics() {
        String json = "{\"sellerID\": 123456, \"name\": \"A\", \"price\": 1}";
        Response response = given()
                .contentType(ContentType.JSON)
                .body(json)
                .when()
                .post("/api/1/item");
        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("TC-POST-08: sellerID не может быть дробным числом — ожидается ошибка 400")
    @Description("Негативный тест: sellerID с плавающей точкой")
    void testCreateItemWithFloatSellerId() {
        String json = "{\"sellerID\": 123.45, \"name\": \"A\", \"price\": 1, \"statistics\": {\"likes\":0,\"viewCount\":0,\"contacts\":0}}";
        Response response = given().contentType(ContentType.JSON).body(json).post("/api/1/item");
        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("TC-POST-09: sellerID не может быть отрицательным — ожидается ошибка 400")
    @Description("Негативный тест: отрицательный sellerID")
    void testCreateItemWithNegativeSellerId() {
        CreateItemRequest request = new CreateItemRequest(-5, DEFAULT_NAME, DEFAULT_PRICE, new Statistics(0,0,0));
        Response response = sendPostRequest(request);
        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("TC-POST-10: sellerID не может быть нулевым — ожидается ошибка 400")
    @Description("Негативный тест: sellerID = 0")
    void testCreateItemWithZeroSellerId() {
        CreateItemRequest request = new CreateItemRequest(0, DEFAULT_NAME, DEFAULT_PRICE, new Statistics(0,0,0));
        Response response = sendPostRequest(request);
        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("TC-POST-11: Название объявления не может быть пустой строкой — ожидается ошибка 400")
    @Description("Негативный тест: пустое поле name")
    void testCreateItemWithEmptyName() {
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, "", DEFAULT_PRICE, new Statistics(0,0,0));
        Response response = sendPostRequest(request);
        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("TC-POST-12: Слишком длинное название (1000 символов) должно приводить к ошибке 400")
    @Description("Негативный тест: name = 1000 символов (превышение допустимой длины)")
    void testCreateItemWithVeryLongName() {
        String veryLongName = "A".repeat(1000);
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, veryLongName, DEFAULT_PRICE, new Statistics(1,2,2));
        Response response = sendPostRequest(request);
        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("TC-POST-13: Цена не может быть отрицательной — ожидается ошибка 400")
    @Description("Негативный тест: отрицательная цена")
    void testCreateItemWithNegativePrice() {
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, DEFAULT_NAME, -100, new Statistics(1,1,1));
        Response response = sendPostRequest(request);
        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("TC-POST-14: Цена должна быть числом, а не строкой — ожидается ошибка 400")
    @Description("Негативный тест: price передан как строка")
    void testCreateItemWithPriceAsString() {
        String json = "{\"sellerID\": 123456, \"name\": \"A\", \"price\": \"100\", \"statistics\": {\"likes\":1,\"viewCount\":1,\"contacts\":1}}";
        Response response = given().contentType(ContentType.JSON).body(json).post("/api/1/item");
        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("TC-POST-15: Поле sellerID обязательно — при его отсутствии возвращается ошибка 400 с корректной структурой")
    @Description("Негативный тест: отсутствует поле sellerID, проверяется структура ошибки")
    void testCreateItemWithoutSellerId() {
        String json = "{\"name\": \"A\", \"price\": 1, \"statistics\": {\"likes\":1,\"viewCount\":1,\"contacts\":1}}";
        Response response = given().contentType(ContentType.JSON).body(json).post("/api/1/item");
        assertEquals(400, response.statusCode());
        ErrorResponse error = response.as(ErrorResponse.class);
        assertNotNull(error.getResult());
        assertNotNull(error.getResult().getMessage());
        assertNotNull(error.getStatus());
        assertFalse(error.getStatus().isEmpty());
        assertNotNull(error.getResult().getMessages());
    }

    @Test
    @DisplayName("TC-POST-16: Невалидный JSON (синтаксическая ошибка) должен возвращать ошибку 400 с правильной структуру")
    @Description("Негативный тест: невалидный JSON в теле запроса")
    void testCreateItemWithInvalidJson() {
        String invalidJson = "{sellerID: 123, name: \"test\"";
        Response response = given().contentType(ContentType.JSON).body(invalidJson).post("/api/1/item");
        assertEquals(400, response.statusCode());
        ErrorResponse error = response.as(ErrorResponse.class);
        assertNotNull(error.getResult());
        assertNotNull(error.getResult().getMessage());
        assertNotNull(error.getStatus());
        assertFalse(error.getStatus().isEmpty());
        assertNotNull(error.getResult().getMessages());
    }

    @Test
    @DisplayName("TC-POST-18: Внутри statistics поле likes обязательно — его отсутствие приводит к ошибке 400")
    @Description("Негативный тест: внутри statistics отсутствует поле likes")
    void testCreateItemWithoutLikes() {
        String json = "{\"sellerID\": 123456, \"name\": \"A\", \"price\": 1, \"statistics\": {\"viewCount\": 10, \"contacts\": 1}}";
        Response response = given().contentType(ContentType.JSON).body(json).post("/api/1/item");
        assertEquals(400, response.statusCode());
        ErrorResponse error = response.as(ErrorResponse.class);
        assertNotNull(error.getResult());
        assertNotNull(error.getResult().getMessage());
        assertNotNull(error.getStatus());
        assertFalse(error.getStatus().isEmpty());
        assertNotNull(error.getResult().getMessages());
    }

    @Test
    @DisplayName("TC-POST-19: Поле likes в statistics должно быть числом, а не строкой — ожидается ошибка 400")
    @Description("Негативный тест: поле likes передано как строка")
    void testCreateItemWithStringInStatistics() {
        String json = "{\"sellerID\": 123456, \"name\": \"A\", \"price\": 1, \"statistics\": {\"likes\": \"10\", \"viewCount\": 100, \"contacts\": 5}}";
        Response response = given().contentType(ContentType.JSON).body(json).post("/api/1/item");
        assertEquals(400, response.statusCode());
        ErrorResponse error = response.as(ErrorResponse.class);
        assertNotNull(error.getResult());
        assertNotNull(error.getResult().getMessage());
        assertNotNull(error.getStatus());
        assertFalse(error.getStatus().isEmpty());
        assertNotNull(error.getResult().getMessages());
    }

    @Test
    @DisplayName("TC-POST-20: Количество лайков не может быть отрицательным — ожидается ошибка 400")
    @Description("Негативный тест: отрицательное значение likes")
    void testCreateItemWithNegativeLikes() {
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, DEFAULT_NAME, DEFAULT_PRICE, new Statistics(-1, 1, 1));
        Response response = sendPostRequest(request);
        assertEquals(400, response.statusCode());
        ErrorResponse error = response.as(ErrorResponse.class);
        assertNotNull(error.getResult());
        assertNotNull(error.getResult().getMessage());
        assertNotNull(error.getStatus());
        assertFalse(error.getStatus().isEmpty());
        assertNotNull(error.getResult().getMessages());
    }

    @Test
    @DisplayName("TC-POST-21: Количество просмотров не может быть отрицательным — ожидается ошибка 400")
    @Description("Негативный тест: отрицательное значение viewCount")
    void testCreateItemWithNegativeViewCount() {
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, DEFAULT_NAME, DEFAULT_PRICE, new Statistics(1, -1, 1));
        Response response = sendPostRequest(request);
        assertEquals(400, response.statusCode());
        ErrorResponse error = response.as(ErrorResponse.class);
        assertNotNull(error.getResult());
        assertNotNull(error.getResult().getMessage());
        assertNotNull(error.getStatus());
        assertFalse(error.getStatus().isEmpty());
        assertNotNull(error.getResult().getMessages());
    }

    @Test
    @DisplayName("TC-POST-22: Количество контактов не может быть отрицательным — ожидается ошибка 400")
    @Description("Негативный тест: отрицательное значение contacts")
    void testCreateItemWithNegativeContacts() {
        CreateItemRequest request = new CreateItemRequest(DEFAULT_SELLER_ID, DEFAULT_NAME, DEFAULT_PRICE, new Statistics(1, 1, -1));
        Response response = sendPostRequest(request);
        assertEquals(400, response.statusCode());
        ErrorResponse error = response.as(ErrorResponse.class);
        assertNotNull(error.getResult());
        assertNotNull(error.getResult().getMessage());
        assertNotNull(error.getStatus());
        assertFalse(error.getStatus().isEmpty());
        assertNotNull(error.getResult().getMessages());
    }

    @Test
    @DisplayName("TC-POST-23: Цена не может превышать максимальное значение Long — ожидается ошибка 400")
    @Description("Негативный тест: price больше Long.MAX_VALUE (9223372036854775808)")
    void testCreateItemWithOverMaxPrice() {
        String json = "{\"sellerID\": 123456, \"name\": \"A\", \"price\": 9223372036854775808, \"statistics\": {\"likes\":1,\"viewCount\":1,\"contacts\":1} }";
        Response response = given()
                .contentType(ContentType.JSON)
                .body(json)
                .when()
                .post("/api/1/item");
        assertEquals(400, response.statusCode());
    }
}