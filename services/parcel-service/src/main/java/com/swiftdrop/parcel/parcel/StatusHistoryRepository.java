package com.swiftdrop.parcel.parcel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatusHistoryRepository extends JpaRepository<StatusHistory, String> {

    List<StatusHistory> findByParcelIdOrderByCreatedAtAsc(String parcelId);
}
