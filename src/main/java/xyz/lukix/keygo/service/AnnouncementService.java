package xyz.lukix.keygo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import xyz.lukix.keygo.entity.Announcement;
import xyz.lukix.keygo.repository.AnnouncementRepository;

import java.time.Duration;
import java.util.List;

/**
 * 系统公告服务
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    /** 公告列表缓存：登录页每次拉取都命中同一份，变更时主动失效 */
    private static final String LIST_CACHE_KEY = "keygo:cache:announcements";
    private static final Duration LIST_CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<Announcement>> LIST_CACHE_TYPE = new TypeReference<>() {
    };

    private final AnnouncementRepository announcementRepository;
    private final RedisJsonCache redisJsonCache;

    /** 查询所有公告（置顶优先，时间倒序） */
    @Transactional(readOnly = true)
    public List<Announcement> listAll() {
        return redisJsonCache.getOrLoad(LIST_CACHE_KEY, LIST_CACHE_TTL, LIST_CACHE_TYPE,
                () -> announcementRepository.findAllByOrderByPinnedDescCreateTimeDesc());
    }

    /** 发布公告（visible=是否在获取页显示） */
    @Transactional
    public Announcement publish(String content, String publisher, boolean pinned, boolean visible) {
        Announcement a = new Announcement();
        a.setContent(content);
        a.setPublisher(publisher);
        a.setPinned(pinned);
        a.setVisible(visible);
        Announcement saved = announcementRepository.save(a);
        redisJsonCache.evict(LIST_CACHE_KEY);
        return saved;
    }

    /** 更新公告可见性；公告不存在时返回 false */
    @Transactional
    public boolean updateVisible(Long id, boolean visible) {
        boolean updated = announcementRepository.findById(id)
                .map(a -> {
                    a.setVisible(visible);
                    announcementRepository.save(a);
                    return true;
                })
                .orElse(false);
        if (updated) {
            redisJsonCache.evict(LIST_CACHE_KEY);
        }
        return updated;
    }

    /** 删除公告；不存在时返回 false */
    @Transactional
    public boolean delete(Long id) {
        if (!announcementRepository.existsById(id)) {
            return false;
        }
        announcementRepository.deleteById(id);
        redisJsonCache.evict(LIST_CACHE_KEY);
        return true;
    }
}
