package xyz.lukix.keygo.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安全过滤链行为测试（H2 test profile）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedApiRequestGets401() throws Exception {
        mockMvc.perform(get("/api/v1/codes/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void undeclaredApiEndpointRequiresAuthByDefault() throws Exception {
        // /api/** 默认拒绝：即使未显式声明的端点也不允许匿名访问
        mockMvc.perform(get("/api/v1/some-future-endpoint"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonApiPathsRemainPublic() throws Exception {
        // SPA 静态资源路径不在 /api/** 保护范围内：未认证时不应返回 401（资源不存在则为 404）
        mockMvc.perform(get("/some/spa/route"))
                .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            if (status == 401 || status == 403) {
                                throw new AssertionError("非 API 路径不应要求认证，实际状态码: " + status);
                            }
                        }
                );
    }

    @Test
    void registerEndpointIsPermitAll() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester\",\"password\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void loginFailureReturns401WithApiEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .param("username", "nobody")
                        .param("password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void authenticatedUserCanAccessStats() throws Exception {
        // Spring Security 7 下 @WithMockUser 的 TestSecurityContextHolder 不会自动桥接到
        // 惰性加载的过滤器链，改用显式 user() 请求后置处理器注入认证
        mockMvc.perform(get("/api/v1/codes/stats")
                        .with(SecurityMockMvcRequestPostProcessors.user("tester").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").exists());
    }

    @Test
    void redeemEndpointIsPublicAndFailMessageIsUnified() throws Exception {
        // 匿名可达（否则入口点会返回 401），且"不存在"与"已核销"共用一条话术防枚举
        mockMvc.perform(post("/api/v1/codes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"XXXXX-XXXXX-XXXXX-XXXXX\",\"deviceId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("激活码无效或已被核销"));
    }

    @Test
    void generateEndpointRejectsNonAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/codes/generate")
                        .with(SecurityMockMvcRequestPostProcessors.user("tester").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":1}"))
                .andExpect(status().isForbidden());
    }
}
