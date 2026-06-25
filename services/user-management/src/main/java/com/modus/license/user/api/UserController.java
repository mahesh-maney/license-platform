package com.modus.license.user.api;

import com.modus.license.core.web.ApiResponse;
import com.modus.license.core.web.PageResponse;
import com.modus.license.user.api.dto.AssignRolesRequest;
import com.modus.license.user.api.dto.CreateUserRequest;
import com.modus.license.user.api.dto.UpdateUserRequest;
import com.modus.license.user.api.dto.UserResponse;
import com.modus.license.user.domain.enums.UserStatus;
import com.modus.license.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.of(service.createUser(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(service.getUser(id));
    }

    @GetMapping("/by-email/{email}")
    public ApiResponse<UserResponse> getByEmail(@PathVariable String email) {
        return ApiResponse.of(service.getUserByEmail(email));
    }

    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> list(
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<UserResponse> page = (status != null)
                ? service.listByStatus(status, pageable)
                : service.listUsers(pageable);

        return ApiResponse.of(PageResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements()));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.of(service.updateUser(id, request));
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<UserResponse> assignRoles(@PathVariable UUID id,
                                                 @Valid @RequestBody AssignRolesRequest request) {
        return ApiResponse.of(service.assignRoles(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<UserResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.of(service.deactivateUser(id));
    }

    @PostMapping("/{id}/reactivate")
    public ApiResponse<UserResponse> reactivate(@PathVariable UUID id) {
        return ApiResponse.of(service.reactivateUser(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
