package com.synchat.backend.user.controller;

import com.synchat.backend.user.dto.UserSearchResponse;
import com.synchat.backend.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/search")
    public UserSearchResponse search(@RequestParam("q") String q) {
        return userService.searchUsers(q);
    }
}
