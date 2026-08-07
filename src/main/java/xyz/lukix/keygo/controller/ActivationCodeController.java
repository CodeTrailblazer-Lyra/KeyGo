package xyz.lukix.keygo.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.entity.Announcement;
import xyz.lukix.keygo.entity.OperationLog;
import xyz.lukix.keygo.service.ActivationCodeService;
import xyz.lukix.keygo.service.AnnouncementService;
import xyz.lukix.keygo.service.OperationLogService;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping("/admin/code")
@RequiredArgsConstructor
public class ActivationCodeController {

    private final ActivationCodeService activationCodeService;
    private final OperationLogService operationLogService;
    private final AnnouncementService announcementService;

    /**
     * 激活码申领页面（Vue SPA 入口）
     */
    @GetMapping("/fetch")
    public String fetchPage(Model model) {
        model.addAttribute("total", activationCodeService.countTotal());
        model.addAttribute("available", activationCodeService.countAvailable());
        model.addAttribute("used", activationCodeService.countUsed());
        return "admin/code/fetch";
    }

    /**
     * 执行申领操作（传统表单 POST，保留兼容）
     */
    @PostMapping("/claim")
    public String claimCode(@AuthenticationPrincipal UserDetails user, RedirectAttributes redirectAttributes) {
        String username = user != null ? user.getUsername() : null;
        Optional<ActivationCode> codeOpt = activationCodeService.claimCode(username);
        if (codeOpt.isPresent()) {
            operationLogService.record(username, "CLAIM", codeOpt.get().getCode(), null);
            redirectAttributes.addFlashAttribute("claimedCode", codeOpt.get().getCode());
            redirectAttributes.addFlashAttribute("success", true);
        } else {
            redirectAttributes.addFlashAttribute("error", "暂无可用激活码，请联系管理员补充");
        }
        return "redirect:/admin/code/fetch";
    }

    // ========== Vue SPA JSON API ==========

    /**
     * AJAX 申领（JSON）
     */
    @PostMapping("/claim-json")
    @ResponseBody
    public Map<String, Object> claimCodeJson(@AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        Map<String, Object> result = new HashMap<>();
        Optional<ActivationCode> codeOpt = activationCodeService.claimCode(username);
        if (codeOpt.isPresent()) {
            ActivationCode code = codeOpt.get();
            operationLogService.record(username, "CLAIM", code.getCode(), null);
            result.put("success", true);
            result.put("code", code.getCode());
            result.put("fetchTime", code.getFetchTime());
        } else {
            result.put("success", false);
            result.put("error", "暂无可用激活码，请联系管理员补充");
        }
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
        result.put("total", activationCodeService.countTotal());
        return result;
    }

    /**
     * 获取激活码列表（JSON，分页）
     */
    @GetMapping("/api/list")
    @ResponseBody
    public Map<String, Object> apiList(@RequestParam(required = false) String status,
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
        return result;
    }

    /**
     * 我的获取记录（当前用户获取过的激活码）
     */
    @GetMapping("/api/my-codes")
    @ResponseBody
    public Map<String, Object> apiMyCodes(@AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        List<ActivationCode> codes = activationCodeService.listMyCodes(username);
        Map<String, Object> result = new HashMap<>();
        result.put("codes", codes);
        result.put("total", codes.size());
        return result;
    }

    /**
     * 标记激活码为已使用（JSON）
     */
    @PostMapping("/api/use/{id}")
    @ResponseBody
    public Map<String, Object> apiMarkAsUsed(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        activationCodeService.markAsUsed(id);
        activationCodeService.findById(id).ifPresent(c ->
                operationLogService.record(username, "MARK_USED", c.getCode(), null));
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "激活码已标记为已使用");
        return result;
    }

    /**
     * 标记激活码为未使用（JSON，仅超级管理员）
     */
    @PostMapping("/api/unuse/{id}")
    @ResponseBody
    public Map<String, Object> apiMarkAsUnused(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        activationCodeService.markAsUnused(id);
        activationCodeService.findById(id).ifPresent(c ->
                operationLogService.record(username, "MARK_UNUSED", c.getCode(), null));
        return Map.of("success", true, "message", "激活码已标记为未使用");
    }

    /**
     * 更新激活码备注
     */
    @PostMapping("/api/remark/{id}")
    @ResponseBody
    public Map<String, Object> apiUpdateRemark(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String remark = body.getOrDefault("remark", "");
        if (remark.length() > 200) remark = remark.substring(0, 200);
        activationCodeService.updateRemark(id, remark.isBlank() ? null : remark);
        return Map.of("success", true);
    }

    /**
     * Excel 批量导入激活码（支持第二列作为备注）
     */
    @PostMapping("/api/import")
    @ResponseBody
    public Map<String, Object> apiImport(@RequestParam("file") MultipartFile file,
                                         @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        Map<String, Object> result = new HashMap<>();
        try {
            List<String[]> rows = new ArrayList<>();
            try (var is = file.getInputStream();
                 var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(is)) {
                var sheet = workbook.getSheetAt(0);
                for (var row : sheet) {
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
            result.put("success", true);
            result.put("count", count);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "导入失败: " + e.getMessage());
        }
        result.put("total", activationCodeService.countTotal());
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
        return result;
    }

    /**
     * Excel 导出（当前筛选条件）
     */
    @GetMapping("/api/export")
    @ResponseBody
    public void apiExport(@RequestParam(required = false) String status,
                          @RequestParam(required = false) String keyword,
                          HttpServletResponse response) {
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
            // 表头
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("#");
            header.createCell(1).setCellValue("激活码");
            header.createCell(2).setCellValue("状态");
            header.createCell(3).setCellValue("备注");
            header.createCell(4).setCellValue("获取人");
            header.createCell(5).setCellValue("获取时间");
            header.createCell(6).setCellValue("创建时间");
            // 数据行
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

    /**
     * 批量标记激活码为已使用
     */
    @PostMapping("/api/batch-use")
    @ResponseBody
    public Map<String, Object> apiBatchUse(@RequestBody List<Long> ids, @AuthenticationPrincipal UserDetails user) {
        if (ids == null || ids.isEmpty()) {
            return Map.of("success", false, "message", "请选择要标记的激活码");
        }
        String username = user != null ? user.getUsername() : null;
        int count = activationCodeService.batchMarkAsUsed(ids);
        operationLogService.record(username, "BATCH_MARK_USED", null, "批量标记 " + count + " 个");
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", count);
        result.put("total", activationCodeService.countTotal());
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
        return result;
    }

    /**
     * 批量标记激活码为未使用
     */
    @PostMapping("/api/batch-unuse")
    @ResponseBody
    public Map<String, Object> apiBatchUnuse(@RequestBody List<Long> ids, @AuthenticationPrincipal UserDetails user) {
        if (ids == null || ids.isEmpty()) {
            return Map.of("success", false, "message", "请选择要标记的激活码");
        }
        String username = user != null ? user.getUsername() : null;
        int count = activationCodeService.batchMarkAsUnused(ids);
        operationLogService.record(username, "BATCH_MARK_UNUSED", null, "批量恢复 " + count + " 个");
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", count);
        result.put("total", activationCodeService.countTotal());
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
        return result;
    }

    /**
     * 批量申领 N 个激活码
     */
    @PostMapping("/api/batch-claim")
    @ResponseBody
    public Map<String, Object> apiBatchClaim(@RequestBody Map<String, Integer> body,
                                            @AuthenticationPrincipal UserDetails user) {
        int count = body.getOrDefault("count", 1);
        String username = user != null ? user.getUsername() : null;
        if (count < 1 || count > 100) {
            return Map.of("success", false, "message", "数量范围 1-100");
        }
        List<ActivationCode> claimed = activationCodeService.batchClaim(count, username);
        List<String> codes = claimed.stream().map(ActivationCode::getCode).toList();
        operationLogService.record(username, "BATCH_CLAIM", null, "批量获取 " + codes.size() + " 个");
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", codes.size());
        result.put("codes", codes);
        result.put("total", activationCodeService.countTotal());
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
        return result;
    }

    /**
     * 删除激活码（仅超级管理员）
     */
    @DeleteMapping("/api/code/{id}")
    @ResponseBody
    public Map<String, Object> apiDeleteCode(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        String username = user != null ? user.getUsername() : null;
        activationCodeService.findById(id).ifPresent(c ->
                operationLogService.record(username, "DELETE", c.getCode(), null));
        activationCodeService.deleteCode(id);
        return Map.of("success", true, "message", "激活码已删除");
    }

    /**
     * 批量删除激活码
     */
    @PostMapping("/api/batch-delete")
    @ResponseBody
    public Map<String, Object> apiBatchDelete(@RequestBody List<Long> ids, @AuthenticationPrincipal UserDetails user) {
        if (ids == null || ids.isEmpty()) {
            return Map.of("success", false, "message", "请选择要删除的激活码");
        }
        String username = user != null ? user.getUsername() : null;
        int count = activationCodeService.batchDeleteCodes(ids);
        operationLogService.record(username, "BATCH_DELETE", null, "批量删除 " + count + " 个");
        return Map.of("success", true, "count", count);
    }

    // ========== 操作日志 API ==========

    /**
     * 查询操作日志（分页，仅管理员）
     */
    @GetMapping("/api/logs")
    @ResponseBody
    public Map<String, Object> apiLogs(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        Page<OperationLog> logs = operationLogService.pageLogs(page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("logs", logs.getContent());
        result.put("page", logs.getNumber() + 1);
        result.put("totalPages", logs.getTotalPages());
        result.put("totalElements", logs.getTotalElements());
        return result;
    }

    // ========== 公告 API ==========

    /**
     * 获取公告列表（所有用户可用）
     */
    @GetMapping("/api/announcements")
    @ResponseBody
    public List<Announcement> apiAnnouncements() {
        return announcementService.listAll();
    }

    /**
     * 发布公告（仅管理员）
     */
    @PostMapping("/api/announcements")
    @ResponseBody
    public Map<String, Object> apiPublishAnnouncement(@RequestBody Map<String, Object> body,
                                                       @AuthenticationPrincipal UserDetails user) {
        String content = (String) body.getOrDefault("content", "");
        if (content.isBlank()) {
            return Map.of("success", false, "message", "公告内容不能为空");
        }
        if (content.length() > 500) content = content.substring(0, 500);
        boolean pinned = Boolean.TRUE.equals(body.get("pinned"));
        String publisher = user != null ? user.getUsername() : null;
        Announcement a = announcementService.publish(content, publisher, pinned);
        operationLogService.record(publisher, "PUBLISH_ANNOUNCEMENT", String.valueOf(a.getId()), content);
        return Map.of("success", true, "id", a.getId());
    }

    /**
     * 删除公告（仅管理员）
     */
    @DeleteMapping("/api/announcements/{id}")
    @ResponseBody
    public Map<String, Object> apiDeleteAnnouncement(@PathVariable Long id) {
        announcementService.delete(id);
        return Map.of("success", true);
    }
}
