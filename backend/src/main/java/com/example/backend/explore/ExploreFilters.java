package com.example.backend.explore;

public record ExploreFilters(String query, String deckType, String category,
                             String sourceLanguage, String targetLanguage, String sort) {}
