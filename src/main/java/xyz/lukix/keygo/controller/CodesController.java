package xyz.lukix.keygo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import xyz.lukix.keygo.common.ApiResponse;
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

    private final ActivationCodeService activationCodeService;
    private final OperationLogService operationLogService;
    private final RateLimitService rateLimitService;

    /** 公共统计 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> result = new HashMap<>();
        result.put("total", activationCodeService.countTotal());
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
        return ApiResponse.ok(result);
    }

    /**
     * 申领一个激活码（每用户每分钟限 5 次）
     * body 可选字段 unit（使用单位）：非空时写入该激活码备注，缺省或为空时备注保持原值
     */
    @PostMapping("/claim")
    public ApiResponse<Map<String, Object>> claim(@RequestBody(required = false) Map<String, String> body,
                                                  @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        if (!rateLimitService.tryAcquire("claim:" + username, CLAIM_LIMIT, CLAIM_WINDOW)) {
            return ApiResponse.error(429, "申领过于频繁，请1分钟后再试");
        }
        String unit = body != null ? body.get("unit") : null;
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
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
        result.put("total", activationCodeService.countTotal());
        return ApiResponse.ok("申领成功", result);
    }

    /** 批量申领 N 个激活码（与单次申领共享限流配额） */
    @PostMapping("/batch-claim")
    public ApiResponse<Map<String, Object>> batchClaim(@RequestBody Map<String, Integer> body,
                                                       @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        int count = body.getOrDefault("count", 1);
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
        result.put("count", codes.size());
        result.put("codes", codes);
        result.put("total", activationCodeService.countTotal());
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
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
}
