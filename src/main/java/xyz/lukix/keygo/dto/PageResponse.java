package xyz.lukix.keygo.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 通用分页响应
 *
 * @param items         数据列表
 * @param page          当前页码（从 1 开始）
 * @param size          每页大小
 * @param totalPages    总页数
 * @param totalElements 总记录数
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        int totalPages,
        long totalElements
) {
    /**
     * 从 Spring Data Page 构建分页响应
     *
     * @param page Spring Data 分页结果
     * @param <T>  数据类型
     * @return 分页响应 DTO
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements()
        );
    }
}
