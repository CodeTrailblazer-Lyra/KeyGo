package xyz.lukix.keygo.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.common.AppConstants;
import xyz.lukix.keygo.dto.*;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.service.ActivationCodeService;
import xyz.lukix.keygo.service.OperationLogService;
import xyz.lukix.keygo.service.RateLimitService;

import java.util.List;
import java.util.Optional;

/**
 * 激活码公共接口（认证用户可用）
 */
@RestController
@RequestMapping("/api/v1/codes")
@RequiredArgsConstructor
public class CodesController {

    private final ActivationCodeService activationCodeService;
    private final OperationLogService operationLogService;
    private final RateLimitService rateLimitService;

    /**
     * 公共统计
     */
    @GetMapping("/stats")
    public ApiResponse<StatsResponse> stats() {
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        return ApiResponse.ok(toStatsResponse(stats));
    }

    /**
     * 申领一个激活码（每用户每分钟限 5 次）
     * body 可选字段 unit（使用单位）：非空时写入该激活码备注，缺省或为空时备注保持原值。
     * 返回明文激活码，用户持此值进行核销。
     */
    @PostMapping("/claim")
    public ApiResponse<ClaimResponse> claim(@RequestBody(required = false) ClaimRequest body,
                                            @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        if (!rateLimitService.tryAcquire("claim:" + username, AppConstants.CLAIM_LIMIT, AppConstants.CLAIM_WINDOW)) {
            return ApiResponse.error(429, "申领过于频繁，请1分钟后再试");
        }
        String unit = body != null ? body.unit() : null;
        Optional<ActivationCode> codeOpt = activationCodeService.claimCode(username, unit);
        if (codeOpt.isEmpty()) {
            return ApiResponse.error(404, "暂无可用激活码，请联系管理员补充");
        }
        ActivationCode code = codeOpt.get();
        operationLogService.record(username, "CLAIM", code.getCode(),
                unit != null && !unit.isBlank() ? "单位: " + unit.trim() : null);
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        return ApiResponse.ok(AppConstants.CLAIM_SUCCESS_MESSAGE,
                new ClaimResponse(code.getCode(), code.getFetchTime(), toStatsResponse(stats)));
    }

    /**
     * 批量申领 N 个激活码（与单次申领共享限流配额）
     */
    @PostMapping("/batch-claim")
    public ApiResponse<BatchClaimResponse> batchClaim(@RequestBody BatchClaimRequest body,
                                                      @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        int count = body.count() != null ? body.count() : 1;
        if (count < 1 || count > AppConstants.MAX_BATCH_CLAIM_COUNT) {
            return ApiResponse.error(400, "数量范围 1-" + AppConstants.MAX_BATCH_CLAIM_COUNT);
        }
        if (!rateLimitService.tryAcquire("claim:" + username, AppConstants.CLAIM_LIMIT, AppConstants.CLAIM_WINDOW)) {
            return ApiResponse.error(429, "申领过于频繁，请1分钟后再试");
        }
        List<ActivationCode> claimed = activationCodeService.batchClaim(count, username);
        List<String> codes = claimed.stream().map(ActivationCode::getCode).toList();
        operationLogService.record(username, "BATCH_CLAIM", null, "批量获取 " + codes.size() + " 个");
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        return ApiResponse.ok(new BatchClaimResponse(codes.size(), codes, toStatsResponse(stats)));
    }

    /**
     * 我的获取记录（分页）
     */
    @GetMapping("/my-codes")
    public ApiResponse<PageResponse<CodeResponse>> myCodes(@AuthenticationPrincipal UserDetails user,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "" + AppConstants.DEFAULT_PAGE_SIZE) int size) {
        String username = user != null ? user.getUsername() : null;
        Page<ActivationCode> pageResult = activationCodeService.pageMyCodes(username, page, size);
        Page<CodeResponse> responsePage = pageResult.map(CodeResponse::from);
        return ApiResponse.ok(PageResponse.from(responsePage));
    }

    /**
     * 设备核销激活码（供外部软件调用，无登录态，SecurityConfig 已放行）：
     * 按 IP 限流；body.code 传激活码明文。
     * 成功仅回 codeId，失败统一话术，真实结果只进异步审计日志。
     */
    @PostMapping("/redeem")
    public ApiResponse<RedeemResponse> redeem(@RequestBody(required = false) RedeemRequest body,
                                              HttpServletRequest request) {
        String ip = rateLimitService.resolveClientIp(request);
        if (!rateLimitService.tryAcquire("redeem:" + ip, AppConstants.REDEEM_LIMIT, AppConstants.CLAIM_WINDOW)) {
            return ApiResponse.error(429, "核销过于频繁，请1分钟后再试");
        }
        ActivationCodeService.RedeemResult result = activationCodeService.redeem(
                body != null ? body.code() : null, body != null ? body.deviceId() : null, ip);
        return switch (result.status()) {
            case SUCCESS -> ApiResponse.ok(AppConstants.REDEEM_SUCCESS_MESSAGE, new RedeemResponse(result.codeId()));
            case EXPIRED -> ApiResponse.error(400, "该激活码已过期");
            default -> ApiResponse.error(400, AppConstants.REDEEM_FAIL_MESSAGE);
        };
    }

    private StatsResponse toStatsResponse(ActivationCodeService.Stats stats) {
        return new StatsResponse(stats.total(), stats.available(), stats.used());
    }

    public record RedeemResponse(Long codeId) {
    }
}
