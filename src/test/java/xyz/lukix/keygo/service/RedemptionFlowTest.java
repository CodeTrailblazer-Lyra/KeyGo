package xyz.lukix.keygo.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 核销全链路测试（H2 test profile）：明文核销、防重复核销、未知码拦截
 */
@SpringBootTest
@ActiveProfiles("test")
class RedemptionFlowTest {

    @Autowired
    private ActivationCodeService activationCodeService;

    @Test
    void plaintextRedeemSuccess() {
        activationCodeService.batchImportCodes(List.<String[]>of(new String[]{"legacy-code-01", null}));
        // 导入后直接用明文核销（batchClaim 会标记 used=true，导致 redeem 找不到未使用的码）
        String code = "legacy-code-01";
        ActivationCodeService.RedeemResult result =
                activationCodeService.redeem(code, "dev-hash", "10.0.0.1");
        assertEquals(ActivationCodeService.RedeemStatus.SUCCESS, result.status());
        // 同一码二次核销被拒绝
        assertEquals(ActivationCodeService.RedeemStatus.ALREADY_USED,
                activationCodeService.redeem(code, "dev-other", "10.0.0.2").status());

        // 清理
        activationCodeService.deleteCode(result.codeId());
    }

    @Test
    void directRedeemWithoutClaim() {
        // 导入后直接用明文核销（不经过申领流程）
        activationCodeService.batchImportCodes(List.<String[]>of(new String[]{"direct-code-01", null}));
        String expectedCode = "direct-code-01";
        ActivationCodeService.RedeemResult result =
                activationCodeService.redeem(expectedCode, "dev-direct", "10.0.0.3");
        assertEquals(ActivationCodeService.RedeemStatus.SUCCESS, result.status());

        // 清理
        activationCodeService.deleteCode(result.codeId());
    }

    @Test
    void unknownCodeIsInvalid() {
        assertEquals(ActivationCodeService.RedeemStatus.INVALID,
                activationCodeService.redeem("UNKNOWN-CODE-00000000000000000000000000000", "dev-d", "10.0.0.6").status());
        assertEquals(ActivationCodeService.RedeemStatus.INVALID,
                activationCodeService.redeem(null, "dev-d", "10.0.0.6").status());
    }
}
