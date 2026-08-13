package com.schemaplatform.workflowchat.runtime;

import java.util.List;

public record ModelDto(String id, String name, String model, String provider,
    List<String> capabilities, boolean isDefault) {}
