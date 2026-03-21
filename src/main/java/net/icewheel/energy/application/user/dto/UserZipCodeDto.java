package net.icewheel.energy.application.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserZipCodeDto {
    @NotBlank
    @Pattern(regexp = "^[0-9]{5}(?:-[0-9]{4})?$")
    private String zipCode;

    @Size(max = 255)
    private String nickname;
}
