package com.hm.badminton.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.UserPublicProfile;
import com.hm.badminton.entity.PlayerProfileEntity;
import com.hm.badminton.entity.UserAccount;
import com.hm.badminton.mapper.PlayerProfileMapper;
import com.hm.badminton.mapper.UserMapper;
import com.hm.badminton.service.IAuthService;
import com.hm.badminton.service.IBloomFilterService;
import com.hm.badminton.service.IFollowService;
import com.hm.badminton.service.IPasswordService;
import com.hm.badminton.utils.RedisConstants;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthService implements IAuthService {

    private final UserMapper userMapper;
    private final PlayerProfileMapper playerProfileMapper;
    private final StringRedisTemplate redisTemplate;
    private final IBloomFilterService bloomFilterService;
    private final IFollowService followService;
    private final IPasswordService passwordService;
    private final Duration codeTtl;
    private final Duration tokenTtl;

    public AuthService(UserMapper userMapper,
                       PlayerProfileMapper playerProfileMapper,
                       StringRedisTemplate redisTemplate,
                       IBloomFilterService bloomFilterService,
                       IFollowService followService,
                       IPasswordService passwordService,
                       @Value("${hm.auth.code-expire-minutes:2}") long codeExpireMinutes,
                       @Value("${hm.auth.token-expire-minutes:120}") long tokenExpireMinutes) {
        this.userMapper = userMapper;
        this.playerProfileMapper = playerProfileMapper;
        this.redisTemplate = redisTemplate;
        this.bloomFilterService = bloomFilterService;
        this.followService = followService;
        this.passwordService = passwordService;
        this.codeTtl = Duration.ofMinutes(codeExpireMinutes);
        this.tokenTtl = Duration.ofMinutes(tokenExpireMinutes);
    }

    public CodeResponse sendCode(CodeRequest request) {
        String phone = normalizePhone(request.phone());
        assertPhone(phone);
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, codeTtl);
        return new CodeResponse(phone, code, codeTtl.toSeconds());
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String phone = normalizePhone(request.phone());
        String email = normalizeEmail(request.email());
        String username = normalizeUsername(request.username());
        assertUnique(RedisConstants.BLOOM_USER_PHONE_KEY, "phone", phone, "手机号已注册");
        assertUnique(RedisConstants.BLOOM_USER_EMAIL_KEY, "email", email, "邮箱已注册");
        assertUnique(RedisConstants.BLOOM_USER_USERNAME_KEY, "username", username, "用户名已存在");

        String nickname = request.nickname() == null || request.nickname().isBlank()
                ? "羽友" + phone.substring(phone.length() - 4)
                : request.nickname().trim();
        String city = request.city() == null || request.city().isBlank() ? "西安" : request.city().trim();
        String level = request.level() == null || request.level().isBlank() ? "新手" : request.level().trim();
        String passwordHash = passwordService.encode(request.password());

        UserAccount account = new UserAccount();
        account.setPhone(phone);
        account.setEmail(email);
        account.setUsername(username);
        account.setPasswordHash(passwordHash);
        account.setNickname(nickname);
        account.setAvatar("https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?auto=format&fit=crop&w=240&q=80");
        account.setCity(city);
        account.setLevel(level);
        account.setPreferTime("工作日晚上 / 周末下午");
        try {
            userMapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "手机号、邮箱或用户名已存在");
        }

        Long userId = account.getId();
        if (userId == null) {
            throw new BusinessException("注册失败，请重试");
        }
        playerProfileMapper.insert(defaultPlayerProfile(userId, city, "碑林区", 108.946465, 34.347269, level));

        bloomFilterService.put(RedisConstants.BLOOM_USER_PHONE_KEY, phone);
        bloomFilterService.put(RedisConstants.BLOOM_USER_EMAIL_KEY, email);
        bloomFilterService.put(RedisConstants.BLOOM_USER_USERNAME_KEY, username);

        LoginUser user = new LoginUser(userId, phone, nickname, city, level);
        return new LoginResponse(createLoginToken(user), user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if ((request.phone() != null && !request.phone().isBlank()) || (request.code() != null && !request.code().isBlank())) {
            return loginByCode(request);
        }
        return loginByPassword(request);
    }

    private LoginResponse loginByCode(LoginRequest request) {
        String phone = normalizePhone(request.phone());
        assertPhone(phone);
        String code = request.code() == null ? "" : request.code().trim();
        String cachedCode = redisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (cachedCode == null || !cachedCode.equals(code)) {
            throw new BusinessException(401, "验证码错误或已过期");
        }

        LoginUser user = findByPhone(phone);
        if (user == null) {
            user = createUserByPhone(phone);
        }
        redisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + phone);
        return new LoginResponse(createLoginToken(user), user);
    }

    private LoginResponse loginByPassword(LoginRequest request) {
        if (request.account() == null || request.account().isBlank() || request.password() == null || request.password().isBlank()) {
            throw new BusinessException(400, "手机号和验证码不能为空");
        }
        String account = request.account().trim().toLowerCase(Locale.ROOT);
        UserAccount user = userMapper.selectOne(new QueryWrapper<UserAccount>()
                .eq("status", 1)
                .and(wrapper -> wrapper.eq("phone", account).or().eq("email", account).or().eq("username", account)));
        if (user == null || !passwordService.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "账号或密码错误");
        }
        LoginUser loginUser = new LoginUser(user.getId(), user.getPhone(), user.getNickname(), user.getCity(), user.getLevel());
        return new LoginResponse(createLoginToken(loginUser), loginUser);
    }

    public Map<String, Object> me(LoginUser user) {
        UserAccount account = userMapper.selectById(user.id());
        if (account == null) {
            throw new BusinessException(404, "用户不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", account.getId());
        result.put("phone", account.getPhone());
        result.put("email", account.getEmail());
        result.put("username", account.getUsername());
        result.put("nickname", account.getNickname());
        result.put("avatar", account.getAvatar());
        result.put("city", account.getCity());
        result.put("level", account.getLevel());
        result.put("prefer_time", account.getPreferTime());
        result.put("created_at", account.getCreatedAt());
        return result;
    }

    @Override
    public UserPublicProfile publicProfile(Long userId, LoginUser currentUser) {
        UserAccount account = userMapper.selectById(userId);
        if (account == null || !Integer.valueOf(1).equals(account.getStatus())) {
            throw new BusinessException(404, "用户不存在");
        }
        Long currentUserId = currentUser == null ? null : currentUser.id();
        boolean isMe = currentUserId != null && currentUserId.equals(userId);
        boolean followed = currentUserId != null && !isMe && followService.isFollowed(currentUserId, userId);
        return new UserPublicProfile(
                account.getId(),
                account.getNickname(),
                account.getAvatar(),
                account.getCity(),
                account.getLevel(),
                account.getPreferTime(),
                account.getCreatedAt(),
                followed,
                isMe);
    }

    @Override
    public void updateLocation(String token, Long userId, LocationRequest request) {
        if (token == null || token.isBlank() || request == null) {
            return;
        }
        Map<String, String> values = new HashMap<>();
        putIfText(values, "city", request.city());
        putIfText(values, "preciseAddress", request.preciseAddress());
        if (request.lng() != null) values.put("lng", String.valueOf(request.lng()));
        if (request.lat() != null) values.put("lat", String.valueOf(request.lat()));
        if (values.isEmpty()) {
            return;
        }
        String key = RedisConstants.LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, tokenTtl);

        if (request.city() != null && !request.city().isBlank()) {
            UserAccount user = new UserAccount();
            user.setId(userId);
            user.setCity(request.city().trim());
            userMapper.updateById(user);
        }
        if (request.city() != null && !request.city().isBlank() && request.lng() != null && request.lat() != null) {
            PlayerProfileEntity profile = new PlayerProfileEntity();
            profile.setUserId(userId);
            profile.setCity(request.city().trim());
            profile.setLongitude(request.lng());
            profile.setLatitude(request.lat());
            playerProfileMapper.updateById(profile);
        }
    }

    private LoginUser findByPhone(String phone) {
        UserAccount user = userMapper.selectOne(new QueryWrapper<UserAccount>()
                .eq("phone", phone)
                .eq("status", 1));
        return toLoginUser(user);
    }

    private LoginUser createUserByPhone(String phone) {
        String nickname = "球友" + phone.substring(phone.length() - 4);
        String city = "西安";
        String level = "新手";
        UserAccount account = new UserAccount();
        account.setPhone(phone);
        account.setPasswordHash(passwordService.encode(UUID.randomUUID().toString()));
        account.setNickname(nickname);
        account.setAvatar("https://images.unsplash.com/photo-1527980965255-d3b416303d12?auto=format&fit=crop&w=240&q=80");
        account.setCity(city);
        account.setLevel(level);
        account.setPreferTime("工作日晚上 / 周末下午");
        try {
            userMapper.insert(account);
        } catch (DuplicateKeyException e) {
            LoginUser existing = findByPhone(phone);
            if (existing != null) {
                return existing;
            }
            throw new BusinessException(409, "手机号已注册");
        }
        Long userId = account.getId();
        if (userId == null) {
            throw new BusinessException("登录失败，请重试");
        }
        playerProfileMapper.insert(defaultPlayerProfile(userId, city, "未央区", 108.946465, 34.347269, level));
        bloomFilterService.put(RedisConstants.BLOOM_USER_PHONE_KEY, phone);
        return new LoginUser(userId, phone, nickname, city, level);
    }

    private String createLoginToken(LoginUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(user.id()));
        userMap.put("phone", nullToEmpty(user.phone()));
        userMap.put("nickname", nullToEmpty(user.nickname()));
        userMap.put("city", nullToEmpty(user.city()));
        userMap.put("level", nullToEmpty(user.level()));
        redisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);
        redisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, tokenTtl);
        return token;
    }

    private void assertPhone(String phone) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(400, "手机号格式不正确");
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void putIfText(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }

    private LoginUser toLoginUser(UserAccount user) {
        if (user == null) {
            return null;
        }
        return new LoginUser(user.getId(), user.getPhone(), user.getNickname(), user.getCity(), user.getLevel());
    }

    private PlayerProfileEntity defaultPlayerProfile(Long userId,
                                                     String city,
                                                     String area,
                                                     Double lng,
                                                     Double lat,
                                                     String level) {
        PlayerProfileEntity profile = new PlayerProfileEntity();
        profile.setUserId(userId);
        profile.setSportCode("badminton");
        profile.setCity(city);
        profile.setArea(area);
        profile.setLongitude(lng);
        profile.setLatitude(lat);
        profile.setLevel(level);
        profile.setPlayStyle("双打");
        profile.setAvailableTime("周末下午");
        profile.setIntro("刚加入球动，想找稳定球友。");
        profile.setAllowInvite(true);
        return profile;
    }

    private void assertUnique(String bloomKey, String column, String value, String message) {
        if (value == null || value.isBlank()) {
            return;
        }
        Boolean mightExist = bloomFilterService.mightContain(bloomKey, value);
        if (Boolean.FALSE.equals(mightExist)) {
            return;
        }
        Long count = userMapper.selectCount(new QueryWrapper<UserAccount>().eq(column, value));
        if (count != null && count > 0) {
            throw new BusinessException(409, message);
        }
    }

    private String normalizePhone(String phone) {
        return phone == null ? null : phone.replaceAll("\\s+", "");
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return username == null || username.isBlank() ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    public record RegisterRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,
            @Email(message = "邮箱格式不正确") String email,
            @Size(min = 3, max = 24, message = "用户名长度 3-24 位") String username,
            @NotBlank(message = "密码不能为空") @Size(min = 6, max = 32, message = "密码长度 6-32 位") String password,
            String nickname,
            String city,
            String level) {
    }

    public record CodeRequest(@NotBlank(message = "手机号不能为空") String phone) {
    }

    public record CodeResponse(String phone, String code, long expireSeconds) {
    }

    public record LoginRequest(String account, String password, String phone, String code) {
    }

    public record LoginResponse(String token, LoginUser user) {
    }

    public record LocationRequest(String city, String preciseAddress, Double lng, Double lat) {
    }

}

