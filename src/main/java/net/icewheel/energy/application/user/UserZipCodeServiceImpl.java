package net.icewheel.energy.application.user;

import java.util.List;

import lombok.RequiredArgsConstructor;
import net.icewheel.energy.application.user.dto.UserZipCodeDto;
import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.application.user.model.UserZipCode;
import net.icewheel.energy.application.user.repository.UserZipCodeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserZipCodeServiceImpl implements UserZipCodeService {

    private final UserZipCodeRepository userZipCodeRepository;
    private final AppUserService appUserService;

    @Override
    @Transactional(readOnly = true)
    public List<UserZipCode> getZipCodesForCurrentUser() {
        User currentUser = appUserService.getCurrentUser().orElseThrow(() -> new RuntimeException("User not found"));
        return userZipCodeRepository.findByUserId(currentUser.getId());
    }

    @Override
    public UserZipCode addZipCodeToCurrentUser(UserZipCodeDto zipCodeDto) {
        User currentUser = appUserService.getCurrentUser().orElseThrow(() -> new RuntimeException("User not found"));
        UserZipCode userZipCode = new UserZipCode();
        userZipCode.setUser(currentUser);
        userZipCode.setZipCode(zipCodeDto.getZipCode());
        userZipCode.setNickname(zipCodeDto.getNickname());
        return userZipCodeRepository.save(userZipCode);
    }

    @Override
    public void deleteZipCode(Long zipCodeId) {
        User currentUser = appUserService.getCurrentUser().orElseThrow(() -> new RuntimeException("User not found"));
        UserZipCode zipCode = userZipCodeRepository.findById(zipCodeId)
                .orElseThrow(() -> new RuntimeException("Zip code not found"));
        if (!zipCode.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("User not authorized to delete this zip code");
        }
        userZipCodeRepository.deleteById(zipCodeId);
    }
}