package com.rizkysiregar.feedzmore.repository;



import com.rizkysiregar.feedzmore.model.entity.UsersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UsersEntity, UUID> {
    UsersEntity findByUsername(String username);
}
