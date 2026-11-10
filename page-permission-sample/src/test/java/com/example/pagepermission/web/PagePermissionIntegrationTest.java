package com.example.pagepermission.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.example.pagepermission.security.RsaPageTokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Timeout(10)
class PagePermissionIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired RsaPageTokenService tokenService;

    @Test
    void parentGuardAndCrossChildApiProduceRealJwtChain() throws Exception {
        String tokenA = authenticate("11001");

        MvcResult resultB = mockMvc.perform(post("/api/business/b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, "Bearer " + tokenA)
                        .content("{\"code\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("B controller returned normally"))
                .andExpect(header().exists(PageTokenRequestReader.HEADER))
                .andReturn();
        String tokenB = resultB.getResponse().getHeader(PageTokenRequestReader.HEADER);

        MvcResult resultC = mockMvc.perform(post("/api/business/c")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, "Bearer " + tokenB)
                        .content("{\"code\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("C controller returned normally"))
                .andExpect(header().exists(PageTokenRequestReader.HEADER))
                .andReturn();

        assertThat(resultC.getResponse().getHeader(PageTokenRequestReader.HEADER)).isNotEqualTo(tokenB);
    }

    @Test
    void samePageBusinessCallDoesNotIssueAnotherJwt() throws Exception {
        String tokenB = authenticate("12341");
        mockMvc.perform(post("/api/business/b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, tokenB)
                        .content("{\"code\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(PageTokenRequestReader.HEADER));
    }

    @Test
    void invalidOperationCodeIsRejectedBeforeController() throws Exception {
        String tokenA = authenticate("11001");
        mockMvc.perform(post("/api/business/b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, tokenA)
                        .content("{\"code\":\"9\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OPERATION_CODE_INVALID"));
    }

    @ParameterizedTest(name = "A page + code {0} selects target page {1}")
    @CsvSource({
            "1,12341", "2,12341", "3,12341",
            "4,12342", "5,12342", "6,12342",
            "7,12343", "8,12343", "9,12343",
            "10,12344", "11,12344", "12,12344"
    })
    void nToOneApiSelectsExactlyOneActionPage(String code, String expectedPageId) throws Exception {
        String tokenA = authenticate("11001");
        MvcResult result = callShared(tokenA, code)
                .andExpect(status().isOk())
                .andExpect(header().exists(PageTokenRequestReader.HEADER))
                .andReturn();

        String issued = result.getResponse().getHeader(PageTokenRequestReader.HEADER);
        callShared(issued, code)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(PageTokenRequestReader.HEADER));
        assertJwtPageId(issued, expectedPageId);
    }

    @ParameterizedTest(name = "current page {0} rejects code {1}")
    @CsvSource({"12341,4", "12342,7", "12343,10", "12344,1"})
    void nToOneApiRejectsCodeBelongingToAnotherActionPage(String pageId, String wrongCode) throws Exception {
        String tokenA = authenticate("11001");
        String setupCode = switch (pageId) {
            case "12341" -> "1";
            case "12342" -> "4";
            case "12343" -> "7";
            case "12344" -> "10";
            default -> throw new IllegalArgumentException(pageId);
        };
        String pageToken = callShared(tokenA, setupCode).andReturn().getResponse()
                .getHeader(PageTokenRequestReader.HEADER);

        callShared(pageToken, wrongCode)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OPERATION_CODE_INVALID"))
                .andExpect(header().doesNotExist(PageTokenRequestReader.HEADER));
    }

    @Test
    void crossPageCallWithoutConfiguredReferenceIsRejected() throws Exception {
        String tokenA = authenticate("11001");
        mockMvc.perform(post("/api/business/c")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, tokenA)
                        .content("{\"code\":\"1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PAGE_REFERENCE_DENIED"));
    }

    @Test
    void missingPageJwtIsRejected() throws Exception {
        mockMvc.perform(post("/api/business/b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PAGE_TOKEN_REQUIRED"));
    }

    @Test
    void forgedPageJwtIsRejected() throws Exception {
        String token = authenticate("11001");
        String forged = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");
        mockMvc.perform(post("/api/business/b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, forged)
                        .content("{\"code\":\"1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PAGE_TOKEN_INVALID"));
    }

    @Test
    void controllerFailureDoesNotReturnTargetJwt() throws Exception {
        String tokenA = authenticate("11001");
        mockMvc.perform(post("/api/business/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, tokenA)
                        .content("{\"code\":\"1\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("CONTROLLER_FAILED"))
                .andExpect(header().doesNotExist(PageTokenRequestReader.HEADER));
    }

    @ParameterizedTest
    @CsvSource({"1234", "abcde", "123456", "''"})
    void malformedParentPageIdIsRejected(String pageId) throws Exception {
        mockMvc.perform(post("/api/page-auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageId\":\"" + pageId + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PAGE_ID_INVALID"));
    }

    @Test
    void validButNonMenuParentPageIsDenied() throws Exception {
        mockMvc.perform(post("/api/page-auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageId\":\"23451\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(PageTokenRequestReader.HEADER));
    }

    @Test
    void pageModeIgnoresCodeEvenWhenEveryRequestContainsIt() throws Exception {
        String tokenA = authenticate("22001");
        MvcResult result = mockMvc.perform(post("/api/business/shared")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, tokenA)
                        .content("{\"code\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists(PageTokenRequestReader.HEADER))
                .andReturn();
        String pageToken = result.getResponse().getHeader(PageTokenRequestReader.HEADER);
        assertJwtPageId(pageToken, "56781");

        mockMvc.perform(post("/api/business/shared")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, pageToken)
                        .content("{\"code\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(PageTokenRequestReader.HEADER));
    }

    @Test
    void everyListedSourceCanCallTheSamePageTarget() throws Exception {
        for (String sourcePageId : new String[]{"22001", "22002"}) {
            String sourceToken = tokenService.issueInitial(sourcePageId);
            MvcResult result = mockMvc.perform(post("/api/business/shared")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(PageTokenRequestReader.HEADER, sourceToken)
                            .content("{\"code\":\"ordinary-page-code\"}"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(PageTokenRequestReader.HEADER))
                    .andReturn();
            assertJwtPageId(result.getResponse().getHeader(PageTokenRequestReader.HEADER), "56781");
        }
    }

    @Test
    void sourceOutsideTargetSourceListIsDeniedWithoutNewJwt() throws Exception {
        String sourceToken = tokenService.issueInitial("33001");
        mockMvc.perform(post("/api/business/shared")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, sourceToken)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PAGE_REFERENCE_DENIED"))
                .andExpect(header().doesNotExist(PageTokenRequestReader.HEADER));
    }

    @Test
    void pageReferenceDeterminesTargetBeforeOperationCodeIsConsidered() throws Exception {
        String sourceToken = tokenService.issueInitial("22001");

        // 22001只关联PAGE目标56781。即使code=1也不能把请求切换到OPERATION目标12341。
        // 22001はPAGE遷移先56781だけに関連します。code=1でもOPERATION遷移先には切り替えません。
        MvcResult result = mockMvc.perform(post("/api/business/shared")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, sourceToken)
                        .content("{\"code\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists(PageTokenRequestReader.HEADER))
                .andReturn();
        assertJwtPageId(result.getResponse().getHeader(PageTokenRequestReader.HEADER), "56781");
    }

    @Test
    void currentOperationPageWithoutCodeIsRejected() throws Exception {
        String operationToken = tokenService.issueInitial("12341");
        mockMvc.perform(post("/api/business/shared")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, operationToken)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OPERATION_CODE_REQUIRED"))
                .andExpect(header().doesNotExist(PageTokenRequestReader.HEADER));
    }

    @Test
    void apiWithoutPagePermissionBypassesPageAuthentication() throws Exception {
        mockMvc.perform(post("/api/business/public")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("public controller returned normally"))
                .andExpect(header().doesNotExist(PageTokenRequestReader.HEADER));

        mockMvc.perform(post("/api/business/public")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PageTokenRequestReader.HEADER, "forged-token"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(PageTokenRequestReader.HEADER));
    }

    @Test
    void repeatedParentAuthenticationIssuesDifferentJwt() throws Exception {
        assertThat(authenticate("11001")).isNotEqualTo(authenticate("11001"));
    }

    @Test
    void oldJwtRemainsUsableUntilItsShortExpiration() throws Exception {
        String oldTokenA = authenticate("11001");
        callShared(oldTokenA, "1").andExpect(status().isOk());
        callShared(oldTokenA, "4")
                .andExpect(status().isOk())
                .andExpect(header().exists(PageTokenRequestReader.HEADER));
    }

    @Test
    void corsPreflightAllowsAngularOriginAndPageTokenHeader() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/business/b")
                        .header("Origin", "http://127.0.0.1:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", PageTokenRequestReader.HEADER))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:4200"));
    }

    private String authenticate(String pageId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/page-auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageId\":\"" + pageId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists(PageTokenRequestReader.HEADER))
                .andReturn();
        return result.getResponse().getHeader(PageTokenRequestReader.HEADER);
    }

    private org.springframework.test.web.servlet.ResultActions callShared(String token, String code) throws Exception {
        return mockMvc.perform(post("/api/business/shared")
                .contentType(MediaType.APPLICATION_JSON)
                .header(PageTokenRequestReader.HEADER, token)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private void assertJwtPageId(String token, String expectedPageId) throws Exception {
        String[] parts = token.split("\\.");
        String json = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json).contains("\"pageId\":\"" + expectedPageId + "\"");
    }
}
