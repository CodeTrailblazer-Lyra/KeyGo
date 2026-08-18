package xyz.lukix.keygo.controller;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.entity.Unit;
import xyz.lukix.keygo.service.OperationLogService;
import xyz.lukix.keygo.service.UnitService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 使用单位管理接口（仅管理员可用）
 */
@RestController
@RequestMapping("/api/v1/admin/units")
@RequiredArgsConstructor
public class AdminUnitsController {

    /** 单位名称最大长度（与表结构一致） */
    private static final int MAX_NAME_LENGTH = 100;

    private final UnitService unitService;
    private final OperationLogService operationLogService;

    /** 新建使用单位 */
    @PostMapping
    public ApiResponse<Unit> create(@RequestBody Map<String, String> body,
                                    @AuthenticationPrincipal UserDetails user) {
        String name = body == null ? null : body.get("name");
        if (name == null || name.isBlank()) {
            return ApiResponse.error(400, "单位名称不能为空");
        }
        name = name.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            return ApiResponse.error(400, "单位名称过长（最多 " + MAX_NAME_LENGTH + " 字符）");
        }
        try {
            Unit unit = unitService.create(name).orElse(null);
            if (unit == null) {
                return ApiResponse.error(409, "使用单位已存在: " + name);
            }
            String username = user != null ? user.getUsername() : null;
            operationLogService.record(username, "UNIT_CREATE", String.valueOf(unit.getId()), name);
            return ApiResponse.ok("创建成功", unit);
        } catch (DataIntegrityViolationException e) {
            // 并发下唯一约束兜底
            return ApiResponse.error(409, "使用单位已存在: " + name);
        }
    }

    /**
     * 批量导入使用单位（multipart 字段 file）
     * .txt/.csv：每行一个单位，忽略空行；.xlsx：取第一列
     */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importUnits(@RequestParam("file") MultipartFile file,
                                                        @AuthenticationPrincipal UserDetails user) {
        String filename = file.getOriginalFilename();
        if (filename == null
                || !(filename.toLowerCase().endsWith(".txt")
                || filename.toLowerCase().endsWith(".csv")
                || filename.toLowerCase().endsWith(".xlsx"))) {
            return ApiResponse.error(400, "不支持的文件类型，仅支持 .txt/.csv/.xlsx");
        }

        try {
            List<String> names;
            if (filename.toLowerCase().endsWith(".xlsx")) {
                names = parseExcel(file);
            } else {
                names = parseText(file);
            }
            UnitService.ImportResult result = unitService.importUnits(names);
            String username = user != null ? user.getUsername() : null;
            operationLogService.record(username, "UNIT_IMPORT", null,
                    "导入使用单位：新增 " + result.added() + "，重复 " + result.duplicates().size());
            return ApiResponse.ok(Map.of(
                    "added", result.added(),
                    "skipped", result.skipped(),
                    "duplicates", result.duplicates()));
        } catch (Exception e) {
            return ApiResponse.error(500, "导入失败: " + e.getMessage());
        }
    }

    /** 删除使用单位 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal UserDetails user) {
        if (!unitService.delete(id)) {
            return ApiResponse.error(404, "使用单位不存在");
        }
        String username = user != null ? user.getUsername() : null;
        operationLogService.record(username, "UNIT_DELETE", String.valueOf(id), null);
        return ApiResponse.ok("使用单位已删除", null);
    }

    /**
     * 解析 .xlsx：取第一列，忽略空单元格
     */
    private List<String> parseExcel(MultipartFile file) throws Exception {
        List<String> names = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (var is = file.getInputStream();
             var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(is)) {
            var sheet = workbook.getSheetAt(0);
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                if (row == null) continue;
                var cell = row.getCell(0);
                if (cell == null) continue;
                addName(names, formatter.formatCellValue(cell));
            }
        }
        return names;
    }

    /**
     * 解析 .txt/.csv：每行一个单位，忽略空行
     */
    private List<String> parseText(MultipartFile file) throws Exception {
        List<String> names = new ArrayList<>();
        try (var is = file.getInputStream();
             var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                addName(names, line);
            }
        }
        return names;
    }

    /**
     * 去除首尾空白与 UTF-8 BOM 后加入列表；超长截断，空行忽略
     */
    private void addName(List<String> names, String raw) {
        if (raw == null) return;
        String name = raw.replace("\uFEFF", "").trim();
        if (name.isEmpty()) return;
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        names.add(name);
    }
}
