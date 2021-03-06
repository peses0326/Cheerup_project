package com.cheerup.cheerup.service;

import com.cheerup.cheerup.dto.SignupRequestDto;
import com.cheerup.cheerup.model.User;
import com.cheerup.cheerup.model.UserRole;
import com.cheerup.cheerup.repository.UserRepository;
import com.cheerup.cheerup.security.kakao.KakaoOAuth2;
import com.cheerup.cheerup.security.kakao.KakaoUserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final KakaoOAuth2 kakaoOAuth2;
    private final AuthenticationManager authenticationManager;
    private static final String ADMIN_TOKEN = "AAABnv/xRVklrnYxKZ0aHgTBcXukeZygoC";

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, KakaoOAuth2 kakaoOAuth2, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.kakaoOAuth2 = kakaoOAuth2;
        this.authenticationManager = authenticationManager;
    }

    public Map<String, String> validateHandling(Errors errors) {
        Map<String, String> validatorResult = new HashMap<>();

        for (FieldError error : errors.getFieldErrors()) {
            String validKeyName = String.format("valid_%s", error.getField());
            validatorResult.put(validKeyName, error.getDefaultMessage());
        }

        return validatorResult;
    }

    public String registerUser(SignupRequestDto requestDto) {
        String username = requestDto.getUsername();
        String errorMessage = "";
        // ?????? ID ?????? ??????
        Optional<User> found = userRepository.findByUsername(username);
        if (username.equals("null") || username.equals("admin")) {
            errorMessage = "???????????? ID?????????.";
            return errorMessage;
        }
        if (found.isPresent()) {
            errorMessage = "????????? ????????? ID??? ???????????????.";
            return errorMessage;
        }
        if (requestDto.getPassword().contains(username)) {
            errorMessage = "PW??? ID??? ???????????? ?????????????????????.";
            return errorMessage;
        }
        if (!requestDto.getPassword().equals(requestDto.getPasswordChecker())) {
            errorMessage = "PW ????????? ???????????????.";
            return errorMessage;
        }
        // ???????????? ?????????
        String password = passwordEncoder.encode(requestDto.getPassword());
        // ????????? ROLE ??????
        UserRole role = UserRole.USER;
        if (requestDto.isAdmin()) {
            if (!requestDto.getAdminToken().equals(ADMIN_TOKEN)) {
                errorMessage = "????????? ????????? ?????? ????????? ??????????????????.";
                return errorMessage;
            }
            role = UserRole.ADMIN;
        }

        User user = new User(username, password, role);
        userRepository.save(user);
        return errorMessage;
    }

    public void kakaoLogin(String authorizedCode) {
        // ????????? OAuth2 ??? ?????? ????????? ????????? ?????? ??????
        KakaoUserInfo userInfo = kakaoOAuth2.getUserInfo(authorizedCode);
        Long kakaoId = userInfo.getId();
        String nickname = userInfo.getNickname();


        // ?????? DB ?????? ?????? Id ??? ????????????
        // ?????? Id = ????????? nickname
        String username = nickname;
        // ???????????? = ????????? Id + ADMIN TOKEN
        String password = kakaoId + ADMIN_TOKEN;

        // DB ??? ????????? Kakao Id ??? ????????? ??????
        User kakaoUser = userRepository.findByKakaoId(kakaoId)
                .orElse(null);

        // ????????? ????????? ????????????
        if (kakaoUser == null) {
            // ???????????? ?????????
            String encodedPassword = passwordEncoder.encode(password);
            // ROLE = ?????????
            UserRole role = UserRole.USER;

            kakaoUser = new User(nickname, encodedPassword, role, kakaoId);
            userRepository.save(kakaoUser);
        }

        // ????????? ??????
        Authentication kakaoUsernamePassword = new UsernamePasswordAuthenticationToken(username, password);
        Authentication authentication = authenticationManager.authenticate(kakaoUsernamePassword);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}