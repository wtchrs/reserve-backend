package reserve.menu.representation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import reserve.global.BaseRestAssuredTest;
import reserve.menu.domain.Menu;
import reserve.menu.dto.request.MenuCreateRequest;
import reserve.menu.dto.request.MenuUpdateRequest;
import reserve.menu.infrastructure.MenuRepository;
import reserve.signin.dto.SignInToken;
import reserve.signin.infrastructure.JwtProvider;
import reserve.store.domain.Store;
import reserve.store.infrastructure.StoreRepository;
import reserve.user.domain.User;
import reserve.user.infrastructure.UserRepository;

import javax.sql.DataSource;

import static com.epages.restdocs.apispec.RestAssuredRestDocumentationWrapper.document;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.restdocs.headers.HeaderDocumentation.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;

class MenuControllerTest extends BaseRestAssuredTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    UserRepository userRepository;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    MenuRepository menuRepository;

    User user;
    Store store;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("username", "password", "nickname", "description"));
        store = storeRepository.save(new Store(user, "Italian Restaurant", "address", "Pasta and Pizza"));
    }

    @AfterEach
    void tearDown() {
//        menuRepository.deleteAll();
//        storeRepository.deleteAll();
//        userRepository.deleteAll();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM menus");
        jdbcTemplate.update("DELETE FROM stores");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    @DisplayName("[Integration] Testing POST /v1/stores/{storeId}/menus endpoint")
    void testCreateMenuEndpoint() throws JsonProcessingException {
        MenuCreateRequest menuCreateRequest = new MenuCreateRequest();
        menuCreateRequest.setName("Aglio e Olio");
        menuCreateRequest.setPrice(10000);
        menuCreateRequest.setDescription("Spaghetti with garlic and olive oil");

        SignInToken signInToken = jwtProvider.generateSignInToken(String.valueOf(user.getId()));

        String payload = objectMapper.writeValueAsString(menuCreateRequest);

        Response response = RestAssured
                .given(spec)
                .header("Authorization", "Bearer " + signInToken.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(payload)
                .relaxedHTTPSValidation()
                .filter(document(
                        DEFAULT_RESTDOC_PATH,
                        requestHeaders(headerWithName("Authorization").description("Access token in bearer scheme")),
                        pathParameters(parameterWithName("storeId").description("The ID of the store")),
                        requestFields(
                                fieldWithPath("name").description("The name of the menu"),
                                fieldWithPath("price").description("The price of the menu"),
                                fieldWithPath("description").description("The description of the menu")
                        ),
                        responseHeaders(headerWithName("Location").description("The location of the created menu"))
                ))
                .when()
                .post("/v1/stores/{storeId}/menus", store.getId());

        response.then().statusCode(201).header("Location", startsWith("/v1/menus/"));

        String location = response.getHeader("Location");
        long menuId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        menuRepository.findById(menuId).ifPresentOrElse(
                menu -> {
                    assertEquals(menuCreateRequest.getName(), menu.getName());
                    assertEquals(menuCreateRequest.getPrice(), menu.getPrice());
                    assertEquals(menuCreateRequest.getDescription(), menu.getDescription());
                },
                () -> fail("Menu not found")
        );
    }

    @Test
    @DisplayName("[Integration] Testing GET /v1/menus/{menuId} endpoint")
    void testGetMenuInfoEndpoint() {
        Menu menu = menuRepository.save(new Menu(store, "Aglio e Olio", 10000, "Spaghetti with garlic and olive oil"));

        RestAssured
                .given(spec)
                .relaxedHTTPSValidation()
                .filter(document(
                        DEFAULT_RESTDOC_PATH,
                        pathParameters(parameterWithName("menuId").description("The ID of the menu")),
                        responseFields(
                                fieldWithPath("menuId").description("The ID of the menu"),
                                fieldWithPath("storeId").description("The ID of the store"),
                                fieldWithPath("name").description("The name of the menu"),
                                fieldWithPath("price").description("The price of the menu"),
                                fieldWithPath("description").description("The description of the menu")
                        )
                ))
                .when().get("/v1/menus/{menuId}", menu.getId())
                .then()
                .statusCode(200)
                .body(
                        "menuId", equalTo(menu.getId().intValue()),
                        "storeId", equalTo(store.getId().intValue()),
                        "name", equalTo(menu.getName()),
                        "price", equalTo(menu.getPrice()),
                        "description", equalTo(menu.getDescription())
                );
    }

    @Test
    @DisplayName("[Integration] Testing GET /v1/stores/{storeId}/menus endpoint")
    void testGetStoreMenusEndpoint() {
        Menu menu1 = menuRepository.save(new Menu(store, "Aglio e Olio", 10000, "Spaghetti with garlic and olive oil"));
        Menu menu2 = menuRepository.save(new Menu(store, "Carbonara", 12000, "Spaghetti with bacon, eggs, and cheese"));
        Menu menu3 = menuRepository.save(new Menu(store, "Bolognese", 12000, "Spaghetti with meat sauce"));

        RestAssured
                .given(spec)
                .relaxedHTTPSValidation()
                .filter(document(
                        DEFAULT_RESTDOC_PATH,
                        pathParameters(parameterWithName("storeId").description("The ID of the store")),
                        responseFields(
                                fieldWithPath("count").description("The number of menus"),
                                fieldWithPath("results[].menuId").description("The ID of the menu"),
                                fieldWithPath("results[].storeId").description("The ID of the store"),
                                fieldWithPath("results[].name").description("The name of the menu"),
                                fieldWithPath("results[].price").description("The price of the menu"),
                                fieldWithPath("results[].description").description("The description of the menu")
                        )
                ))
                .when().get("/v1/stores/{storeId}/menus", store.getId())
                .then()
                .statusCode(200)
                .body(
                        "count", equalTo(3),
                        "results.menuId", contains(
                                menu1.getId().intValue(),
                                menu2.getId().intValue(),
                                menu3.getId().intValue()
                        ),
                        "results.storeId", contains(
                                store.getId().intValue(),
                                store.getId().intValue(),
                                store.getId().intValue()
                        ),
                        "results.name", contains(menu1.getName(), menu2.getName(), menu3.getName()),
                        "results.price", contains(menu1.getPrice(), menu2.getPrice(), menu3.getPrice()),
                        "results.description", contains(
                                menu1.getDescription(),
                                menu2.getDescription(),
                                menu3.getDescription()
                        )
                );
    }

    @Test
    @DisplayName("[Integration] Testing PUT /v1/menus/{menuId} endpoint")
    void testUpdateMenuEndpoint() throws JsonProcessingException {
        Menu menu1 = menuRepository.save(new Menu(store, "Aglio e Olio", 10000, "Spaghetti with garlic and olive oil"));

        MenuUpdateRequest menuUpdateRequest = new MenuUpdateRequest();
        menuUpdateRequest.setName("Spaghetti Aglio e Olio");
        menuUpdateRequest.setPrice(12000);

        SignInToken signInToken = jwtProvider.generateSignInToken(String.valueOf(user.getId()));

        String payload = objectMapper.writeValueAsString(menuUpdateRequest);

        RestAssured
                .given(spec)
                .header("Authorization", "Bearer " + signInToken.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(payload)
                .relaxedHTTPSValidation()
                .filter(document(
                        DEFAULT_RESTDOC_PATH,
                        requestHeaders(headerWithName("Authorization").description("Access token in bearer scheme")),
                        pathParameters(parameterWithName("menuId").description("The ID of the menu")),
                        requestFields(
                                fieldWithPath("name").description("The name of the menu").optional(),
                                fieldWithPath("price").description("The price of the menu").optional(),
                                fieldWithPath("description").description("The description of the menu").optional()
                        )
                ))
                .when().put("/v1/menus/{menuId}", menu1.getId())
                .then().statusCode(200);

        menuRepository.findById(menu1.getId()).ifPresentOrElse(
                menu -> {
                    assertEquals(menuUpdateRequest.getName(), menu.getName());
                    assertEquals(menuUpdateRequest.getPrice(), menu.getPrice());
                    assertEquals("Spaghetti with garlic and olive oil", menu.getDescription());
                },
                () -> fail("Menu not found")
        );
    }

    @Test
    @DisplayName("[Integration] Testing DELETE /v1/menus/{menuId} endpoint")
    void testDeleteMenuEndpoint() {
        Menu menu1 = menuRepository.save(new Menu(store, "Aglio e Olio", 10000, "Spaghetti with garlic and olive oil"));

        SignInToken signInToken = jwtProvider.generateSignInToken(String.valueOf(user.getId()));

        RestAssured
                .given(spec)
                .header("Authorization", "Bearer " + signInToken.getAccessToken())
                .relaxedHTTPSValidation()
                .filter(document(
                        DEFAULT_RESTDOC_PATH,
                        requestHeaders(headerWithName("Authorization").description("Access token in bearer scheme")),
                        pathParameters(parameterWithName("menuId").description("The ID of the menu"))
                ))
                .when().delete("/v1/menus/{menuId}", menu1.getId())
                .then().statusCode(200);

        assertFalse(menuRepository.existsById(menu1.getId()));
    }

}
