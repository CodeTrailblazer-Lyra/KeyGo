package xyz.lukix.keygo.service;

import xyz.lukix.keygo.entity.ActivationCode;

import java.util.List;
import java.util.Optional;

public interface ActivationCodeService {

    /**
     * 申领一个可用的激活码（从未使用的池子中分配一个）
     */
    Optional<ActivationCode> claimCode();

    /**
     * 分页查询所有激活码
     */
    List<ActivationCode> listAll();

    /**
     * 根据状态查询激活码
     */
    List<ActivationCode> listByUsed(boolean used);

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
     * 批量导入激活码
     */
    int batchImportCodes(List<String> codes);

    /**
     * 批量标记为已使用
     */
    int batchMarkAsUsed(List<Long> ids);
}
