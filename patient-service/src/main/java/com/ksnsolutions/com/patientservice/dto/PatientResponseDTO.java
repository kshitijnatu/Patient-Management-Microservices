package com.ksnsolutions.com.patientservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class PatientResponseDTO {
    private String id, name, email, address, dateOfBirth;
}
