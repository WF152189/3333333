package com.example.pagepermission.core;

import com.example.pagepermission.annotation.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageIdParserTest {
    private final PageIdParser parser = new PageIdParser();

    @ParameterizedTest
    @CsvSource({"12341,QUERY", "12342,CREATE", "12343,UPDATE", "12344,DELETE"})
    void parsesGroupAndAction(String pageId, Operation operation) {
        assertEquals("1234", parser.groupId(pageId));
        assertEquals(operation, parser.operation(pageId));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234", "123456", "abcde", "12 41"})
    void rejectsMalformedPageId(String pageId) {
        PagePermissionException ex = assertThrows(PagePermissionException.class, () -> parser.groupId(pageId));
        assertEquals(PagePermissionError.PAGE_ID_INVALID, ex.error());
    }

    @Test
    void rejectsUnknownActionDigit() {
        PagePermissionException ex = assertThrows(PagePermissionException.class, () -> parser.operation("12345"));
        assertEquals(PagePermissionError.PAGE_ID_INVALID, ex.error());
    }
}
