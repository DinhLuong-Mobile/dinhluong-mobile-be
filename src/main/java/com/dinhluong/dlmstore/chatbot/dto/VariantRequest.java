package com.dinhluong.dlmstore.chatbot.dto;

/** A name/configuration request, never an AI-selected database identifier. */
public record VariantRequest(String productName, String ram, String rom, String color) { }
