package com.rizkysiregar.feedzmore.repository.elasticsearch;

import com.rizkysiregar.feedzmore.model.document.UserDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface UserSearchRepository extends ElasticsearchRepository<UserDocument, String> {
    List<UserDocument> findByUsername(String username);
    List<UserDocument> findByUsernameContaining(String username);
}
