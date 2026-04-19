package com.rizkysiregar.feedzmore.model.document;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Data
@Builder
@Document(indexName = "users-index")
@Setting(shards = 1, replicas = 0) // replica refer to kubernetes nodes, single nodes mean 0 replica
public class UserDocument {

    private @Id String id;

    @Field(type = FieldType.Text , analyzer = "standard")
    private String username;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String displayName;

    @Field(type = FieldType.Keyword)
    private String email;

    @Field(type = FieldType.Keyword)
    private String profilePictureUrl;

//    @Field(type = FieldType.Date)
//    private LocalDateTime createdAt;
}
