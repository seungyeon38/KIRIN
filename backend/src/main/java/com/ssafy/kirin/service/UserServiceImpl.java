package com.ssafy.kirin.service;

import com.ssafy.kirin.dto.UserDTO;
import com.ssafy.kirin.dto.request.UserFindPWRequestDTO;
import com.ssafy.kirin.dto.request.UserLoginRequestDTO;
import com.ssafy.kirin.dto.request.UserModifyRequestDTO;
import com.ssafy.kirin.dto.request.UserSignupRequestDTO;
import com.ssafy.kirin.dto.response.CelebResponseDTO;
import com.ssafy.kirin.dto.response.UserResponseDTO;
import com.ssafy.kirin.entity.*;
import com.ssafy.kirin.repository.*;
import com.ssafy.kirin.util.Web3jUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.MessagingException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserDetailsService, UserService {
    private final UserRepository userRepository;
    private final CelebInfoRepository celebInfoRepository;
    private final EmailAuthRepository emailAuthRepository;
    private final EmailAuthRepositoryCustom emailAuthRepositoryCustom;
    private final EmailService emailService;
    private final SubscribeRepository subscribeRepository;
    private final EthereumService ethereumService;

    @Value("${property.app.upload-path}")
    private String uploadPath;

    @Override
    public void signup(UserSignupRequestDTO userSignupRequestDTO, MultipartFile profileImg, PasswordEncoder passwordEncoder) throws Exception {
        User user = null;

        // email, password null check && email, nickname ?????? check -> ??????, ?????????
        if(userSignupRequestDTO.getEmail() == null || userSignupRequestDTO.getPassword() == null){ // email, password null check
            log.info("email, password null");
            throw new Exception();
        }

        if(userRepository.existsByEmail(userSignupRequestDTO.getEmail()) || userRepository.existsByNickname(userSignupRequestDTO.getNickname())){ // email,nickname ?????? check
            log.info("email, nickname ??????");
            throw new Exception();
        }

        userSignupRequestDTO.setPassword(passwordEncoder.encode(userSignupRequestDTO.getPassword()));
        // wallet ???????????? ???????????????.
        Wallet wallet = ethereumService.createWallet();
        if(userSignupRequestDTO.getIsCeleb()){ // ????????? ??????
            CelebInfo celebInfo = new CelebInfo(); // info, coverImg??? ?????? ??????



            user = User.builder()
                    .name(userSignupRequestDTO.getName())
                    .nickname(userSignupRequestDTO.getNickname())
                    .profileImg(getFilePath(profileImg)) // null?????????
                    .email(userSignupRequestDTO.getEmail())
                    .password(userSignupRequestDTO.getPassword())
                    .isCeleb(userSignupRequestDTO.getIsCeleb())
                    .reg(LocalDateTime.now())
                    .wallet(wallet)
                    .build();

            user.setCelebInfo(celebInfoRepository.save(celebInfo));
        } else { // ???????????? ??????
            log.info("????????? ????????????");

            // wallet ???????????? ???????????????.

            user = User.builder()
                    .name(userSignupRequestDTO.getName())
                    .nickname(userSignupRequestDTO.getNickname())
                    .profileImg(getFilePath(profileImg)) // null?????????
                    .email(userSignupRequestDTO.getEmail())
                    .password(userSignupRequestDTO.getPassword())
                    .isCeleb(userSignupRequestDTO.getIsCeleb())
                    .reg(LocalDateTime.now())
                    .wallet(wallet)
                    .build();
        }
        userRepository.save(user);

        // ????????? verify ??????
        EmailAuth emailAuth = emailAuthRepository.save(
                new EmailAuth(userSignupRequestDTO.getEmail(), UUID.randomUUID().toString(), false)
        );

        emailService.sendVerifyMail(emailAuth.getEmail(), emailAuth.getAuthToken()); // ????????? ?????? ?????? ?????????
    }

    @Override
    public void confirmEmail(String email, String authToken) {
        EmailAuth emailAuth = emailAuthRepositoryCustom.findValidAuthByEmail(email, authToken, LocalDateTime.now()) // ?????????, authToken, ??????, ???????????? check
                .orElseThrow(() -> new NoSuchElementException("EmailAuth : " + email + " was not found"));
        User user = userRepository.findByEmail(email).orElseThrow(() -> new NoSuchElementException("User : " + email + " was not found"));

        emailAuth.useToken(); // ?????? ????????????
        user.emailVerifiedSuccess(); // ?????? ????????? ?????? ?????? ??????
        emailAuthRepository.save(emailAuth);
        userRepository.save(user);
    }

    @Override
    public UserResponseDTO login(UserLoginRequestDTO userLoginRequestDTO, PasswordEncoder passwordEncoder) {
        User user = userRepository.findByEmail(userLoginRequestDTO.getEmail())
                .orElseThrow(() -> new NoSuchElementException("User : " + userLoginRequestDTO.getEmail() + " was not found"));

        if(!user.getIsEmailVerified()){ // ????????? ????????? ?????? ??????
            log.error("login ??????: ????????? ?????? ??????");
            return null;
        }

        if(user.getIsCeleb() && !user.getIsCelebVerified()){ // ?????? ??????????????? ?????? ????????? ?????? ??????
            log.error("login ??????: ?????? ?????? ??????");
            return null;
        }

        if(!passwordEncoder.matches(userLoginRequestDTO.getPassword(), user.getPassword())){ // ??????????????? ???????????? ?????? ??????
            log.error("login ??????: ???????????? ??????");
            return null;
        }

        return userToUserDto(user);
    }

    @Override
    public UserResponseDTO modifyUser(long userId, String nickname, MultipartFile profileImg) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User : " + userId + " was not found"));

        // ?????? profile ????????? ?????????
        if(user.getProfileImg() != null){
            deleteFile(user.getProfileImg());
        }

        user.setProfileImgAndNickname(getFilePath(profileImg), nickname);

        userRepository.save(user); // user update

        return userToUserDto(user);
    }

    @Override
    public void subscribe(long userId, long starId) throws Exception {
        if(userId == starId) {
            log.error("?????? ????????? ???????????????.");
            throw new Exception();
        }

        // celebId??? celeb?????? ??????
        User celeb = userRepository.findById(starId)
                .orElseThrow(() -> new NoSuchElementException("Star : " + starId + " was not found"));

        if(!celeb.getIsCeleb()){
            log.error("celeb ????????? ????????????.");
            throw new Exception();
        }

        // subscribe??? ??????????????? ??????
        Subscribe subscribe = subscribeRepository.findByUserIdAndCelebId(userId, starId).orElse(null);

        if(subscribe != null){ // ?????? ??????
            subscribeRepository.delete(subscribe);
        } else { // ??????
            Subscribe newSubscribe = Subscribe.builder()
                    .userId(userId)
                    .celebId(starId)
                    .build();

            subscribeRepository.save(newSubscribe);
        }
    }

    @Override
    public List<UserResponseDTO> getCelebList() {
        List<User> users = userRepository.findByIsCeleb(true);
        List<UserResponseDTO> result = new ArrayList<>();

        for(User user: users){
            result.add(userToUserDto(user));
        }

        return result;
    }

    @Override
    public List<UserResponseDTO> getCelebListById(long userId) {
        List<Subscribe> subscribes = subscribeRepository.findByUserId(userId);
        List<UserResponseDTO> result = new ArrayList<>();

        for(Subscribe subscribe: subscribes){
            UserResponseDTO userDTO = userToUserDto(userRepository.findById(subscribe.getCelebId())
                    .orElseThrow(() -> new NoSuchElementException("Star : " + subscribe.getCelebId() + " was not found")));
            result.add(userDTO);
        }

        return result;
    }

    @Override
    public CelebResponseDTO getCelebInfo(long userId, long starId) {
        User user = userRepository.findById(starId)
                .orElseThrow(() -> new NoSuchElementException("Star : " + starId + " was not found"));

        if(!user.getIsCeleb()){
            log.error("????????? ??????");
            return null;
        }

        Subscribe subscribe = subscribeRepository.findByUserIdAndCelebId(userId, starId).orElse(null);

        if(subscribe == null){
            return CelebResponseDTO.builder()
                    .id(user.getId())
                    .nickname(user.getNickname())
                    .profileImg(user.getProfileImg())
                    .coverImg(user.getCelebInfo().getCoverImg())
                    .info(user.getCelebInfo().getInfo())
                    .isSubscribed(false)
                    .build();
        } else {
            return CelebResponseDTO.builder()
                    .id(user.getId())
                    .nickname(user.getNickname())
                    .profileImg(user.getProfileImg())
                    .coverImg(user.getCelebInfo().getCoverImg())
                    .info(user.getCelebInfo().getInfo())
                    .isSubscribed(true)
                    .build();
        }
    }

    @Override
    public boolean checkEmailDuplicate(String email) {
        boolean isEmailDupliated = userRepository.existsByEmail(email);

        if(isEmailDupliated) return false;

        return true;
    }

    @Override
    public boolean checkNicknameDuplicate(String nickname) {
        boolean isNicknameDupliated = userRepository.existsByNickname(nickname);

        if(isNicknameDupliated) return false;

        return true;
    }

    @Override
    public UserDTO loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new NoSuchElementException("User : " + userId + " was not found"));

        // ?????????, ?????? role ??????

        return UserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .nickname(user.getNickname())
                .profileImg(user.getProfileImg())
                .email(user.getEmail())
                .walletAddress(user.getWallet().getAddress())
                .build();
    }

    @Transactional(readOnly = true)
    public Map<String, String> validateHandling(Errors errors){
        Map<String, String> validatorResult = new HashMap<>();

        // ????????? ????????? ????????? ?????? ????????? ??????
        for (FieldError error : errors.getFieldErrors()) {
            String validKeyName = String.format("valid_%s", error.getField());
            validatorResult.put(validKeyName, error.getDefaultMessage());
        }

        return validatorResult;
    }

    @Override
    public void findPassword(String email, String name, PasswordEncoder passwordEncoder) throws Exception {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("?????? ???????????? ?????? ???????????? ???????????? ????????????."));

        if(user.getName().equals(name)){
            String newPassword = getRamdomPassword(10);
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            emailService.sendNewPasswordMail(email, newPassword);
        } else {
            throw new Exception("????????? ????????? ???????????? ????????????.");
        }
    }

    @Override
    public void updatePassword(UserFindPWRequestDTO userFindPWRequestDTO, PasswordEncoder passwordEncoder) throws Exception {
        User user = userRepository.findById(userFindPWRequestDTO.getUserId())
                .orElseThrow(() -> new NoSuchElementException("User : " + userFindPWRequestDTO.getUserId() + " was not found"));

        if(passwordEncoder.matches(userFindPWRequestDTO.getPassword(), user.getPassword())){
            user.setPassword(passwordEncoder.encode(userFindPWRequestDTO.getNewPassword()));
            userRepository.save(user);
        } else {
            throw new Exception();
        }
    }

    @Override
    public void updateCoverImg(long starId, MultipartFile coverImg) throws Exception {
        User user = userRepository.findById(starId)
                .orElseThrow(() -> new NoSuchElementException("Star : " + starId + " was not found"));

        if(!user.getIsCeleb()){
            throw new Exception();
        } else {
            CelebInfo celebInfo = user.getCelebInfo();

            if(user.getCelebInfo().getCoverImg() != null){
                // ?????? ?????? ?????????
                deleteFile(celebInfo.getCoverImg());
            }
            celebInfo.setCoverImg(getFilePath(coverImg));

            celebInfoRepository.save(celebInfo);
        }
    }

    @Override
    public void updateStarInfo(long starId, String info) throws Exception {
        User user = userRepository.findById(starId)
                .orElseThrow(() -> new NoSuchElementException("Star : " + starId + " was not found"));

        if(!user.getIsCeleb()){
            throw new Exception();
        } else {
            CelebInfo celebInfo = user.getCelebInfo();

            celebInfo.setInfo(info);

            celebInfoRepository.save(celebInfo);
        }
    }

    @Override
    public UserResponseDTO getUserProfile(UserDTO userDTO) {
        UserResponseDTO userResponseDTO = UserResponseDTO.builder()
                .id(userDTO.getId())
                .nickname(userDTO.getNickname())
                .profileImg(userDTO.getProfileImg())
                .walletAddress(userDTO.getWalletAddress())
                .build();

        return userResponseDTO;
    }

    @Override
    public void reissueEmailAuth(String email, String authToken) throws MessagingException, UnsupportedEncodingException {
        EmailAuth emailAuth = emailAuthRepository.findByEmailAndAuthToken(email, authToken)
                .orElseThrow(() -> new NoSuchElementException("EmailAuth : " + email + " was not found"));

        emailAuthRepository.delete(emailAuth); // ?????? ?????? ??? ??????

        EmailAuth newEmailAuth = emailAuthRepository.save(
                new EmailAuth(email, UUID.randomUUID().toString(), false)
        );

        emailService.sendVerifyMail(newEmailAuth.getEmail(), newEmailAuth.getAuthToken()); // ????????? ?????? ?????? ?????????
    }

    private UserResponseDTO userToUserDto(User user){
        return UserResponseDTO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .profileImg(user.getProfileImg())
                .walletAddress(user.getWallet().getAddress())
                .build();
    }

    private String getFilePath(MultipartFile file) { // file??? docker volume??? ????????????, ??????+????????? return
        if(file != null && !file.isEmpty()) {
            System.out.println("file ?????????");
            try{
                // ?????? ???????????? + UUID + ???????????? Path ??????
                String fileName = UUID.randomUUID() + file.getOriginalFilename();
                Path dir = Paths.get(uploadPath + fileName);

                // ????????? ??????????????? ??????
                Files.copy(file.getInputStream(), dir);

                return fileName;
            } catch (Exception e){
                log.error("getFilePath error: ", e);
            }
        }

        log.error("file??? ???????????? ??????");
        return null;
    }

    private void deleteFile(String fileDir) {
        if(fileDir != null){
            try {
                Path filePath = Paths.get(fileDir);
                Files.delete(filePath);
            } catch (Exception e){
                log.error("?????? ?????? ??????: ", e);
            }
        }
    }

    public static String getRamdomPassword(int size) {
        char[] charSet1 = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
        char[] charSet2 = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
        char[] charSet3 = {'!', '@', '#', '$', '%', '^', '&' };

        StringBuffer sb = new StringBuffer();
        SecureRandom sr = new SecureRandom();
        sr.setSeed(new Date().getTime());

        int idx = 0;
        int len = charSet1.length;
        int size1 = sr.nextInt(size-2)+1;
        size-=size1;
        for (int i=0; i<size1; i++) {
            idx = sr.nextInt(len);
            sb.append(charSet1[idx]);
        }
        len = charSet2.length;
        size1 = sr.nextInt(size-1)+1;
        size-=size1;
        for (int i=0; i<size1; i++) {
            idx = sr.nextInt(len);
            sb.append(charSet2[idx]);
        }
        len = charSet3.length;
        for (int i=0; i<size; i++) {
            idx = sr.nextInt(len);
            sb.append(charSet3[idx]);
        }
        List<String> list = Arrays.asList(sb.toString().split(""));
        Collections.shuffle(list);
        sb = new StringBuffer();
        for (int i=0, n = list.size(); i<n; i++){
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
