package org.example.service;

import org.example.dto.UserRequest;
import org.example.dto.UserResponse;
import org.example.entity.User;
import org.example.event.UserEvent;
import org.example.event.UserOperation;
import org.example.exception.EmailAlreadyExistsException;
import org.example.exception.UserNotFoundException;
import org.example.mapper.UserMapper;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private UserMapper userMapper = new UserMapper();

    @InjectMocks
    private UserServiceImpl userService;

    private final UserRequest request = new UserRequest("Alex", "alex@mail.ru", 20);

    private User existingUser() {
        User user = new User("Alex", "alex@mail.ru", 20);
        user.setId(1L);
        return user;
    }

    @Test
    void shouldCreateUser() {
        when(userRepository.existsByEmail("alex@mail.ru")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserResponse result = userService.create(request);

        assertEquals(1L, result.id());
        assertEquals("Alex", result.name());
        assertEquals("alex@mail.ru", result.email());
        assertEquals(20, result.age());
        assertNotNull(result.createdAt());
        verify(eventPublisher).publishEvent(new UserEvent(UserOperation.CREATED, "alex@mail.ru"));
    }

    @Test
    void shouldThrowWhenCreatingUserWithExistingEmail() {
        when(userRepository.existsByEmail("alex@mail.ru")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> userService.create(request));

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void shouldGetUserById() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));

        UserResponse result = userService.getById(1L);

        assertEquals(1L, result.id());
        assertEquals("alex@mail.ru", result.email());
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getById(99L));
    }

    @Test
    void shouldGetAllUsers() {
        User second = new User("Ivan", "ivan@mail.ru", 25);
        second.setId(2L);
        when(userRepository.findAll()).thenReturn(List.of(existingUser(), second));

        List<UserResponse> result = userService.getAll();

        assertEquals(2, result.size());
        assertEquals("Alex", result.get(0).name());
        assertEquals("Ivan", result.get(1).name());
    }

    @Test
    void shouldUpdateUser() {
        UserRequest updateRequest = new UserRequest("Alexander", "alexander@mail.ru", 21);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));
        when(userRepository.existsByEmailAndIdNot("alexander@mail.ru", 1L)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.update(1L, updateRequest);

        assertEquals("Alexander", result.name());
        assertEquals("alexander@mail.ru", result.email());
        assertEquals(21, result.age());
    }

    @Test
    void shouldThrowWhenUpdatingNonExistingUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.update(99L, request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenUpdatingToEmailOfAnotherUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));
        when(userRepository.existsByEmailAndIdNot("alex@mail.ru", 1L)).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> userService.update(1L, request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldDeleteUserAndPublishEvent() {
        User user = existingUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.delete(1L);

        verify(userRepository).delete(user);
        verify(eventPublisher).publishEvent(new UserEvent(UserOperation.DELETED, "alex@mail.ru"));
    }

    @Test
    void shouldThrowWhenDeletingNonExistingUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.delete(99L));

        verify(userRepository, never()).delete(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
