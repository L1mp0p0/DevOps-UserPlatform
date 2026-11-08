package org.example.service;

import org.example.dto.UserRequest;
import org.example.dto.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse create(UserRequest request);

    UserResponse getById(Long id);

    List<UserResponse> getAll();

    UserResponse update(Long id, UserRequest request);

    void delete(Long id);
}
