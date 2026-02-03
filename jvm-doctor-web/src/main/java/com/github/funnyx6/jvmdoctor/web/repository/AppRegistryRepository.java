package com.github.funnyx6.jvmdoctor.web.repository;

import com.github.funnyx6.jvmdoctor.web.entity.AppRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppRegistryRepository extends JpaRepository<AppRegistry, Long> {
    
    Optional<AppRegistry> findByAppNameAndHostAndPort(String appName, String host, Integer port);
    
    List<AppRegistry> findByStatus(String status);
    
    List<AppRegistry> findAllByOrderByRegisteredAtDesc();
    
    boolean existsByAppNameAndHostAndPort(String appName, String host, Integer port);
}
