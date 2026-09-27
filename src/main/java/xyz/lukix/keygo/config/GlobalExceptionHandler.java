package xyz.lukix.keygo.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import xyz.lukix.keygo.common.ApiResponse;

/**
 * 全局异常兜底处理：
 * <ul>
 *   <li>不替换各控制器内已有的 try/catch（其错误码映射与前端约定绑定），仅处理逃逸到框架层的异常；</li>
 *   <li>向前端返回统一的 ApiResponse 结构，且不透出异常原始消息（避免泄露 SQL/文件系统细节）；</li>
 *   <li>Spring MVC 自身携带状态码语义的异常（404/400 等）保持原状态码，不降级为 500。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @PreAuthorize / authorizeHttpRequests 抛出的权限拒绝（HTTP 状态码保持 403） */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限拒绝: {}", e.getMessage());
        return ApiResponse.error(403, "无权访问");
    }

    /** 其余所有异常：可识别状态码的原样保留（如 404 静态资源缺失、400 请求体解析失败），其余按 500 处理 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(HttpServletRequest request, Exception e) {
        HttpStatusCode status = resolveStatus(e);
        if (status.is4xxClientError()) {
            // 客户端错误属于预期噪音，仅记简要日志
            log.warn("请求异常 {} {}: {} (status={})",
                    request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName(), status.value());
            return ResponseEntity.status(status).body(ApiResponse.error(status.value(), "请求无效"));
        }
        log.error("未处理异常 {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误"));
    }

    /**
     * 提取 Spring MVC 异常自带的 HTTP 状态码：ResponseStatusException、ErrorResponse
     * （覆盖 NoResourceFoundException 等）、@ResponseStatus 注解异常（如 HttpMessageNotReadableException）
     */
    private HttpStatusCode resolveStatus(Exception e) {
        if (e instanceof ResponseStatusException rse) {
            return rse.getStatusCode();
        }
        if (e instanceof ErrorResponse er) {
            return er.getStatusCode();
        }
        ResponseStatus rs = AnnotationUtils.findAnnotation(e.getClass(), ResponseStatus.class);
        if (rs != null) {
            return HttpStatus.valueOf(rs.code().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
