package com.zuhlke.tradenet.chat.repository;

import com.zuhlke.tradenet.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findBySenderIdAndClientMsgId(Long senderId, String clientMsgId);

    @Query("select m from Message m where "
            + "(m.senderId = :userA and m.recipientId = :userB) or (m.senderId = :userB and m.recipientId = :userA) "
            + "order by m.id desc")
    List<Message> findConversationPageDesc(@Param("userA") Long userA, @Param("userB") Long userB, Pageable pageable);
}
