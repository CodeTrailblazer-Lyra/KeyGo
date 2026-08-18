package xyz.lukix.keygo.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.dto.BatchIdsRequest;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.entity.OperationLog;
import xyz.lukix.keygo.service.ActivationCodeService;
import xyz.lukix.keygo.service.OperationLogService;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 激活码管理接口（仅管理员可用）
 */
@RestController
@RequestMapping("/api/v1/admin/codes")
@RequiredArgsConstructor
public class AdminCodesController {

    /** 单次批量操作允许的 id 数量上限 */
    private static final int MAX_BATCH_SIZE = 100;

    private final ActivationCodeService activationCodeService;
    private final OperationLogService operationLogService;

    /** 激活码列表（分页） */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        Page<ActivationCode> pageResult = activationCodeService.pageQuery(status, keyword, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("codes", pageResult.getContent());
        result.put("total", activationCodeService.countTotal());
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
        result.put("page", pageResult.getNumber() + 1);
        result.put("size", pageResult.getSize());
        result.put("totalPages", pageResult.getTotalPages());
        result.put("totalElements", pageResult.getTotalElements());
        return ApiResponse.ok(result);
    }

    /** 标记激活码为已使用 */
    @PatchMapping("/{id}/use")
    public ApiResponse<Void> markAsUsed(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        activationCodeService.markAsUsed(id);
        activationCodeService.findById(id).ifPresent(c ->
                operationLogService.record(username, "MARK_USED", c.getCode(), null));
        return ApiResponse.ok("激活码已标记为已使用", null);
    }

    /** 标记激活码为未使用（仅超级管理员） */
    @PatchMapping("/{id}/unuse")
    public ApiResponse<Void> markAsUnused(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        activationCodeService.markAsUnused(id);
        activationCodeService.findById(id).ifPresent(c ->
                operationLogService.record(username, "MARK_UNUSED", c.getCode(), null));
        return ApiResponse.ok("激活码已标记为未使用", null);
    }

    /** 更新激活码备注 */
    @PatchMapping("/{id}/remark")
    public ApiResponse<Void> updateRemark(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String remark = body.getOrDefault("remark", "");
        if (remark.length() > 200) remark = remark.substring(0, 200);
        activationCodeService.updateRemark(id, remark.isBlank() ? null : remark);
        return ApiResponse.ok("备注已更新", null);
    }

    /** Excel 批量导入激活码（支持第二列作为备注） */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file,
                                                        @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        try {
            List<String[]> rows = new ArrayList<>();
            try (var is = file.getInputStream();
                 var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(is)) {
                var sheet = workbook.getSheetAt(0);
                int lastRowNum = sheet.getLastRowNum();
                for (int i = 0; i <= lastRowNum; i++) {
                    var row = sheet.getRow(i);
                    if (row == null) continue;
                    var cell = row.getCell(0);
                    if (cell != null) {
                        String val = cell.getStringCellValue();
                        if (val != null && !val.isBlank()) {
                            String remark = null;
                            var remarkCell = row.getCell(1);
                            if (remarkCell != null) {
                                String rv = remarkCell.getStringCellValue();
                                if (rv != null && !rv.isBlank()) remark = rv.trim();
                            }
                            rows.add(new String[]{val.trim(), remark});
                        }
                    }
                }
            }
            int count = activationCodeService.batchImportCodes(rows);
            operationLogService.record(username, "IMPORT", null, "导入 " + count + " 个激活码");
            Map<String, Object> result = new HashMap<>();
            result.put("count", count);
            result.put("total", activationCodeService.countTotal());
            result.put("available", activationCodeService.countAvailable());
            result.put("used", activationCodeService.countUsed());
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.error(500, "导入失败: " + e.getMessage());
        }
    }

    /**
     * Excel 导出（POST body 传筛选条件，避免筛选参数暴露在 URL/Nginx 日志中）
     * body: {"status": "used|unused", "keyword": "xxx"}，两者均可省略
     */
    @PostMapping("/export")
    public void export(@RequestBody(required = false) Map<String, String> body,
                       HttpServletResponse response) {
        String status = body != null ? body.get("status") : null;
        String keyword = body != null ? body.get("keyword") : null;

        List<ActivationCode> codes;
        if (keyword != null && !keyword.isBlank()) {
            codes = activationCodeService.searchByCode(keyword);
        } else if ("used".equals(status)) {
            codes = activationCodeService.listByUsed(true);
        } else if ("unused".equals(status)) {
            codes = activationCodeService.listByUsed(false);
        } else {
            codes = activationCodeService.listAll();
        }

        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("激活码");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("#");
            header.createCell(1).setCellValue("激活码");
            header.createCell(2).setCellValue("状态");
            header.createCell(3).setCellValue("备注");
            header.createCell(4).setCellValue("获取人");
            header.createCell(5).setCellValue("获取时间");
            header.createCell(6).setCellValue("创建时间");
            for (int i = 0; i < codes.size(); i++) {
                var row = sheet.createRow(i + 1);
                ActivationCode c = codes.get(i);
                row.createCell(0).setCellValue(c.getId());
                row.createCell(1).setCellValue(c.getCode());
                row.createCell(2).setCellValue(Boolean.TRUE.equals(c.getUsed()) ? "已使用" : "未使用");
                row.createCell(3).setCellValue(c.getRemark() != null ? c.getRemark() : "");
                row.createCell(4).setCellValue(c.getFetchUser() != null ? c.getFetchUser() : "");
                row.createCell(5).setCellValue(c.getFetchTime() != null ? c.getFetchTime().toString() : "");
                row.createCell(6).setCellValue(c.getCreateTime() != null ? c.getCreateTime().toString() : "");
            }
            String filename = URLEncoder.encode("激活码导出.xlsx", StandardCharsets.UTF_8);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
            }
        } catch (Exception e) {
            // 写入失败时响应已部分发送，无法恢复
        }
    }

    /** 批量标记激活码为已使用 */
    @PatchMapping("/batch/use")
    public ApiResponse<Map<String, Object>> batchUse(@RequestBody BatchIdsRequest req,
                                                     @AuthenticationPrincipal UserDetails user) {
        ApiResponse<List<Long>> check = validateIds(req);
        if (check.code() != 0) {
            return ApiResponse.error(check.code(), check.message());
        }
        String username = user != null ? user.getUsername() : null;
        int count = activationCodeService.batchMarkAsUsed(check.data());
        operationLogService.record(username, "BATCH_MARK_USED", null, "批量标记 " + count + " 个");
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("total", activationCodeService.countTotal());
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
        return ApiResponse.ok(result);
    }

    /** 批量标记激活码为未使用 */
    @PatchMapping("/batch/unuse")
    public ApiResponse<Map<String, Object>> batchUnuse(@RequestBody BatchIdsRequest req,
                                                       @AuthenticationPrincipal UserDetails user) {
        ApiResponse<List<Long>> check = validateIds(req);
        if (check.code() != 0) {
            return ApiResponse.error(check.code(), check.message());
        }
        String username = user != null ? user.getUsername() : null;
        int count = activationCodeService.batchMarkAsUnused(check.data());
        operationLogService.record(username, "BATCH_MARK_UNUSED", null, "批量恢复 " + count + " 个");
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("total", activationCodeService.countTotal());
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
        return ApiResponse.ok(result);
    }

    /** 批量删除激活码（仅超级管理员） */
    @PostMapping("/batch-delete")
    public ApiResponse<Map<String, Object>> batchDelete(@RequestBody BatchIdsRequest req,
                                                        @AuthenticationPrincipal UserDetails user) {
        ApiResponse<List<Long>> check = validateIds(req);
        if (check.code() != 0) {
            return ApiResponse.error(check.code(), check.message());
        }
        String username = user != null ? user.getUsername() : null;
        int count = activationCodeService.batchDeleteCodes(check.data());
        operationLogService.record(username, "BATCH_DELETE", null, "批量删除 " + count + " 个");
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        return ApiResponse.ok(result);
    }

    /** 删除激活码（仅超级管理员） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCode(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        activationCodeService.findById(id).ifPresent(c ->
                operationLogService.record(username, "DELETE", c.getCode(), null));
        activationCodeService.deleteCode(id);
        return ApiResponse.ok("激活码已删除", null);
    }

    /** 查询操作日志（分页，仅管理员及以上角色可查看） */
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<Map<String, Object>> logs(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        Page<OperationLog> logs = operationLogService.pageLogs(page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("logs", logs.getContent());
        result.put("page", logs.getNumber() + 1);
        result.put("totalPages", logs.getTotalPages());
        result.put("totalElements", logs.getTotalElements());
        return ApiResponse.ok(result);
    }

    /**
     * 批量操作参数校验：非空、去重、数量上限
     */
    private ApiResponse<List<Long>> validateIds(BatchIdsRequest req) {
        if (req == null || req.ids() == null || req.ids().isEmpty()) {
            return ApiResponse.error(400, "请选择要操作的激活码");
        }
        List<Long> ids = req.ids().stream().distinct().toList();
        if (ids.size() > MAX_BATCH_SIZE) {
            return ApiResponse.error(400, "单次批量操作最多 " + MAX_BATCH_SIZE + " 条");
        }
        return ApiResponse.ok(ids);
    }
}
