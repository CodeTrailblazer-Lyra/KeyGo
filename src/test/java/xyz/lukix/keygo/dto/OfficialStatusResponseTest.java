package xyz.lukix.keygo.dto;

import org.junit.jupiter.api.Test;
import xyz.lukix.keygo.entity.ActivationCode;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 官方验证状态文案推导：状态取值优先级与字段透传，纯逻辑无外部依赖。
 */
class OfficialStatusResponseTest {

    private static final LocalDateTime CHECKED_AT = LocalDateTime.of(2026, 11, 8, 10, 0);

    /** 构造一条带官方字段的激活码 */
    private static ActivationCode code(Integer useStatus, Integer officialCode, LocalDateTime checkedAt) {
        ActivationCode entity = new ActivationCode();
        entity.setCode("9AAAU9AAY3PKNUX5DMHLZQ8XR");
        entity.setOfficialUseStatus(useStatus);
        entity.setOfficialCode(officialCode);
        entity.setOfficialCheckedAt(checkedAt);
        return entity;
    }

    @Test
    void neverCheckedShowsUnverified() {
        // 即便残留了历史字段值，只要从未验证成功就一律显示"未验证"
        OfficialStatusResponse view = OfficialStatusResponse.from(code(1, 0, null));
        assertEquals("未验证", view.useStatusText());
    }

    @Test
    void nonZeroOfficialCodeShowsInvalidCode() {
        assertEquals("无效码", OfficialStatusResponse.from(code(null, 40404, CHECKED_AT)).useStatusText());
    }

    @Test
    void zeroCodeButMissingUseStatusShowsQueryError() {
        assertEquals("查询异常", OfficialStatusResponse.from(code(null, 0, CHECKED_AT)).useStatusText());
    }

    @Test
    void useStatusOneMeansUsed() {
        assertEquals("已使用", OfficialStatusResponse.from(code(1, 0, CHECKED_AT)).useStatusText());
    }

    @Test
    void otherUseStatusMeansUnused() {
        assertEquals("未使用", OfficialStatusResponse.from(code(0, 0, CHECKED_AT)).useStatusText());
        assertEquals("未使用", OfficialStatusResponse.from(code(2, 0, CHECKED_AT)).useStatusText());
    }

    @Test
    void businessFieldsArePassedThrough() {
        LocalDateTime useTime = LocalDateTime.of(2026, 11, 1, 8, 30);
        ActivationCode entity = code(1, 0, CHECKED_AT);
        entity.setOfficialUseTime(useTime);
        entity.setOfficialProduct("统信桌面操作系统 V20 专业版 全架构");
        entity.setOfficialDevice("联想 / ThinkPad X1");

        OfficialStatusResponse view = OfficialStatusResponse.from(entity);
        assertEquals(1, view.useStatus());
        assertEquals(useTime, view.useTime());
        assertEquals("统信桌面操作系统 V20 专业版 全架构", view.product());
        assertEquals("联想 / ThinkPad X1", view.device());
        assertEquals(0, view.officialCode());
        assertEquals(CHECKED_AT, view.checkedAt());
    }
}
