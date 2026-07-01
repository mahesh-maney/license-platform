package com.modus.license.user.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.user.api.dto.AssignRolesRequest;
import com.modus.license.user.api.dto.CreateUserRequest;
import com.modus.license.user.api.dto.UpdateUserRequest;
import com.modus.license.user.api.dto.UserResponse;
import com.modus.license.user.domain.enums.UserStatus;
import com.modus.license.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController")
class UserControllerTest {

    @Mock UserService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    static final UUID USER_ID   = UUID.randomUUID();
    static final UUID TENANT_ID = UUID.randomUUID();
    static final String EMAIL   = "alice@example.com";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private UserResponse response() {
        return new UserResponse(USER_ID, TENANT_ID, EMAIL, "Alice", "Smith",
                UserStatus.ACTIVE, Set.of("TENANT_USER"), Instant.now(), Instant.now());
    }

    // ── POST /api/v1/users ────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/users")
    class Create {

        @Test
        @DisplayName("201 when valid request")
        void success() throws Exception {
            when(service.createUser(any())).thenReturn(response());

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new CreateUserRequest(EMAIL, "Alice", "Smith", Set.of("TENANT_USER")))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.email").value(EMAIL));
        }

        @Test
        @DisplayName("400 when email is blank")
        void missingEmail() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"\",\"firstName\":\"A\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("409 when email already exists")
        void duplicate() throws Exception {
            when(service.createUser(any()))
                    .thenThrow(new ConflictException(ErrorCode.USER_ALREADY_EXISTS, "Email taken"));

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new CreateUserRequest(EMAIL, "Alice", "Smith", Set.of()))))
                    .andExpect(status().isConflict());
        }
    }

    // ── GET /api/v1/users/{id} ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/users/{id}")
    class GetById {

        @Test
        @DisplayName("200 when found")
        void found() throws Exception {
            when(service.getUser(USER_ID)).thenReturn(response());

            mockMvc.perform(get("/api/v1/users/{id}", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(USER_ID.toString()));
        }

        @Test
        @DisplayName("404 when not found")
        void notFound() throws Exception {
            when(service.getUser(USER_ID))
                    .thenThrow(ResourceNotFoundException.user(USER_ID.toString()));

            mockMvc.perform(get("/api/v1/users/{id}", USER_ID))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/users/by-email/{email} ───────────────────────────────────

    @Test
    @DisplayName("GET /by-email → 200 when found")
    void getByEmail() throws Exception {
        when(service.getUserByEmail(EMAIL)).thenReturn(response());

        mockMvc.perform(get("/api/v1/users/by-email/{email}", EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL));
    }

    // ── GET /api/v1/users ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/users → 200 returns page")
    void list() throws Exception {
        when(service.listUsers(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response())));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/users?status=ACTIVE → delegates to listByStatus")
    void listByStatus() throws Exception {
        when(service.listByStatus(eq(UserStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response())));

        mockMvc.perform(get("/api/v1/users").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"));
    }

    // ── PUT /api/v1/users/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/users/{id} → 200 on success")
    void update() throws Exception {
        when(service.updateUser(eq(USER_ID), any())).thenReturn(response());

        mockMvc.perform(put("/api/v1/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest("Bob", "Jones"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL));
    }

    // ── PUT /api/v1/users/{id}/roles ──────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/users/{id}/roles → 200 on success")
    void assignRoles() throws Exception {
        when(service.assignRoles(eq(USER_ID), any())).thenReturn(response());

        mockMvc.perform(put("/api/v1/users/{id}/roles", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssignRolesRequest(Set.of("TENANT_ADMIN")))))
                .andExpect(status().isOk());
    }

    // ── POST /api/v1/users/{id}/deactivate ───────────────────────────────────

    @Test
    @DisplayName("POST /deactivate → 200")
    void deactivate() throws Exception {
        UserResponse inactive = new UserResponse(USER_ID, TENANT_ID, EMAIL, "Alice", "Smith",
                UserStatus.INACTIVE, Set.of(), Instant.now(), Instant.now());
        when(service.deactivateUser(USER_ID)).thenReturn(inactive);

        mockMvc.perform(post("/api/v1/users/{id}/deactivate", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    // ── POST /api/v1/users/{id}/reactivate ───────────────────────────────────

    @Test
    @DisplayName("POST /reactivate → 200")
    void reactivate() throws Exception {
        when(service.reactivateUser(USER_ID)).thenReturn(response());

        mockMvc.perform(post("/api/v1/users/{id}/reactivate", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    // ── DELETE /api/v1/users/{id} ─────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/users/{id} → 204")
    void delete_() throws Exception {
        doNothing().when(service).deleteUser(USER_ID);

        mockMvc.perform(delete("/api/v1/users/{id}", USER_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{id} → 404 when not found")
    void deleteNotFound() throws Exception {
        doThrow(ResourceNotFoundException.user(USER_ID.toString()))
                .when(service).deleteUser(USER_ID);

        mockMvc.perform(delete("/api/v1/users/{id}", USER_ID))
                .andExpect(status().isNotFound());
    }

    // ── GlobalExceptionHandler ─────────────────────────────────────────────────

    @Test
    @DisplayName("500 on unexpected exception")
    void unexpectedException() throws Exception {
        when(service.getUser(any()))
                .thenThrow(new RuntimeException("Database down"));

        mockMvc.perform(get("/api/v1/users/{id}", USER_ID))
                .andExpect(status().isInternalServerError());
    }
}
