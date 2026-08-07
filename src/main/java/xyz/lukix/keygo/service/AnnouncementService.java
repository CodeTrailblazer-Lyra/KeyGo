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

    /** 发布公告 */
    @Transactional
    public Announcement publish(String content, String publisher, boolean pinned) {
        Announcement a = new Announcement();
        a.setContent(content);
        a.setPublisher(publisher);
        a.setPinned(pinned);
        return announcementRepository.save(a);
    }

    /** 删除公告 */
    @Transactional
    public void delete(Long id) {
        announcementRepository.deleteById(id);
    }
}
