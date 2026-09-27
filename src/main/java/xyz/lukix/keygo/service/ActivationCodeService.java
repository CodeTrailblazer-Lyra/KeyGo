package xyz.lukix.keygo.service;

import xyz.lukix.keygo.entity.ActivationCode;

import org.springframework.data.domain.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ActivationCodeService {

    /**
     * 设备核销激活码：SHA-256 匹配（hash 优先、明文降级），行锁防重复核销，
     * 成功后标记已使用并绑定设备；clientIp 仅用于异步审计
     */
    RedeemResult redeem(String rawCode, String deviceId, String clientIp);

    /** 核销结果状态（INVALID 同时覆盖输入非法与码不存在，供对外统一话术） */
    enum RedeemStatus { SUCCESS, INVALID, ALREADY_USED, EXPIRED }

    /** 核销结果：codeId 仅内部排查用 */
    record RedeemResult(RedeemStatus status, Long codeId) {
        public boolean success() {
            return status == RedeemStatus.SUCCESS;
        }
    }

    /**
     * 申领一个可用的激活码（从未使用的池子中分配一个），并记录获取人；
     * remark 非空时写入该激活码备注（为空时保持原值）
     */
    Optional<ActivationCode> claimCode(String username, String remark);

    /**
     * 分页查询所有激活码
     */
    List<ActivationCode> listAll();

    /**
     * 根据状态查询激活码
     */
    List<ActivationCode> listByUsed(boolean used);

    /**
     * 分页查询激活码（按状态筛选/关键字搜索，未使用在前，已使用在后，各自按ID升序）
     */
    Page<ActivationCode> pageQuery(String status, String keyword, int page, int size);

    /**
     * 一次获取激活码统计（总数/可用/已用），单条聚合查询实现
     */
    Stats getStats();

    /** 激活码统计结果 */
    record Stats(long total, long available, long used) {
    }

    /**
     * 将指定激活码标记为已使用；不存在时返回 false
     */
    boolean markAsUsed(Long id);

    /**
     * 根据ID查询激活码
     */
    Optional<ActivationCode> findById(Long id);

    /**
     * 按激活码字符串搜索
     */
    List<ActivationCode> searchByCode(String keyword);

    /**
     * 分页查询指定用户获取过的激活码（我的获取记录，按获取时间倒序）
     */
    Page<ActivationCode> pageMyCodes(String username, int page, int size);

    /**
     * 更新激活码备注
     */
    void updateRemark(Long id, String remark);

    /**
     * 批量导入激活码（每项为 {code, remark}，remark 可为 null）；
     * 自动跳过空行、文件内重复与库内已存在的 code
     */
    ImportResult batchImportCodes(List<String[]> rows);

    /** 批量导入结果：imported=实际导入数，skipped=未导入的有效行数（重复/已存在） */
    record ImportResult(int imported, int skipped) {
    }

    /**
     * 批量标记为已使用
     */
    int batchMarkAsUsed(List<Long> ids);

    /**
     * 将激活码标记为未使用（恢复，同时清除获取人与设备绑定）；不存在时返回 false
     */
    boolean markAsUnused(Long id);

    /**
     * 批量标记为未使用
     */
    int batchMarkAsUnused(List<Long> ids);

    /**
     * 批量申领 N 个激活码（返回实际申领到的列表），并记录获取人
     */
    List<ActivationCode> batchClaim(int count, String username);

    /**
     * 删除激活码；不存在时返回 false
     */
    boolean deleteCode(Long id);

    /**
     * 批量删除激活码
     */
    int batchDeleteCodes(List<Long> ids);
}
