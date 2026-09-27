package xyz.lukix.keygo.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.dto.BatchIdsRequest;
import xyz.lukix.keygo.dto.ExportRequest;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.entity.ActivationLog;
import xyz.lukix.keygo.entity.OperationLog;
import xyz.lukix.keygo.service.ActivationCodeService;
import xyz.lukix.keygo.service.ActivationLogService;
import xyz.lukix.keygo.service.OperationLogService;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 激活码管理接口（仅管理员可用）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/codes")
@RequiredArgsConstructor
public class AdminCodesController {

    /** 单次批量操作允许的 id 数量上限 */
    private static final int MAX_BATCH_SIZE = 100;
    /** 单次 Excel 导入允许的最大行数（防超大文件拖垮内存/事务） */
    private static final int MAX_IMPORT_ROWS = 5000;
    /** SXSSFWorkbook 滑动窗口大小：内存中保留的行数 */
    private static final int SXSS_WINDOW_SIZE = 100;

    private final ActivationCodeService activationCodeService;
    private final ActivationLogService activationLogService;
    private final OperationLogService operationLogService;

    /** 激活码列表（分页） */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        Page<ActivationCode> pageResult = activationCodeService.pageQuery(status, keyword, page, size);
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        Map<String, Object> result = new HashMap<>();
        result.put("codes", pageResult.getContent());
        result.put("total", stats.total());
        result.put("available", stats.available());
        result.put("used", stats.used());
        result.put("page", pageResult.getNumber() + 1);
        result.put("size", pageResult.getSize());
        result.put("totalPages", pageResult.getTotalPages());
        result.put("totalElements", pageResult.getTotalElements());
        return ApiResponse.ok(result);
    }

    /** 标记激活码为已使用 */
    @PatchMapping("/{id}/use")
    public ApiResponse<Void> markAsUsed(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        if (!activationCodeService.markAsUsed(id)) {
            return ApiResponse.error(404, "激活码不存在");
        }
        String username = user != null ? user.getUsername() : null;
        activationCodeService.findById(id).ifPresent(c ->
                operationLogService.record(username, "MARK_USED", c.getCode(), null));
        return ApiResponse.ok("激活码已标记为已使用", null);
    }

    /** 标记激活码为未使用（恢复，同时清除领取人与设备绑定；仅超级管理员） */
    @PatchMapping("/{id}/unuse")
    public ApiResponse<Void> markAsUnused(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        if (!activationCodeService.markAsUnused(id)) {
            return ApiResponse.error(404, "激活码不存在");
        }
        String username = user != null ? user.getUsername() : null;
        activationCodeService.findById(id).ifPresent(c ->
                operationLogService.record(username, "MARK_UNUSED", c.getCode(), null));
        return ApiResponse.ok("激活码已标记为未使用", null);
    }

    /** 更新激活码备注 */
    @PatchMapping("/{id}/remark")
    public ApiResponse<Void> updateRemark(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String remark = body.getOrDefault("remark", "");
        if (remark.length() > ActivationCode.MAX_REMARK_LENGTH) {
            remark = remark.substring(0, ActivationCode.MAX_REMARK_LENGTH);
        }
        activationCodeService.updateRemark(id, remark.isBlank() ? null : remark);
        return ApiResponse.ok("备注已更新", null);
    }

    /** Excel 批量导入激活码（支持第二列作为备注） */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file,
                                                        @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "请选择非空的 Excel 文件");
        }
        List<String[]> rows = new ArrayList<>();
        try (var is = file.getInputStream();
             var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(is)) {
            // DataFormatter 按单元格展示值读取，避免数字型激活码单元格 getStringCellValue 直接抛异常
            DataFormatter formatter = new DataFormatter();
            var sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            for (int i = 0; i <= lastRowNum; i++) {
                var row = sheet.getRow(i);
                if (row == null) continue;
                var cell = row.getCell(0);
                if (cell != null) {
                    String val = formatter.formatCellValue(cell);
                    if (val != null && !val.isBlank()) {
                        String remark = null;
                        var remarkCell = row.getCell(1);
                        if (remarkCell != null) {
                            String rv = formatter.formatCellValue(remarkCell);
                            if (rv != null && !rv.isBlank()) remark = rv.trim();
                        }
                        rows.add(new String[]{val.trim(), remark});
                        if (rows.size() > MAX_IMPORT_ROWS) {
                            return ApiResponse.error(400, "单次导入最多 " + MAX_IMPORT_ROWS + " 行，请拆分文件");
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 不回传原始异常消息，避免泄露服务器内部信息
            log.error("Excel 导入解析失败，操作者={}", username, e);
            return ApiResponse.error(500, "导入失败，请检查文件格式后重试");
        }
        // 去重（文件内重复/库内已存在）在服务层处理，重复项跳过而非整单失败
        ActivationCodeService.ImportResult importResult = activationCodeService.batchImportCodes(rows);
        operationLogService.record(username, "IMPORT", null, "导入 " + importResult.imported() + " 个激活码");
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        Map<String, Object> result = new HashMap<>();
        result.put("count", importResult.imported());
        result.put("skipped", importResult.skipped());
        result.put("total", stats.total());
        result.put("available", stats.available());
        result.put("used", stats.used());
        return ApiResponse.ok(result);
    }

    /**
     * Excel 导出（POST body 传筛选条件，避免筛选参数暴露在 URL/Nginx 日志中）
     * body: {"status": "used|unused", "keyword": "xxx"}，两者均可省略
     */
    @PostMapping("/export")
    public void export(@RequestBody(required = false) ExportRequest body,
                       HttpServletResponse response) {
        String status = body != null ? body.status() : null;
        String keyword = body != null ? body.keyword() : null;

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

        // SXSSF 流式写出（内存仅保留滑动窗口行），避免大表导出时全量 DOM 驻留内存
        try (var workbook = new SXSSFWorkbook(SXSS_WINDOW_SIZE)) {
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
            // 写入失败时响应可能已部分发送，无法再返回错误体，仅记录日志供排查
            log.error("激活码导出失败", e);
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
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("total", stats.total());
        result.put("available", stats.available());
        result.put("used", stats.used());
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
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("total", stats.total());
        result.put("available", stats.available());
        result.put("used", stats.used());
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
        Optional<ActivationCode> found = activationCodeService.findById(id);
        if (found.isEmpty()) {
            return ApiResponse.error(404, "激活码不存在");
        }
        String username = user != null ? user.getUsername() : null;
        operationLogService.record(username, "DELETE", found.get().getCode(), null);
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
     * 查询核销审计日志（分页，时间倒序，仅管理员）：
     * result 可选过滤（SUCCESS/INVALID/ALREADY_USED/EXPIRED）；仅返回哈希，不含明文
     */
    @GetMapping("/redeem-logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<Map<String, Object>> redeemLogs(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @RequestParam(required = false) String result) {
        Page<ActivationLog> logs = activationLogService.pageLogs(page, size, result);
        Map<String, Object> data = new HashMap<>();
        data.put("logs", logs.getContent());
        data.put("page", logs.getNumber() + 1);
        data.put("size", logs.getSize());
        data.put("totalPages", logs.getTotalPages());
        data.put("totalElements", logs.getTotalElements());
        return ApiResponse.ok(data);
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
