package com.example.pagepermission.config;

import com.example.pagepermission.annotation.Operation;
import com.example.pagepermission.annotation.AccessMode;
import com.example.pagepermission.annotation.PagePermission;
import com.example.pagepermission.annotation.PagePermissionGroup;
import com.example.pagepermission.annotation.PagePermissionItem;
import com.example.pagepermission.annotation.PagePermissionRef;
import com.example.pagepermission.annotation.PagePermissionRefItem;
import com.example.pagepermission.core.PageIdParser;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PermissionAnnotationValidatorTest {
    private final PermissionAnnotationValidator validator =
            new PermissionAnnotationValidator(null, new PageIdParser());

    @Test
    void validMultiItemConfigurationPasses() throws Exception {
        Method method = method("valid");
        assertDoesNotThrow(() -> validate(method));
    }

    @Test
    void emptyGroupsFail() throws Exception {
        assertInvalid("emptyGroups");
    }

    @Test
    void duplicatePageIdsFail() throws Exception {
        assertInvalid("duplicatePages");
    }

    @Test
    void pageIdActionMismatchFails() throws Exception {
        assertInvalid("actionMismatch");
    }

    @Test
    void duplicateCodesWithinItemFail() throws Exception {
        assertInvalid("duplicateCodes");
    }

    @Test
    void refTargetAbsentFromPermissionFails() throws Exception {
        assertInvalid("missingTarget");
    }

    @Test
    void duplicateReferencesFail() throws Exception {
        assertInvalid("duplicateRefs");
    }

    @Test
    void overlappingCodesAcrossTargetsForSameSourceFail() throws Exception {
        assertInvalid("overlappingTargetCodes");
    }

    @Test
    void pageModeWithoutPageIdFails() throws Exception {
        assertInvalid("pageWithoutId");
    }

    @Test
    void pageModeWithItemsFails() throws Exception {
        assertInvalid("pageWithItems");
    }

    @Test
    void operationModeWithPageIdFails() throws Exception {
        assertInvalid("operationWithPageId");
    }

    @Test
    void oneSourceWithMultiplePageTargetsFails() throws Exception {
        assertInvalid("multiplePageTargets");
    }

    @Test
    void oneSourceWithPageAndOperationTargetsFails() throws Exception {
        assertInvalid("mixedPageAndOperationTargets");
    }

    @Test
    void emptySourcePageIdsFail() throws Exception {
        assertInvalid("emptySources");
    }

    @Test
    void blankSourcePageIdFails() throws Exception {
        assertInvalid("blankSource");
    }

    @Test
    void duplicateSourcePageIdsWithinOneTargetFail() throws Exception {
        assertInvalid("duplicateSources");
    }

    @Test
    void malformedSourcePageIdFails() throws Exception {
        assertInvalid("malformedSource");
    }

    @Test
    void differentSourcesMayUseSameCodeForDifferentTargets() throws Exception {
        Method method = method("sameCodeForDifferentSources");
        assertDoesNotThrow(() -> validate(method));
    }

    @Test
    void emptyReferenceItemsFail() throws Exception {
        assertInvalid("emptyReferenceItems");
    }

    @Test
    void operationWithoutItemsFails() throws Exception {
        assertInvalid("operationWithoutItems");
    }

    @Test
    void operationWithBlankCodeFieldFails() throws Exception {
        assertInvalid("blankCodeField");
    }

    @Test
    void operationWithEmptyCodesFails() throws Exception {
        assertInvalid("emptyCodes");
    }

    @Test
    void operationWithBlankCodeFails() throws Exception {
        assertInvalid("blankCode");
    }

    @Test
    void duplicatePageIdAcrossPageAndOperationModesFails() throws Exception {
        assertInvalid("duplicateAcrossModes");
    }

    @Test
    void referenceWithoutPagePermissionFailsDuringStartupScan() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(RefOnlyBean.class);
            context.refresh();
            PermissionAnnotationValidator startupValidator =
                    new PermissionAnnotationValidator(context, new PageIdParser());

            assertThrows(IllegalStateException.class, startupValidator::afterSingletonsInstantiated);
        }
    }

    private void assertInvalid(String name) throws Exception {
        Method method = method(name);
        assertThrows(IllegalStateException.class, () -> validate(method));
    }

    private Method method(String name) throws Exception {
        return Fixture.class.getDeclaredMethod(name, Map.class);
    }

    private void validate(Method method) {
        validator.validate(
                method,
                method.getAnnotation(PagePermission.class),
                method.getAnnotation(PagePermissionRef.class));
    }

    static class Fixture {
        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION, items = {
                @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = "1"),
                @PagePermissionItem(pageId = "12342", action = Operation.CREATE, codes = "2")
        }))
        static void valid(Map<String, String> body) {}

        @PagePermission(groups = {})
        static void emptyGroups(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION, items = {
                @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = "1"),
                @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = "2")
        }))
        static void duplicatePages(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION,
                items = @PagePermissionItem(pageId = "12341", action = Operation.DELETE, codes = "1")))
        static void actionMismatch(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION,
                items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = {"1", "1"})))
        static void duplicateCodes(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION,
                items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = "1")))
        @PagePermissionRef(items = @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12342"))
        static void missingTarget(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION,
                items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = "1")))
        @PagePermissionRef(items = {
                @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12341"),
                @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12341")
        })
        static void duplicateRefs(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION, items = {
                @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = "1"),
                @PagePermissionItem(pageId = "12342", action = Operation.CREATE, codes = "1")
        }))
        @PagePermissionRef(items = {
                @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12341"),
                @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12342")
        })
        static void overlappingTargetCodes(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.PAGE))
        static void pageWithoutId(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.PAGE, pageId = "56781",
                items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = "1")))
        static void pageWithItems(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION, pageId = "56781",
                items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = "1")))
        static void operationWithPageId(Map<String, String> body) {}

        @PagePermission(groups = {
                @PagePermissionGroup(mode = AccessMode.PAGE, pageId = "56781"),
                @PagePermissionGroup(mode = AccessMode.PAGE, pageId = "67891")
        })
        @PagePermissionRef(items = {
                @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "56781"),
                @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "67891")
        })
        static void multiplePageTargets(Map<String, String> body) {}

        @PagePermission(groups = {
                @PagePermissionGroup(mode = AccessMode.PAGE, pageId = "56781"),
                @PagePermissionGroup(mode = AccessMode.OPERATION,
                        items = @PagePermissionItem(
                                pageId = "12341", action = Operation.QUERY, codes = "1"))
        })
        @PagePermissionRef(items = {
                @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "56781"),
                @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12341")
        })
        static void mixedPageAndOperationTargets(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.PAGE, pageId = "56781"))
        @PagePermissionRef(items = @PagePermissionRefItem(sourcePageIds = {}, targetPageId = "56781"))
        static void emptySources(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.PAGE, pageId = "56781"))
        @PagePermissionRef(items = @PagePermissionRefItem(sourcePageIds = {"11001", ""}, targetPageId = "56781"))
        static void blankSource(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.PAGE, pageId = "56781"))
        @PagePermissionRef(items = @PagePermissionRefItem(
                sourcePageIds = {"11001", "11001"}, targetPageId = "56781"))
        static void duplicateSources(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.PAGE, pageId = "56781"))
        @PagePermissionRef(items = @PagePermissionRefItem(sourcePageIds = "abcde", targetPageId = "56781"))
        static void malformedSource(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION, items = {
                @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = "1"),
                @PagePermissionItem(pageId = "23451", action = Operation.QUERY, codes = "1")
        }))
        @PagePermissionRef(items = {
                @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12341"),
                @PagePermissionRefItem(sourcePageIds = "22001", targetPageId = "23451")
        })
        static void sameCodeForDifferentSources(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.PAGE, pageId = "56781"))
        @PagePermissionRef(items = {})
        static void emptyReferenceItems(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION, items = {}))
        static void operationWithoutItems(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION, codeField = "",
                items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = "1")))
        static void blankCodeField(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION,
                items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = {})))
        static void emptyCodes(Map<String, String> body) {}

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION,
                items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = {"1", ""})))
        static void blankCode(Map<String, String> body) {}

        @PagePermission(groups = {
                @PagePermissionGroup(mode = AccessMode.PAGE, pageId = "12341"),
                @PagePermissionGroup(mode = AccessMode.OPERATION,
                        items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = "1"))
        })
        static void duplicateAcrossModes(Map<String, String> body) {}
    }

    static class RefOnlyBean {
        @PagePermissionRef(items = @PagePermissionRefItem(
                sourcePageIds = "11001", targetPageId = "56781"))
        public void invalid() {}
    }
}
