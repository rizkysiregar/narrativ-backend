package com.rizkysiregar.feedzmore.repository.elasticsearch;

import com.rizkysiregar.feedzmore.model.document.MessageDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface MessageSearchRepository extends ElasticsearchRepository<MessageDocument, String> {

    Page<MessageDocument> findByConversationIdAndContentContaining(
            String conversationId,
            String content,
            Pageable pageable
    );

}
