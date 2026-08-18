package xyz.lukix.keygo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.entity.Unit;
import xyz.lukix.keygo.service.UnitService;

import java.util.List;

/**
 * 使用单位接口（所有认证用户可用）
 */
@RestController
@RequestMapping("/api/v1/units")
@RequiredArgsConstructor
public class UnitController {

    private final UnitService unitService;

    /** 使用单位列表 */
    @GetMapping
    public ApiResponse<List<Unit>> list() {
        return ApiResponse.ok(unitService.listAll());
    }
}
