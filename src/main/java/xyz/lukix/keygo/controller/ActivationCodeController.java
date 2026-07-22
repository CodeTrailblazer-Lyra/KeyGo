package xyz.lukix.keygo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import xyz.lukix.keygo.entity.ActivationCode;
import xyz.lukix.keygo.service.ActivationCodeService;

import java.util.*;

@Controller
@RequestMapping("/admin/code")
@RequiredArgsConstructor
public class ActivationCodeController {

    private final ActivationCodeService activationCodeService;

    /**
     * 激活码申领页面
     */
    @GetMapping("/fetch")
    public String fetchPage(Model model) {
        model.addAttribute("total", activationCodeService.countTotal());
        model.addAttribute("available", activationCodeService.countAvailable());
        model.addAttribute("used", activationCodeService.countUsed());
        return "admin/code/fetch";
    }

    /**
     * 执行申领操作
     */
    @PostMapping("/claim")
    public String claimCode(RedirectAttributes redirectAttributes) {
        Optional<ActivationCode> codeOpt = activationCodeService.claimCode();
        if (codeOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("claimedCode", codeOpt.get().getCode());
            redirectAttributes.addFlashAttribute("success", true);
        } else {
            redirectAttributes.addFlashAttribute("error", "暂无可用激活码，请联系管理员补充");
        }
        return "redirect:/admin/code/fetch";
    }

    /**
     * AJAX 申领接口（返回 JSON，弹窗内展示结果）
     */
    @PostMapping("/claim-json")
    @ResponseBody
    public Map<String, Object> claimCodeJson() {
        Map<String, Object> result = new HashMap<>();
        Optional<ActivationCode> codeOpt = activationCodeService.claimCode();
        if (codeOpt.isPresent()) {
            result.put("success", true);
            result.put("code", codeOpt.get().getCode());
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
     * 激活码查询页面（含状态管理）
     */
    @GetMapping("/list")
    public String listPage(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String keyword,
                           Model model) {
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

        model.addAttribute("codes", codes);
        model.addAttribute("currentStatus", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("total", activationCodeService.countTotal());
        model.addAttribute("available", activationCodeService.countAvailable());
        model.addAttribute("used", activationCodeService.countUsed());
        return "admin/code/list";
    }

    /**
     * 手动标记激活码为已使用
     */
    @PostMapping("/use/{id}")
    public String markAsUsed(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        activationCodeService.markAsUsed(id);
        redirectAttributes.addFlashAttribute("success", "激活码已标记为已使用");
        return "redirect:/admin/code/list";
    }

    // ========== Vue SPA JSON API ==========

    /**
     * 获取激活码列表（JSON）
     */
    @GetMapping("/api/list")
    @ResponseBody
    public Map<String, Object> apiList(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String keyword) {
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
        Map<String, Object> result = new HashMap<>();
        result.put("codes", codes);
        result.put("total", activationCodeService.countTotal());
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
        return result;
    }

    /**
     * 获取统计数据（JSON）
     */
    @GetMapping("/api/stats")
    @ResponseBody
    public Map<String, Long> apiStats() {
        Map<String, Long> result = new HashMap<>();
        result.put("total", activationCodeService.countTotal());
        result.put("available", activationCodeService.countAvailable());
        result.put("used", activationCodeService.countUsed());
        return result;
    }

    /**
     * 标记激活码为已使用（JSON）
     */
    @PostMapping("/api/use/{id}")
    @ResponseBody
    public Map<String, Object> apiMarkAsUsed(@PathVariable Long id) {
        activationCodeService.markAsUsed(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "激活码已标记为已使用");
        return result;
    }

    /**
     * Excel 批量导入激活码
     */
    @PostMapping("/api/import")
    @ResponseBody
    public Map<String, Object> apiImport(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> codes = new ArrayList<>();
            try (var is = file.getInputStream();
                 var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(is)) {
                var sheet = workbook.getSheetAt(0);
                for (var row : sheet) {
                    var cell = row.getCell(0);
                    if (cell != null) {
                        String val = cell.getStringCellValue();
                        if (val != null && !val.isBlank()) {
                            codes.add(val.trim());
                        }
                    }
                }
            }
            int count = activationCodeService.batchImportCodes(codes);
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
     * 批量标记激活码为已使用
     */
    @PostMapping("/api/batch-use")
    @ResponseBody
    public Map<String, Object> apiBatchUse(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of("success", false, "message", "请选择要标记的激活码");
        }
        int count = activationCodeService.batchMarkAsUsed(ids);
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
    public Map<String, Object> apiBatchClaim(@RequestBody Map<String, Integer> body) {
        int count = body.getOrDefault("count", 1);
        if (count < 1 || count > 100) {
            return Map.of("success", false, "message", "数量范围 1-100");
        }
        List<ActivationCode> claimed = activationCodeService.batchClaim(count);
        List<String> codes = claimed.stream().map(ActivationCode::getCode).toList();
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
    public Map<String, Object> apiDeleteCode(@PathVariable Long id) {
        activationCodeService.deleteCode(id);
        return Map.of("success", true, "message", "激活码已删除");
    }

    /**
     * 批量删除激活码
     */
    @PostMapping("/api/batch-delete")
    @ResponseBody
    public Map<String, Object> apiBatchDelete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of("success", false, "message", "请选择要删除的激活码");
        }
        int count = activationCodeService.batchDeleteCodes(ids);
        return Map.of("success", true, "count", count);
    }

    /**
     * 数据统计页面
     */
    @GetMapping("/stats")
    public String statsPage(Model model) {
        long total = activationCodeService.countTotal();
        long available = activationCodeService.countAvailable();
        long used = activationCodeService.countUsed();

        model.addAttribute("total", total);
        model.addAttribute("available", available);
        model.addAttribute("used", used);
        return "admin/code/stats";
    }
}
