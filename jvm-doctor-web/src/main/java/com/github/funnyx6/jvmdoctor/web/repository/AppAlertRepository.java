package com.github.funnyx6.jvmdoctor.web.repository;

import com.github.funnyx6.jvmdoctor.web.entity.AppAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppAlertRepository extends JpaRepository<AppAlert, Long> {
    
    List<AppAlert> findByAppIdOrderByCreatedAtDesc(Long appId);
    
    List<AppAlert> findByAcknowledgedFalseOrderByCreatedAtDesc();
    
    List<AppAlert> findAllByOrderByCreatedAtDesc();
    
    long countByAcknowledgedFalse();
}
