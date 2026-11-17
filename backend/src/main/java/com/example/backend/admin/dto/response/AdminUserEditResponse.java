package com.example.backend.admin.dto.response;

public record AdminUserEditResponse(AdminUserResponse user, String pendingEmail, boolean sessionsInvalidated) {}
