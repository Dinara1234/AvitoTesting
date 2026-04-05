package get;

import base.BaseTest;
import io.qameta.allure.*;
import io.restassured.http.ContentType;
import org.example.responses.post.ErrorResponse;
import io.restassured.response.Response;
import org.example.models.CreateItemRequest;
import org.example.models.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@Epic("API тестирование Avito")
@Feature("Получение статистики по объявлению (GET /api/2/statistic/{id})")
public class GetStatisticV2Tests extends BaseTest {

    private final int DEFAULT_SELLER_ID = 123456;
    private final String DEFAULT_NAME = "StatV2Test";
    private final int DEFAULT_PRICE = 500;

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

    @Step("Выполнить GET запрос на /api/2/statistic/{id}")
    private Response sendGetStatisticV2Request(String id) {
        return given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/2/statistic/" + id)
                .then()
                .extract().response();
    }

    @Step("Проверить структуру ответа при ошибке 404")
    private void assertError404Structure(Response response) {
        assertEquals(404, response.statusCode());
        assertNotNull(response.jsonPath().getString("result"));
        assertNotNull(response.jsonPath().getString("status"));
    }

    @Test
    @DisplayName("TC-STAT-V2-01: Можно получить статистику для существующего объявления через версию API v2")
    @Description("Создаётся объявление с заданной статистикой, затем GET /api/2/statistic/{id} возвращает те же значения")
    void testGetStatisticV2ForExistingItem() {
        Statistics expectedStats = new Statistics(42, 100, 7);
        CreateItemRequest createRequest = new CreateItemRequest(
                DEFAULT_SELLER_ID, DEFAULT_NAME, DEFAULT_PRICE, expectedStats
        );
        Response createResponse = sendPostRequest(createRequest);
        assertEquals(200, createResponse.statusCode());
        String itemId = extractIdFromResponse(createResponse);

        Response getResponse = sendGetStatisticV2Request(itemId);
        assertEquals(200, getResponse.statusCode());

        List<Statistics> statisticsList = getResponse.jsonPath().getList(".", Statistics.class);
        assertNotNull(statisticsList);
        assertEquals(1, statisticsList.size());

        Statistics actualStats = statisticsList.get(0);
        assertEquals(expectedStats.getLikes(), actualStats.getLikes());
        assertEquals(expectedStats.getViewCount(), actualStats.getViewCount());
        assertEquals(expectedStats.getContacts(), actualStats.getContacts());
    }

    @Test
    @DisplayName("TC-STAT-V2-02: Запрос статистики для несуществующего идентификатора возвращает ошибку 404")
    @Description("Используется заведомо несуществующий UUID, ожидается 404 и корректное тело ошибки")
    void testGetStatisticV2ForNonExistentId() {
        String fakeId = "00000000-0000-0000-0000-000000000000";
        Response response = sendGetStatisticV2Request(fakeId);
        assertError404Structure(response);
    }

    @Test
    @DisplayName("TC-STAT-V2-03: Идентификатор в неверном формате приводит к ошибке 400 или 404")
    @Description("Некорректный ID (не UUID) может вернуть 400 или 404 – проверяем оба варианта и структуру ошибки")
    void testGetStatisticV2WithInvalidFormatId() {
        Response response = sendGetStatisticV2Request("invalid");
        int statusCode = response.statusCode();
        assertTrue(statusCode == 400 || statusCode == 404,
                "Ожидался код 400 или 404, получен " + statusCode);
        if (statusCode == 400) {
            ErrorResponse error = response.as(ErrorResponse.class);
            assertNotNull(error.getResult());
            assertNotNull(error.getResult().getMessage());
            assertNotNull(error.getStatus());
        } else {
            assertError404Structure(response);
        }
    }

    @Test
    @DisplayName("TC-STAT-V2-04: После удаления объявления статистика по его идентификатору больше недоступна (ошибка 404)")
    @Description("Объявление удаляется через DELETE /api/2/item/{id}, после чего GET статистики должен вернуть 404")
    void testGetStatisticV2AfterDelete() {
        Statistics stats = new Statistics(1, 2, 3);
        CreateItemRequest createRequest = new CreateItemRequest(
                DEFAULT_SELLER_ID, "ToDeleteStatV2", 777, stats
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

        Response getResponse = sendGetStatisticV2Request(itemId);
        assertError404Structure(getResponse);
    }
}