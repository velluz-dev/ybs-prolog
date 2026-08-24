package com.ybs.demo.eta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrafficFactorRepository extends JpaRepository<TrafficFactor, Long> {
    Optional<TrafficFactor> findBySegmentIdAndBucket(String segmentId, String bucket);
}
