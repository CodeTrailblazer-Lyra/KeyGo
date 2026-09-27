package xyz.lukix.keygo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.service.ActivationCodeService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 激活码维护行为测试：恢复未使用时清除领取人/设备绑定、
 * 缺失 id 返回 404、核销审计接口权限、codeHash 不随接口返回
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCodesMaintenanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActivationCodeService activationCodeService;

    @Test
    void restoreToUnusedClearsDeviceBindingAndFetcher() {
        activationCodeService.batchImportCodes(List.<String[]>of(new String[]{"maint-code-01", null}));
        ActivationCodeService.RedeemResult r =
                activationCodeService.redeem("MAINT-CODE-01", "dev-maint", "10.9.9.9");
        assertEquals(ActivationCodeService.RedeemStatus.SUCCESS, r.status());

        assertTrue(activationCodeService.markAsUnused(r.codeId()));
        ActivationCode c = activationCodeService.findById(r.codeId()).orElseThrow();
        assertFalse(c.getUsed());
        assertNull(c.getFetchTime());
        assertNull(c.getFetchUser());
        assertNull(c.getDeviceId());
        assertNull(c.getBindTime());

        // 不存在的 id 返回 false（控制器映射为 404）
        assertFalse(activationCodeService.markAsUnused(99_999_999L));
        assertFalse(activationCodeService.markAsUsed(99_999_999L));
        assertFalse(activationCodeService.deleteCode(99_999_999L));

        activationCodeService.deleteCode(r.codeId());
    }

    @Test
    void batchRestoreAlsoClearsFetcher() {
        activationCodeService.batchImportCodes(List.<String[]>of(new String[]{"maint-code-02", null}));
        List<ActivationCode> claimed = activationCodeService.batchClaim(1, "maint-tester");
        assertEquals(1, claimed.size());
        Long id = claimed.getFirst().getId();
        assertEquals("maint-tester", activationCodeService.findById(id).orElseThrow().getFetchUser());

        assertEquals(1, activationCodeService.batchMarkAsUnused(List.of(id)));
        ActivationCode c = activationCodeService.findById(id).orElseThrow();
        assertNull(c.getFetchUser());
        assertNull(c.getFetchTime());

        activationCodeService.deleteCode(id);
    }

    @Test
    void adminMutationEndpointsReturn404ForMissingId() throws Exception {
        var admin = SecurityMockMvcRequestPostProcessors.user("maint-root").roles("SUPER_ADMIN");
        mockMvc.perform(patch("/api/v1/admin/codes/99999999/use").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(patch("/api/v1/admin/codes/99999999/unuse").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(delete("/api/v1/admin/codes/99999999").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void redeemLogsRequiresAdminAndListHidesCodeHash() throws Exception {
        mockMvc.perform(get("/api/v1/admin/codes/redeem-logs")
                        .with(SecurityMockMvcRequestPostProcessors.user("maint-tester").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/codes/redeem-logs")
                        .with(SecurityMockMvcRequestPostProcessors.user("maint-root").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.logs").isArray());

        // 列表接口返回明文 code（存量码），但哈希字段不得序列化给前端
        activationCodeService.batchImportCodes(List.<String[]>of(new String[]{"maint-visibility-01", null}));
        try {
            mockMvc.perform(get("/api/v1/admin/codes")
                            .param("keyword", "maint-visibility")
                            .with(SecurityMockMvcRequestPostProcessors.user("maint-root").roles("SUPER_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.codes[0].code").value("MAINT-VISIBILITY-01"))
                    .andExpect(jsonPath("$.data.codes[0].codeHash").doesNotExist());
        } finally {
            activationCodeService.searchByCode("MAINT-VISIBILITY-01")
                    .forEach(c -> activationCodeService.deleteCode(c.getId()));
        }
    }
}
