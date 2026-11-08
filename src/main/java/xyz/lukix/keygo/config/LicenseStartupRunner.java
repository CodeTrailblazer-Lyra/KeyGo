package xyz.lukix.keygo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import xyz.lukix.keygo.service.OfficialLicenseService;

/**
 * 应用启动后触发一次官方激活码联网验证。
 * <p>
 * 全量检查所有码（含未申领的），若发现官方已激活但本地未使用的码，
 * 自动同步本地状态为已使用，确保数据一致性。
 * 验证任务异步执行，不阻塞启动流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LicenseStartupRunner implements ApplicationRunner {

    private final OfficialLicenseService officialLicenseService;

    @Override
    public void run(ApplicationArguments args) {
        if (!officialLicenseService.isEnabled()) {
            log.info("官方激活码联网验证未启用（keygo.license.enabled=false），跳过启动同步");
            return;
        }
        log.info("启动官方激活码联网验证（全量检查）...");
        // 异步提交，不阻塞启动；tryBegin 失败说明已有任务在跑（理论上启动时不会发生）
        if (officialLicenseService.tryBegin()) {
            officialLicenseService.runCheckAsync(true);  // true=全量检查，发现不一致自动修复
        } else {
            log.warn("官方激活码验证任务已在运行，跳过本次启动同步");
        }
    }
}
