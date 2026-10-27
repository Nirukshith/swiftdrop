package com.swiftdrop.parcel.parcel;

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
public class DeliveryProof {

    @Column(name = "proof_recipient_name", length = 150)
    private String recipientName;

    @Column(name = "proof_photo_path", length = 500)
    private String proofPhotoPath;
}
