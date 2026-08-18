package xyz.lukix.keygo.service;

import xyz.lukix.keygo.entity.ActivationCode;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Optional;

public interface ActivationCodeService {

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
     * 获取可用激活码总数
     */
    long countAvailable();

    /**
     * 获取已使用激活码数量
     */
    long countUsed();

    /**
     * 获取激活码总数量
     */
    long countTotal();

    /**
     * 将指定激活码标记为已使用
     */
    void markAsUsed(Long id);

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
     * 批量导入激活码（每项为 {code, remark}，remark 可为 null）
     */
    int batchImportCodes(List<String[]> rows);

    /**
     * 批量标记为已使用
     */
    int batchMarkAsUsed(List<Long> ids);

    /**
     * 将激活码标记为未使用（恢复）
     */
    void markAsUnused(Long id);

    /**
     * 批量标记为未使用
     */
    int batchMarkAsUnused(List<Long> ids);

    /**
     * 批量申领 N 个激活码（返回实际申领到的列表），并记录获取人
     */
    List<ActivationCode> batchClaim(int count, String username);

    /**
     * 删除激活码
     */
    void deleteCode(Long id);

    /**
     * 批量删除激活码
     */
    int batchDeleteCodes(List<Long> ids);
}
