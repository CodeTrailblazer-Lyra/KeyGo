package xyz.lukix.keygo.dto;

import java.util.List;

/**
 * 批量操作请求体：统一使用 { "ids": [...] } 包裹，避免裸数组
 */
public record BatchIdsRequest(List<Long> ids) {
}
