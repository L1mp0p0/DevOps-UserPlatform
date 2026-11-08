package org.example.repository;

import org.example.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Тест слоя JPA на встроенной H2 (настройки в src/test/resources/application.yml). */
@DataJpaTest
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindUser() {
        User saved = userRepository.save(new User("Alex", "alex@mail.ru", 20));

        assertNotNull(saved.getId());

        Optional<User> found = userRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Alex", found.get().getName());
        assertEquals("alex@mail.ru", found.get().getEmail());
        assertEquals(20, found.get().getAge());
    }

    @Test
    void shouldFindAllUsers() {
        userRepository.save(new User("Alex", "alex@mail.ru", 20));
        userRepository.save(new User("Ivan", "ivan@mail.ru", 25));

        List<User> users = userRepository.findAll();

        assertEquals(2, users.size());
    }

    @Test
    void shouldDeleteUser() {
        User saved = userRepository.save(new User("Alex", "alex@mail.ru", 20));

        userRepository.deleteById(saved.getId());

        assertTrue(userRepository.findById(saved.getId()).isEmpty());
    }

    @Test
    void shouldCheckEmailExistence() {
        User saved = userRepository.save(new User("Alex", "alex@mail.ru", 20));

        assertTrue(userRepository.existsByEmail("alex@mail.ru"));
        assertFalse(userRepository.existsByEmail("other@mail.ru"));

        assertFalse(userRepository.existsByEmailAndIdNot("alex@mail.ru", saved.getId()));
        assertTrue(userRepository.existsByEmailAndIdNot("alex@mail.ru", saved.getId() + 1));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        userRepository.saveAndFlush(new User("Alex", "alex@mail.ru", 20));

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(new User("Other", "alex@mail.ru", 30)));
    }
}
