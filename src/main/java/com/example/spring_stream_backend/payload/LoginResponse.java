package com.example.spring_stream_backend.payload;

import com.example.spring_stream_backend.Entity.User;

public class LoginResponse {

    private String token;
    private UserInfo user;

    public LoginResponse(String token, User user) {
        this.token = token;
        this.user = new UserInfo(user);
    }

    public String getToken() {
        return token;
    }

    public UserInfo getUser() {
        return user;
    }

    public static class UserInfo {
        private String id;
        private String email;
        private String role;

        public UserInfo(User user) {
            this.id = user.getId();
            this.email = user.getEmail();
            this.role = user.getRole().name();
        }

        public String getId() {
            return id;
        }

        public String getEmail() {
            return email;
        }

        public String getRole() {
            return role;
        }
    }
}
