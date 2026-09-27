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

    /** 公告内容最大长度 */
    private static final int MAX_CONTENT_LENGTH = 500;

    private final AnnouncementService announcementService;
    private final OperationLogService operationLogService;

    /** 获取公告列表（所有认证用户可用） */
    @GetMapping
    public ApiResponse<List<Announcement>> list() {
        return ApiResponse.ok(announcementService.listAll());
    }

    /** 发布公告（仅管理员），body 可选字段：pinned（默认 false）、visible（默认 true，是否在获取页显示） */
    @PostMapping
    public ApiResponse<Map<String, Object>> publish(@RequestBody Map<String, Object> body,
                                                    @AuthenticationPrincipal UserDetails user) {
        String content = (String) body.getOrDefault("content", "");
        if (content.isBlank()) {
            return ApiResponse.error(400, "公告内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) content = content.substring(0, MAX_CONTENT_LENGTH);
        boolean pinned = Boolean.TRUE.equals(body.get("pinned"));
        // 缺省时默认显示（向后兼容：旧前端不传该字段即发布即上线）
        Object visibleVal = body.get("visible");
        boolean visible = visibleVal == null || Boolean.TRUE.equals(visibleVal);
        String publisher = user != null ? user.getUsername() : null;
        Announcement a = announcementService.publish(content, publisher, pinned, visible);
        operationLogService.record(publisher, "PUBLISH_ANNOUNCEMENT", String.valueOf(a.getId()), content);
        return ApiResponse.ok(Map.of("id", a.getId()));
    }

    /** 切换公告在获取页的显示状态（仅管理员），body: {"visible": true|false} */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateVisible(@PathVariable Long id,
                                           @RequestBody Map<String, Object> body,
                                           @AuthenticationPrincipal UserDetails user) {
        Object visibleVal = body == null ? null : body.get("visible");
        if (!(visibleVal instanceof Boolean visible)) {
            return ApiResponse.error(400, "visible 必须为布尔值");
        }
        if (!announcementService.updateVisible(id, visible)) {
            return ApiResponse.error(404, "公告不存在");
        }
        String username = user != null ? user.getUsername() : null;
        operationLogService.record(username, "ANNOUNCEMENT_VISIBLE", String.valueOf(id),
                visible ? "显示" : "隐藏");
        return ApiResponse.ok(visible ? "公告已设为显示" : "公告已设为隐藏", null);
    }

    /** 删除公告（仅管理员） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal UserDetails user) {
        if (!announcementService.delete(id)) {
            return ApiResponse.error(404, "公告不存在");
        }
        String username = user != null ? user.getUsername() : null;
        operationLogService.record(username, "ANNOUNCEMENT_DELETE", String.valueOf(id), null);
        return ApiResponse.ok("公告已删除", null);
    }
}
