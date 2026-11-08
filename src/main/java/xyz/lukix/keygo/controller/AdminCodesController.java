package xyz.lukix.keygo.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.common.AppConstants;
import xyz.lukix.keygo.dto.*;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.entity.ActivationLog;
import xyz.lukix.keygo.entity.OperationLog;
import xyz.lukix.keygo.service.ActivationCodeService;
import xyz.lukix.keygo.service.ActivationLogService;
import xyz.lukix.keygo.service.OfficialLicenseService;
import xyz.lukix.keygo.service.OperationLogService;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 激活码管理接口（仅管理员可用）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/codes")
@RequiredArgsConstructor
public class AdminCodesController {

    private final ActivationCodeService activationCodeService;
    private final ActivationLogService activationLogService;
    private final OperationLogService operationLogService;
    private final OfficialLicenseService officialLicenseService;

    /**
     * 激活码列表（分页）
     */
    @GetMapping
    public ApiResponse<AdminCodeListResponse> list(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "" + AppConstants.DEFAULT_PAGE_SIZE) int size) {
        Page<ActivationCode> pageResult = activationCodeService.pageQuery(status, keyword, page, size);
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        Page<CodeResponse> codePage = pageResult.map(CodeResponse::adminFrom);
        return ApiResponse.ok(new AdminCodeListResponse(
                PageResponse.from(codePage),
                toStatsResponse(stats)
        ));
    }

    /**
     * 标记激活码为已使用
     */
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

    /**
     * 标记激活码为未使用（恢复，同时清除领取人与设备绑定；仅超级管理员）
     */
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

    /**
     * 更新激活码备注
     */
    @PatchMapping("/{id}/remark")
    public ApiResponse<Void> updateRemark(@PathVariable Long id, @RequestBody UpdateRemarkRequest body) {
        String remark = body.remark() != null ? body.remark() : "";
        if (remark.length() > ActivationCode.MAX_REMARK_LENGTH) {
            remark = remark.substring(0, ActivationCode.MAX_REMARK_LENGTH);
        }
        activationCodeService.updateRemark(id, remark.isBlank() ? null : remark);
        return ApiResponse.ok("备注已更新", null);
    }

    /**
     * Excel 批量导入激活码（支持第二列作为备注）
     */
    @PostMapping("/import")
    public ApiResponse<ImportResponse> importExcel(@RequestParam("file") MultipartFile file,
                                                   @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "请选择非空的 Excel 文件");
        }

        List<String[]> rows;
        try {
            rows = parseExcelFile(file);
        } catch (ExcelParseException e) {
            log.error("Excel 导入解析失败，操作者={}", username, e.getCause());
            return ApiResponse.error(500, "导入失败，请检查文件格式后重试");
        }

        if (rows.size() > AppConstants.MAX_IMPORT_ROWS) {
            return ApiResponse.error(400, "单次导入最多 " + AppConstants.MAX_IMPORT_ROWS + " 行，请拆分文件");
        }

        ActivationCodeService.ImportResult importResult = activationCodeService.batchImportCodes(rows);
        operationLogService.record(username, "IMPORT", null, "导入 " + importResult.imported() + " 个激活码");
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        return ApiResponse.ok(new ImportResponse(
                importResult.imported(),
                importResult.skipped(),
                toStatsResponse(stats)
        ));
    }

    /**
     * Excel 导出（POST body 传筛选条件，避免筛选参数暴露在 URL/Nginx 日志中）
     */
    @PostMapping("/export")
    public void export(@RequestBody(required = false) ExportRequest body, HttpServletResponse response) {
        String status = body != null ? body.status() : null;
        String keyword = body != null ? body.keyword() : null;

        List<ActivationCode> codes = queryCodesForExport(status, keyword);
        writeExcelExport(codes, response);
    }

    /**
     * 批量标记激活码为已使用
     */
    @PatchMapping("/batch/use")
    public ApiResponse<BatchOperationResponse> batchUse(@RequestBody BatchIdsRequest req,
                                                        @AuthenticationPrincipal UserDetails user) {
        ApiResponse<List<Long>> check = validateIds(req);
        if (check.code() != 0) {
            return ApiResponse.error(check.code(), check.message());
        }
        String username = user != null ? user.getUsername() : null;
        int count = activationCodeService.batchMarkAsUsed(check.data());
        operationLogService.record(username, "BATCH_MARK_USED", null, "批量标记 " + count + " 个");
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        return ApiResponse.ok(BatchOperationResponse.of(count, toStatsResponse(stats)));
    }

    /**
     * 批量标记激活码为未使用
     */
    @PatchMapping("/batch/unuse")
    public ApiResponse<BatchOperationResponse> batchUnuse(@RequestBody BatchIdsRequest req,
                                                          @AuthenticationPrincipal UserDetails user) {
        ApiResponse<List<Long>> check = validateIds(req);
        if (check.code() != 0) {
            return ApiResponse.error(check.code(), check.message());
        }
        String username = user != null ? user.getUsername() : null;
        int count = activationCodeService.batchMarkAsUnused(check.data());
        operationLogService.record(username, "BATCH_MARK_UNUSED", null, "批量恢复 " + count + " 个");
        ActivationCodeService.Stats stats = activationCodeService.getStats();
        return ApiResponse.ok(BatchOperationResponse.of(count, toStatsResponse(stats)));
    }

    /**
     * 批量删除激活码（仅超级管理员）
     */
    @PostMapping("/batch-delete")
    public ApiResponse<BatchOperationResponse> batchDelete(@RequestBody BatchIdsRequest req,
                                                           @AuthenticationPrincipal UserDetails user) {
        ApiResponse<List<Long>> check = validateIds(req);
        if (check.code() != 0) {
            return ApiResponse.error(check.code(), check.message());
        }
        String username = user != null ? user.getUsername() : null;
        int count = activationCodeService.batchDeleteCodes(check.data());
        operationLogService.record(username, "BATCH_DELETE", null, "批量删除 " + count + " 个");
        return ApiResponse.ok(BatchOperationResponse.of(count));
    }

    /**
     * 删除激活码（仅超级管理员）
     */
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

    /**
     * 查询操作日志（分页，仅管理员及以上角色可查看）
     */
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<PageResponse<OperationLog>> logs(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "" + AppConstants.DEFAULT_PAGE_SIZE) int size) {
        Page<OperationLog> logs = operationLogService.pageLogs(page, size);
        return ApiResponse.ok(PageResponse.from(logs));
    }

    /**
     * 查询核销审计日志（分页，时间倒序，仅管理员）
     */
    @GetMapping("/redeem-logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<PageResponse<ActivationLog>> redeemLogs(@RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "" + AppConstants.DEFAULT_PAGE_SIZE) int size,
                                                               @RequestParam(required = false) String result) {
        Page<ActivationLog> logs = activationLogService.pageLogs(page, size, result);
        return ApiResponse.ok(PageResponse.from(logs));
    }

    // ==================== 官方联网验证 ====================

    /**
     * 发起全量官方验证（异步）：含未申领的码，跑完后刷新列表查看结果。
     * 已有批次在跑时返回 409，避免多个全量任务叠加把官方接口打到限流。
     */
    @PostMapping("/official-check")
    public ApiResponse<OfficialCheckResponse> startOfficialCheck(@AuthenticationPrincipal UserDetails user) {
        if (!officialLicenseService.tryBegin()) {
            return ApiResponse.error(409, "已有官方验证任务进行中，请稍后再试");
        }
        long total = activationCodeService.getStats().total();
        try {
            officialLicenseService.runCheckAsync(true);
        } catch (TaskRejectedException e) {
            // 提交失败时必须释放执行权，否则后续所有验证都会被永久判为“进行中”
            officialLicenseService.release();
            log.error("官方全量验证任务提交失败", e);
            return ApiResponse.error(500, "验证任务启动失败，请稍后重试");
        }
        String username = user != null ? user.getUsername() : null;
        operationLogService.record(username, "OFFICIAL_CHECK_ALL", null, "发起全量官方验证 " + total + " 个");
        return ApiResponse.ok("已发起全量验证，预计 " + officialLicenseService.estimateSeconds(total) + " 秒后完成",
                new OfficialCheckResponse(true, total));
    }

    /**
     * 单个激活码立即验证（同步），直接返回该码最新的官方状态。
     * 官方接口不可达时返回 502，此时库中仍为上一次结果。
     */
    @PostMapping("/{id}/official-check")
    public ApiResponse<CodeResponse> checkOneWithOfficial(@PathVariable Long id) {
        OfficialLicenseService.SingleCheckResult result = officialLicenseService.checkById(id);
        if (!result.found()) {
            return ApiResponse.error(404, "激活码不存在");
        }
        if (!result.checked()) {
            return ApiResponse.error(502, "官方验证接口暂时不可达，已保留上一次结果");
        }
        return ApiResponse.ok("验证完成", CodeResponse.adminFrom(result.code()));
    }

    /**
     * 官方验证批次是否进行中（前端可用于控制按钮置灰与轮询刷新）
     */
    @GetMapping("/official-check/running")
    public ApiResponse<Boolean> officialCheckRunning() {
        return ApiResponse.ok(officialLicenseService.isRunning());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 解析 Excel 文件：第一列=激活码，第二列=是否使用，第三列=使用人，第四列=使用单位
     */
    private List<String[]> parseExcelFile(MultipartFile file) throws ExcelParseException {
        List<String[]> rows = new ArrayList<>();
        try (var is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
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
                        String used = getCellString(row, 1, formatter);
                        String fetchUser = getCellString(row, 2, formatter);
                        String unit = getCellString(row, 3, formatter);
                        rows.add(new String[]{val.trim(), used, fetchUser, unit});
                    }
                }
            }
        } catch (Exception e) {
            throw new ExcelParseException("Excel 解析失败", e);
        }
        return rows;
    }

    /**
     * 安全读取单元格字符串值
     */
    private String getCellString(org.apache.poi.ss.usermodel.Row row, int cellIndex, DataFormatter formatter) {
        var cell = row.getCell(cellIndex);
        if (cell == null) return null;
        String rv = formatter.formatCellValue(cell);
        return (rv != null && !rv.isBlank()) ? rv.trim() : null;
    }

    /**
     * 根据筛选条件查询导出用的激活码
     */
    private List<ActivationCode> queryCodesForExport(String status, String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return activationCodeService.searchByRemark(keyword);
        } else if ("used".equals(status)) {
            return activationCodeService.listByUsed(true);
        } else if ("unused".equals(status)) {
            return activationCodeService.listByUsed(false);
        } else {
            return activationCodeService.listAll();
        }
    }

    /**
     * 写入 Excel 导出响应
     */
    private void writeExcelExport(List<ActivationCode> codes, HttpServletResponse response) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(AppConstants.SXSS_WINDOW_SIZE)) {
            var sheet = workbook.createSheet("激活码");
            writeExcelHeader(sheet);
            writeExcelData(sheet, codes);
            setExcelResponseHeaders(response);
            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
            }
        } catch (Exception e) {
            log.error("激活码导出失败", e);
        }
    }

    /**
     * 写入 Excel 表头：激活码 / 状态 / 使用人 / 使用单位 / 获取时间 / 创建时间
     */
    private void writeExcelHeader(org.apache.poi.ss.usermodel.Sheet sheet) {
        var header = sheet.createRow(0);
        header.createCell(0).setCellValue("#");
        header.createCell(1).setCellValue("激活码");
        header.createCell(2).setCellValue("状态");
        header.createCell(3).setCellValue("使用人");
        header.createCell(4).setCellValue("使用单位");
        header.createCell(5).setCellValue("获取时间");
        header.createCell(6).setCellValue("创建时间");
    }

    /**
     * 写入 Excel 数据行
     */
    private void writeExcelData(org.apache.poi.ss.usermodel.Sheet sheet, List<ActivationCode> codes) {
        for (int i = 0; i < codes.size(); i++) {
            var row = sheet.createRow(i + 1);
            ActivationCode c = codes.get(i);
            row.createCell(0).setCellValue(c.getId());
            row.createCell(1).setCellValue(c.getCode());
            row.createCell(2).setCellValue(Boolean.TRUE.equals(c.getUsed()) ? "已使用" : "未使用");
            row.createCell(3).setCellValue(c.getFetchUser() != null ? c.getFetchUser() : "");
            row.createCell(4).setCellValue(c.getRemark() != null ? c.getRemark() : "");
            row.createCell(5).setCellValue(c.getFetchTime() != null ? c.getFetchTime().toString() : "");
            row.createCell(6).setCellValue(c.getCreateTime() != null ? c.getCreateTime().toString() : "");
        }
    }

    /**
     * 设置 Excel 导出响应头
     */
    private void setExcelResponseHeaders(HttpServletResponse response) {
        String filename = URLEncoder.encode("激活码导出.xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
    }

    /**
     * 批量操作参数校验
     */
    private ApiResponse<List<Long>> validateIds(BatchIdsRequest req) {
        if (req == null || req.ids() == null || req.ids().isEmpty()) {
            return ApiResponse.error(400, "请选择要操作的激活码");
        }
        List<Long> ids = req.ids().stream().distinct().toList();
        if (ids.size() > AppConstants.MAX_BATCH_SIZE) {
            return ApiResponse.error(400, "单次批量操作最多 " + AppConstants.MAX_BATCH_SIZE + " 条");
        }
        return ApiResponse.ok(ids);
    }

    /**
     * 将服务层统计结果转换为响应 DTO
     */
    private StatsResponse toStatsResponse(ActivationCodeService.Stats stats) {
        return new StatsResponse(stats.total(), stats.available(), stats.used());
    }

    // ==================== 内部类 ====================

    /**
     * Excel 解析异常
     */
    private static class ExcelParseException extends Exception {
        public ExcelParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 更新备注请求
     */
    public record UpdateRemarkRequest(String remark) {
    }

    /**
     * 发起全量官方验证的结果
     *
     * @param accepted 是否已受理并开始异步执行
     * @param total    本次纳入验证的激活码总数
     */
    public record OfficialCheckResponse(boolean accepted, long total) {
    }

    /**
     * 管理端激活码列表响应（包含分页数据和统计信息）
     */
    public record AdminCodeListResponse(
            PageResponse<CodeResponse> codes,
            StatsResponse stats
    ) {
    }
}
