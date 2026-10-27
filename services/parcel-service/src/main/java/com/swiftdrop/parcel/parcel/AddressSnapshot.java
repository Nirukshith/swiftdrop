package com.swiftdrop.parcel.parcel;

import com.swiftdrop.parcel.address.AddressDto;
import com.swiftdrop.parcel.address.District;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddressSnapshot {

    @Column(nullable = false, length = 255)
    private String line1;

    @Column(length = 255)
    private String line2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 50)
    private String district;

    @Column(nullable = false, length = 20)
    private String postalCode;

    public static AddressSnapshot from(AddressDto dto) {
        return new AddressSnapshot(
                dto.line1().trim(),
                dto.line2() != null ? dto.line2().trim() : null,
                dto.city().trim(),
                District.normalize(dto.district()),
                dto.postalCode().trim()
        );
    }

    public AddressDto toDto() {
        return new AddressDto(line1, line2, city, district, postalCode);
    }
}
