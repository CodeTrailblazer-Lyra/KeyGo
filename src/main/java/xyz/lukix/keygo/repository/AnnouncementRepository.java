package xyz.lukix.keygo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.lukix.keygo.entity.Announcement;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /** 置顶优先，其次按时间倒序 */
    List<Announcement> findAllByOrderByPinnedDescCreateTimeDesc();
}
