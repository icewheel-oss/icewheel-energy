package net.icewheel.energy.application.user;

import java.util.List;

import net.icewheel.energy.application.user.dto.UserZipCodeDto;
import net.icewheel.energy.application.user.model.UserZipCode;

public interface UserZipCodeService {
    List<UserZipCode> getZipCodesForCurrentUser();
    UserZipCode addZipCodeToCurrentUser(UserZipCodeDto zipCodeDto);
    void deleteZipCode(Long zipCodeId);
}
