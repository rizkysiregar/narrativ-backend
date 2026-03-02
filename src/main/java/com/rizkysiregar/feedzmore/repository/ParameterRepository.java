package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.ParameterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParameterRepository extends JpaRepository<ParameterEntity, Long> {

    Optional<ParameterEntity> findByParamKey(String paramKey);
}
