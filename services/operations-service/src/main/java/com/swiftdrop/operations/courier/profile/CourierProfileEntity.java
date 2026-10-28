package com.swiftdrop.operations.courier.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "courier_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierProfileEntity {
    @Id
    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 64)
    private String phone;

    @Column(length = 128)
    private String zone;

    @Column(nullable = false)
    private boolean availability;

    @Column(name = "vehicle_type", length = 64)
    private String vehicleType;
}
