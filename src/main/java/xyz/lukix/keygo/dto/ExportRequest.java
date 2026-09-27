package xyz.lukix.keygo.dto;

/**
 * Excel 导出筛选条件（status/keyword 均可省略）
 */
public record ExportRequest(String status, String keyword) {
}
