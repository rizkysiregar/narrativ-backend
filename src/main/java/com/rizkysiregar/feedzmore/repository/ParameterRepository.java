package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.ParameterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParameterRepository extends JpaRepository<ParameterEntity, Long>, JpaSpecificationExecutor<ParameterEntity> {

    boolean existsByParamKey(String paramKey);


    Optional<ParameterEntity> findByParamKey(String paramKey);
}
