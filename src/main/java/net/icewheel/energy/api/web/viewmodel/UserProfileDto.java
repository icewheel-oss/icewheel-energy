package net.icewheel.energy.api.web.viewmodel;

import lombok.Data;

@Data
public class UserProfileDto {
    private Double latitude;
    private Double longitude;
    private String timezone;
    private String zipCode;
    private String locationName;
}
