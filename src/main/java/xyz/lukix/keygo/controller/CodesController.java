package xyz.lukix.keygo.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.dto.BatchClaimRequest;
import xyz.lukix.keygo.dto.ClaimRequest;
import xyz.lukix.keygo.dto.RedeemRequest;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.service.ActivationCodeService;
import xyz.lukix.keygo.service.OperationLogService;
import xyz.lukix.keygo.service.RateLimitService;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 激活码公共接口（认证用户可用）
 */
@RestController
@RequestMapping("/api/v1/codes")
@RequiredArgsConstructor
public class CodesController {

    /** 申领限流：每用户每分钟最多 5 次（单次申领与批量申领共享配额） */
    private static final int CLAIM_LIMIT = 5;
    private static final Duration CLAIM_WINDOW = Duration.ofMinutes(1);
    /** 核销限流：每 IP 每分钟最多 15 次（外部软件无人值守重试留了余量） */
    private static final int REDEEM_LIMIT = 15;
    /** 对外统一的失败话术：不区分"不存在/已核销"，防止接口被用作码有效性探测；审计日志中仍精确区分 */
    private static final String REDEEM_FAIL_MESSAGE = "激活码无效或已被核销";

    private final ActivationCodeService activationCodeService;
    private final OperationLogService operationLogService;
    private final RateLimitService rateLimitService;

    /** 公共统计 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        Map<String, Object> result = new HashMap<>();
        result.put("total", stats.total());
        result.put("available", stats.available());
        result.put("used", stats.used());
        return ApiResponse.ok(result);
    }

    /**
     * 申领一个激活码（每用户每分钟限 5 次）
     * body 可选字段 unit（使用单位）：非空时写入该激活码备注，缺省或为空时备注保持原值
     */
    @PostMapping("/claim")
    public ApiResponse<Map<String, Object>> claim(@RequestBody(required = false) ClaimRequest body,
                                                  @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        if (!rateLimitService.tryAcquire("claim:" + username, CLAIM_LIMIT, CLAIM_WINDOW)) {
            return ApiResponse.error(429, "申领过于频繁，请1分钟后再试");
        }
        String unit = body != null ? body.unit() : null;
        Optional<ActivationCode> codeOpt = activationCodeService.claimCode(username, unit);
        Map<String, Object> result = new HashMap<>();
        if (codeOpt.isPresent()) {
            ActivationCode code = codeOpt.get();
            operationLogService.record(username, "CLAIM", code.getCode(),
                    unit != null && !unit.isBlank() ? "单位: " + unit.trim() : null);
            result.put("code", code.getCode());
            result.put("fetchTime", code.getFetchTime());
        } else {
            return ApiResponse.error(404, "暂无可用激活码，请联系管理员补充");
        }
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        result.put("available", stats.available());
        result.put("used", stats.used());
        result.put("total", stats.total());
        return ApiResponse.ok("申领成功", result);
    }

    /** 批量申领 N 个激活码（与单次申领共享限流配额） */
    @PostMapping("/batch-claim")
    public ApiResponse<Map<String, Object>> batchClaim(@RequestBody BatchClaimRequest body,
                                                       @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        int count = body.count() != null ? body.count() : 1;
        if (count < 1 || count > 100) {
            return ApiResponse.error(400, "数量范围 1-100");
        }
        if (!rateLimitService.tryAcquire("claim:" + username, CLAIM_LIMIT, CLAIM_WINDOW)) {
            return ApiResponse.error(429, "申领过于频繁，请1分钟后再试");
        }
        List<ActivationCode> claimed = activationCodeService.batchClaim(count, username);
        List<String> codes = claimed.stream().map(ActivationCode::getCode).toList();
        operationLogService.record(username, "BATCH_CLAIM", null, "批量获取 " + codes.size() + " 个");
        Map<String, Object> result = new HashMap<>();
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        result.put("count", codes.size());
        result.put("codes", codes);
        result.put("total", stats.total());
        result.put("available", stats.available());
        result.put("used", stats.used());
        return ApiResponse.ok(result);
    }

    /** 我的获取记录（分页） */
    @GetMapping("/my-codes")
    public ApiResponse<Map<String, Object>> myCodes(@AuthenticationPrincipal UserDetails user,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        String username = user != null ? user.getUsername() : null;
        Page<ActivationCode> pageResult = activationCodeService.pageMyCodes(username, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("codes", pageResult.getContent());
        result.put("page", pageResult.getNumber() + 1);
        result.put("size", pageResult.getSize());
        result.put("totalPages", pageResult.getTotalPages());
        result.put("totalElements", pageResult.getTotalElements());
        return ApiResponse.ok(result);
    }

    /**
     * 设备核销激活码（供外部软件调用，无登录态，SecurityConfig 已放行）：
     * 按 IP 限流；成功仅回 codeId，失败统一话术，真实结果只进异步审计日志
     */
    @PostMapping("/redeem")
    public ApiResponse<Map<String, Object>> redeem(@RequestBody(required = false) RedeemRequest body,
                                                   HttpServletRequest request) {
        String ip = rateLimitService.resolveClientIp(request);
        if (!rateLimitService.tryAcquire("redeem:" + ip, REDEEM_LIMIT, CLAIM_WINDOW)) {
            return ApiResponse.error(429, "核销过于频繁，请1分钟后再试");
        }
        ActivationCodeService.RedeemResult result = activationCodeService.redeem(
                body != null ? body.code() : null, body != null ? body.deviceId() : null, ip);
        return switch (result.status()) {
            case SUCCESS -> {
                Map<String, Object> data = new HashMap<>();
                data.put("codeId", result.codeId());
                yield ApiResponse.ok("核销成功", data);
            }
            case EXPIRED -> ApiResponse.error(400, "该激活码已过期");
            default -> ApiResponse.error(400, REDEEM_FAIL_MESSAGE);
        };
    }
}
