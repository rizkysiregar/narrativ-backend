package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.document.UserDocument;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.repository.UserSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserSearchService {

    private final UserSearchRepository userSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    // index a user: call when create new user or profile update
    public void indexUser(UserEntity userEntity) {
        UserDocument doc = mapToDocument(userEntity);
        userSearchRepository.save(doc);
    }

    public void deleteUser(String userId) {
        userSearchRepository.deleteById(userId);
    }

    public List<UserDocument> searchUsers(String query) {
        Query searchQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .multiMatch(m -> m
                                .query(query) // what user want to search
                                .fields("username", "displayName") // search it on this field or index
                                .fuzziness("AUTO") // allows typos
                        )
                )
                .withPageable(PageRequest.of(0, 20)) // first page, max 20 results
                .build();

        return elasticsearchOperations
                .search(searchQuery, UserDocument.class)
                .stream()
                .map(SearchHit::getContent) // strip ES metadata, keep UserDocument only
                .collect(Collectors.toList());
    }


    private UserDocument mapToDocument(UserEntity entity) {
        return UserDocument.builder()
                .id(entity.getId().toString())
                .username(entity.getUsername())
                .displayName(entity.getFirstName() + " " + entity.getLastName())
                .email(entity.getEmail())
                .build();
    }

}
