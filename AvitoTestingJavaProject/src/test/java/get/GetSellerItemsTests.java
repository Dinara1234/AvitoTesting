package get;

import base.BaseTest;
import io.qameta.allure.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.example.models.CreateItemRequest;
import org.example.models.ItemResponse;
import org.example.responses.post.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.example.models.Statistics;

import java.util.List;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@Epic("API тестирование Avito")
@Feature("Получение всех объявлений продавца (GET /api/1/{sellerID}/item)")
public class GetSellerItemsTests extends BaseTest {

    private final int DEFAULT_SELLER_ID = 123456;
    private final String DEFAULT_NAME = "Item";
    private final int DEFAULT_PRICE = 100;

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

    @Step("Извлечь идентификатор из ответа POST")
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

    @Step("Выполнить GET запрос на /api/1/{sellerId}/item")
    private Response sendGetSellerItemsRequest(int sellerId) {
        return given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/1/" + sellerId + "/item")
                .then()
                .extract().response();
    }

    @Step("Проверить структуру ошибки 400")
    private void assertError400Structure(Response response) {
        assertEquals(400, response.statusCode());
        ErrorResponse error = response.as(ErrorResponse.class);
        assertNotNull(error.getResult());
        assertNotNull(error.getResult().getMessage());
        assertNotNull(error.getStatus());
        assertNotNull(error.getResult().getMessages());
    }

    @Step("Проверить, что элемент ответа соответствует созданному объявлению")
    private void assertItemResponse(ItemResponse actual, CreateItemRequest expected, String expectedId) {
        assertEquals(expectedId, actual.getId());
        assertEquals(expected.getSellerId(), actual.getSellerId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getPrice(), actual.getPrice());
        assertNotNull(actual.getStatistics());
        assertEquals(expected.getStatistics().getLikes(), actual.getStatistics().getLikes());
        assertEquals(expected.getStatistics().getViewCount(), actual.getStatistics().getViewCount());
        assertEquals(expected.getStatistics().getContacts(), actual.getStatistics().getContacts());
        assertNotNull(actual.getCreatedAt());
        assertFalse(actual.getCreatedAt().isEmpty());
        assertTrue(actual.getCreatedAt().matches(".*\\d{4}-\\d{2}-\\d{2}.*"),
                "createdAt не содержит дату: " + actual.getCreatedAt());
    }

    @Test
    @DisplayName("TC-GET-SELLER-01: Можно получить все объявления продавца, создав для него несколько объявлений")
    @Description("Создаются два объявления для одного sellerId, затем запрашивается список – оба объявления должны присутствовать и иметь корректные поля")
    void testGetItemsBySellerId_MultipleItems() {
        int sellerId = 5643118;
        Statistics stats = new Statistics(1, 2, 3);
        CreateItemRequest request1 = new CreateItemRequest(sellerId, "First", 100, stats);
        CreateItemRequest request2 = new CreateItemRequest(sellerId, "Second", 200, stats);

        Response createResp1 = sendPostRequest(request1);
        assertEquals(200, createResp1.statusCode());
        String id1 = extractIdFromResponse(createResp1);

        Response createResp2 = sendPostRequest(request2);
        assertEquals(200, createResp2.statusCode());
        String id2 = extractIdFromResponse(createResp2);

        Response getResponse = sendGetSellerItemsRequest(sellerId);
        assertEquals(200, getResponse.statusCode());

        List<ItemResponse> items = getResponse.jsonPath().getList(".", ItemResponse.class);
        assertNotNull(items);

        List<String> foundIds = items.stream().map(ItemResponse::getId).collect(Collectors.toList());
        assertTrue(foundIds.contains(id1), "Не найдено объявление с id " + id1);
        assertTrue(foundIds.contains(id2), "Не найдено объявление с id " + id2);

        for (ItemResponse item : items) {
            if (item.getId().equals(id1)) {
                assertItemResponse(item, request1, id1);
            } else if (item.getId().equals(id2)) {
                assertItemResponse(item, request2, id2);
            }
        }
    }

    @Test
    @DisplayName("TC-GET-SELLER-02: Для продавца без объявлений должен возвращаться пустой массив")
    @Description("Если у продавца нет объявлений, GET возвращает 200 и пустой список. При наличии объявлений они предварительно удаляются.")
    void testGetItemsBySellerId_NoItems() {
        int targetSellerId = 5643118;
        Response getResponse = sendGetSellerItemsRequest(targetSellerId);
        assertEquals(200, getResponse.statusCode());

        List<ItemResponse> existingItems = getResponse.jsonPath().getList(".", ItemResponse.class);
        if (existingItems != null && !existingItems.isEmpty()) {
            for (ItemResponse item : existingItems) {
                String itemId = item.getId();
                Response deleteResponse = given()
                        .when()
                        .delete("/api/2/item/" + itemId)
                        .then()
                        .extract().response();
                assertTrue(deleteResponse.statusCode() == 200 || deleteResponse.statusCode() == 404,
                        "Не удалось удалить объявление " + itemId);
            }
            getResponse = sendGetSellerItemsRequest(targetSellerId);
            assertEquals(200, getResponse.statusCode());
        }

        List<?> items = getResponse.jsonPath().getList(".");
        assertNotNull(items);
        assertEquals(0, items.size(), "Ожидался пустой массив, но получены объявления");
    }

    @Test
    @DisplayName("TC-GET-SELLER-03: Передача sellerID в виде строки (не числа) вызывает ошибку 400")
    @Description("Некорректный тип параметра – ожидается 400 с валидной структурой ошибки")
    void testGetItemsBySellerId_String() {
        Response response = given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/1/abc/item")
                .then()
                .extract().response();
        assertError400Structure(response);
    }

    @Test
    @DisplayName("TC-GET-SELLER-04: Отрицательный sellerID приводит к ошибке 400")
    @Description("sellerId не может быть отрицательным – ожидается 400")
    void testGetItemsBySellerId_Negative() {
        Response response = sendGetSellerItemsRequest(-123456);
        assertError400Structure(response);
    }

    @Test
    @DisplayName("TC-GET-SELLER-05: Дробный sellerID вызывает ошибку 400")
    @Description("sellerId должен быть целым числом – дробное значение даёт 400")
    void testGetItemsBySellerId_Float() {
        Response response = given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/1/123.45/item")
                .then()
                .extract().response();
        assertError400Structure(response);
    }

    @Test
    @DisplayName("TC-GET-SELLER-06: Очень большой sellerID (10^12) должен либо вернуть 200 с массивом, либо 400")
    @Description("Проверка граничных значений: API может иметь ограничение на размер sellerId")
    void testGetItemsBySellerId_HugeNumber() {
        long hugeSellerId = 1_000_000_000_000L;
        Response response = sendGetSellerItemsRequest((int) hugeSellerId);
        int statusCode = response.statusCode();
        assertTrue(statusCode == 200 || statusCode == 400,
                "Ожидался код 200 или 400, получен " + statusCode);
        if (statusCode == 200) {
            List<?> items = response.jsonPath().getList(".");
            assertNotNull(items);
        } else {
            assertError400Structure(response);
        }
    }

    @Test
    @DisplayName("TC-GET-SELLER-07: sellerID = 0 возвращает ошибку 400")
    @Description("Нулевой sellerId считается невалидным – ожидается 400")
    void testGetItemsBySellerId_Zero() {
        Response response = sendGetSellerItemsRequest(0);
        assertError400Structure(response);
    }
}