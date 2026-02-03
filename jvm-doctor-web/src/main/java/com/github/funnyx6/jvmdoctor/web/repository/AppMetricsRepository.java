package com.github.funnyx6.jvmdoctor.web.repository;

import com.github.funnyx6.jvmdoctor.web.entity.AppMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AppMetricsRepository extends JpaRepository<AppMetrics, Long> {
    
    List<AppMetrics> findByAppIdOrderByTimestampDesc(Long appId);
    
    @Query("SELECT m FROM AppMetrics m WHERE m.appId = :appId AND m.timestamp >= :startTime ORDER BY m.timestamp ASC")
    List<AppMetrics> findByAppIdAndTimestampAfter(
            @Param("appId") Long appId, 
            @Param("startTime") Long startTime);
    
    @Query("SELECT m FROM AppMetrics m WHERE m.appId = :appId ORDER BY m.timestamp DESC LIMIT 1")
    AppMetrics findLatestByAppId(@Param("appId") Long appId);
    
    @Query("SELECT m FROM AppMetrics m WHERE m.timestamp >= :startTime ORDER BY m.timestamp ASC")
    List<AppMetrics> findAllByTimestampAfter(@Param("startTime") Long startTime);
    
    void deleteByTimestampBefore(Long timestamp);
}
