package xyz.lukix.keygo.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核销全链路测试（H2 test profile）：hash/明文双路径核销、防重复核销、未知码拦截
 */
@SpringBootTest
@ActiveProfiles("test")
class RedemptionFlowTest {

    @Autowired
    private ActivationCodeService activationCodeService;

    @Test
    void legacyPlainCodeRedeemedViaFallbackMatch() {
        activationCodeService.batchImportCodes(List.<String[]>of(new String[]{"legacy-code-01", null}));
        // 输入小写带空白，规范化后按明文降级命中（导入时统一存大写）
        ActivationCodeService.RedeemResult result =
                activationCodeService.redeem(" legacy-code-01 ", "dev-legacy", "10.0.0.1");
        assertEquals(ActivationCodeService.RedeemStatus.SUCCESS, result.status());
        // 同一码二次核销被拒绝（行锁 + used 过滤防重复核销）
        assertEquals(ActivationCodeService.RedeemStatus.ALREADY_USED,
                activationCodeService.redeem("LEGACY-CODE-01", "dev-other", "10.0.0.2").status());
    }

    @Test
    void unknownCodeIsInvalid() {
        assertEquals(ActivationCodeService.RedeemStatus.INVALID,
                activationCodeService.redeem("KKKKK-JJJJJ-LLLLL-MMMMM", "dev-d", "10.0.0.6").status());
        assertEquals(ActivationCodeService.RedeemStatus.INVALID,
                activationCodeService.redeem(null, "dev-d", "10.0.0.6").status());
    }
}
