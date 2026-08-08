package xyz.lukix.keygo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.entity.Announcement;
import xyz.lukix.keygo.service.AnnouncementService;
import xyz.lukix.keygo.service.OperationLogService;

import java.util.List;
import java.util.Map;

/**
 * 系统公告接口
 */
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final OperationLogService operationLogService;

    /** 获取公告列表（所有认证用户可用） */
    @GetMapping
    public ApiResponse<List<Announcement>> list() {
        return ApiResponse.ok(announcementService.listAll());
    }

    /** 发布公告（仅管理员） */
    @PostMapping
    public ApiResponse<Map<String, Object>> publish(@RequestBody Map<String, Object> body,
                                                    @AuthenticationPrincipal UserDetails user) {
        String content = (String) body.getOrDefault("content", "");
        if (content.isBlank()) {
            return ApiResponse.error(400, "公告内容不能为空");
        }
        if (content.length() > 500) content = content.substring(0, 500);
        boolean pinned = Boolean.TRUE.equals(body.get("pinned"));
        String publisher = user != null ? user.getUsername() : null;
        Announcement a = announcementService.publish(content, publisher, pinned);
        operationLogService.record(publisher, "PUBLISH_ANNOUNCEMENT", String.valueOf(a.getId()), content);
        return ApiResponse.ok(Map.of("id", a.getId()));
    }

    /** 删除公告（仅管理员） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ApiResponse.ok("公告已删除", null);
    }
}
