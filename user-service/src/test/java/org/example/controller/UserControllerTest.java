package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.UserRequest;
import org.example.dto.UserResponse;
import org.example.exception.EmailAlreadyExistsException;
import org.example.exception.UserNotFoundException;
import org.example.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private final UserResponse response =
            new UserResponse(1L, "Alex", "alex@mail.ru", 20, LocalDateTime.of(2026, 1, 1, 12, 0));

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void getAll_shouldReturnUsers() throws Exception {
        when(userService.getAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].email").value("alex@mail.ru"));
    }

    @Test
    void getById_shouldReturnUser() throws Exception {
        when(userService.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alex"))
                .andExpect(jsonPath("$.age").value(20));
    }

    @Test
    void getById_shouldReturn404WhenNotFound() throws Exception {
        when(userService.getById(99L)).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_shouldReturn400WhenIdIsNotNumber() throws Exception {
        mockMvc.perform(get("/api/users/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturn201() throws Exception {
        when(userService.create(any(UserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserRequest("Alex", "alex@mail.ru", 20))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alex@mail.ru"));
    }

    @Test
    void create_shouldReturn400WhenBodyInvalid() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserRequest("", "not-an-email", -5))))
                .andExpect(status().isBadRequest());

        verify(userService, never()).create(any());
    }

    @Test
    void create_shouldReturn400WhenAgeMissing() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alex\",\"email\":\"alex@mail.ru\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturn409WhenEmailExists() throws Exception {
        when(userService.create(any(UserRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("alex@mail.ru"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserRequest("Alex", "alex@mail.ru", 20))))
                .andExpect(status().isConflict());
    }

    @Test
    void update_shouldReturnUpdatedUser() throws Exception {
        UserResponse updated =
                new UserResponse(1L, "Alexander", "alex@mail.ru", 21, response.createdAt());
        when(userService.update(eq(1L), any(UserRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserRequest("Alexander", "alex@mail.ru", 21))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alexander"))
                .andExpect(jsonPath("$.age").value(21));
    }

    @Test
    void update_shouldReturn404WhenNotFound() throws Exception {
        when(userService.update(eq(99L), any(UserRequest.class)))
                .thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(put("/api/users/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserRequest("Alex", "alex@mail.ru", 20))))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_shouldReturn400WhenBodyInvalid() throws Exception {
        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserRequest("Alex", "bad", 20))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());

        verify(userService).delete(1L);
    }

    @Test
    void delete_shouldReturn404WhenNotFound() throws Exception {
        doThrow(new UserNotFoundException(99L)).when(userService).delete(99L);

        mockMvc.perform(delete("/api/users/99"))
                .andExpect(status().isNotFound());
    }
}
