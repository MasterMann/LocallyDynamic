package com.jeppeman.locallydynamic.serialization;

public enum JsonToken {
    BEGIN_OBJECT('{'),
    END_OBJECT('}'),
    BEGIN_ARRAY('['),
    END_ARRAY(']'),
    DOUBLE_QUOTE('\"'),
    SINGLE_QUOTE('\''),
    COLON(':'),
    COMMA(',');

    private final char value;

    JsonToken(char value) {
        this.value = value;
    }

    public char getValue() {
        return value;
    }
}