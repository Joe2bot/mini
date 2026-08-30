package com.minicoze.platform.tool;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ToolSourceType {
    NATIVE("native"), MCP("mcp");
    private final String value;
    ToolSourceType(String value) { this.value=value; }
    @JsonValue public String value() { return value; }
    @JsonCreator public static ToolSourceType from(String value) { for (ToolSourceType type: values()) if(type.value.equals(value)) return type; throw new IllegalArgumentException("Unsupported source type"); }
}
