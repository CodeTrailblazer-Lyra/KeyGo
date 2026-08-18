package xyz.lukix.keygo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.lukix.keygo.entity.Announcement;
import xyz.lukix.keygo.repository.AnnouncementRepository;

import java.util.List;

/**
 * 系统公告服务
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    /** 查询所有公告（置顶优先，时间倒序） */
    @Transactional(readOnly = true)
    public List<Announcement> listAll() {
        return announcementRepository.findAllByOrderByPinnedDescCreateTimeDesc();
    }

    /** 发布公告（visible=是否在获取页显示） */
    @Transactional
    public Announcement publish(String content, String publisher, boolean pinned, boolean visible) {
        Announcement a = new Announcement();
        a.setContent(content);
        a.setPublisher(publisher);
        a.setPinned(pinned);
        a.setVisible(visible);
        return announcementRepository.save(a);
    }

    /** 更新公告可见性；公告不存在时返回 false */
    @Transactional
    public boolean updateVisible(Long id, boolean visible) {
        return announcementRepository.findById(id)
                .map(a -> {
                    a.setVisible(visible);
                    announcementRepository.save(a);
                    return true;
                })
                .orElse(false);
    }

    /** 删除公告 */
    @Transactional
    public void delete(Long id) {
        announcementRepository.deleteById(id);
    }
}
