package com.hm.badminton.service.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.UserPublicProfile;
import com.hm.badminton.dto.auth.CodeRequest;
import com.hm.badminton.dto.auth.CodeResponse;
import com.hm.badminton.dto.auth.LocationRequest;
import com.hm.badminton.dto.auth.LoginRequest;
import com.hm.badminton.dto.auth.LoginResponse;
import com.hm.badminton.dto.auth.ProfileUpdateRequest;
import com.hm.badminton.dto.auth.RegisterRequest;
import com.hm.badminton.entity.PlayerProfileEntity;
import com.hm.badminton.entity.UserAccount;
import com.hm.badminton.mapper.social.PlayerProfileMapper;
import com.hm.badminton.mapper.auth.UserMapper;
import com.hm.badminton.service.auth.IAuthService;
import com.hm.badminton.service.auth.IBloomFilterService;
import com.hm.badminton.service.community.IFollowService;
import com.hm.badminton.service.auth.IPasswordService;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.utils.CacheClient;
import com.hm.badminton.utils.RedisTtl;

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
    private final CacheClient cacheClient;
    private final Duration codeTtl;
    private final Duration tokenTtl;
    private final long tokenTtlJitterMaxSeconds;

    public AuthService(UserMapper userMapper,
                       PlayerProfileMapper playerProfileMapper,
                       StringRedisTemplate redisTemplate,
                       IBloomFilterService bloomFilterService,
                       IFollowService followService,
                       IPasswordService passwordService,
                       CacheClient cacheClient,
                       @Value("${hm.auth.code-expire-minutes:2}") long codeExpireMinutes,
                       @Value("${hm.auth.token-expire-minutes:120}") long tokenExpireMinutes,
                       @Value("${hm.auth.token-expire-jitter-minutes:10}") long tokenExpireJitterMinutes) {
        this.userMapper = userMapper;
        this.playerProfileMapper = playerProfileMapper;
        this.redisTemplate = redisTemplate;
        this.bloomFilterService = bloomFilterService;
        this.followService = followService;
        this.passwordService = passwordService;
        this.cacheClient = cacheClient;
        this.codeTtl = Duration.ofMinutes(codeExpireMinutes);
        this.tokenTtl = Duration.ofMinutes(tokenExpireMinutes);
        this.tokenTtlJitterMaxSeconds = Duration.ofMinutes(tokenExpireJitterMinutes).toSeconds();
    }

    public CodeResponse sendCode(CodeRequest request) {
        String phone = normalizePhone(request.getPhone());
        assertPhone(phone);
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, codeTtl);
        return new CodeResponse(phone, code, codeTtl.toSeconds());
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String phone = normalizePhone(request.getPhone());
        String email = normalizeEmail(request.getEmail());
        String username = normalizeUsername(request.getUsername());
        assertUnique(RedisConstants.BLOOM_USER_PHONE_KEY, "phone", phone, "手机号已注册");
        assertUnique(RedisConstants.BLOOM_USER_EMAIL_KEY, "email", email, "邮箱已注册");
        assertUnique(RedisConstants.BLOOM_USER_USERNAME_KEY, "username", username, "用户名已存在");

        String nickname = request.getNickname() == null || request.getNickname().isBlank()
                ? "球友" + phone.substring(phone.length() - 4)
                : request.getNickname().trim();
        String city = request.getCity() == null || request.getCity().isBlank() ? "西安" : request.getCity().trim();
        String level = request.getLevel() == null || request.getLevel().isBlank() ? "初级" : request.getLevel().trim();
        String passwordHash = passwordService.encode(request.getPassword());

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
            throw new BusinessException("注册失败，请稍后再试");
        }
        playerProfileMapper.insert(defaultPlayerProfile(userId, city, "雁塔区", 108.946465, 34.347269, level));

        bloomFilterService.put(RedisConstants.BLOOM_USER_PHONE_KEY, phone);
        bloomFilterService.put(RedisConstants.BLOOM_USER_EMAIL_KEY, email);
        bloomFilterService.put(RedisConstants.BLOOM_USER_USERNAME_KEY, username);

        LoginUser user = new LoginUser(userId, phone, nickname, city, level);
        return new LoginResponse(createLoginToken(user), user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if ((request.getPhone() != null && !request.getPhone().isBlank()) || (request.getCode() != null && !request.getCode().isBlank())) {
            return loginByCode(request);
        }
        return loginByPassword(request);
    }

    private LoginResponse loginByCode(LoginRequest request) {
        String phone = normalizePhone(request.getPhone());
        assertPhone(phone);
        String code = request.getCode() == null ? "" : request.getCode().trim();
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
        if (request.getAccount() == null || request.getAccount().isBlank() || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(400, "账号和密码不能为空");
        }
        String account = request.getAccount().trim().toLowerCase(Locale.ROOT);
        UserAccount user = userMapper.selectOne(new QueryWrapper<UserAccount>()
                .eq("status", 1)
                .and(wrapper -> wrapper.eq("phone", account).or().eq("email", account).or().eq("username", account)));
        if (user == null || !passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "账号或密码错误");
        }
        LoginUser loginUser = new LoginUser(user.getId(), user.getPhone(), user.getNickname(), user.getCity(), user.getLevel());
        return new LoginResponse(createLoginToken(loginUser), loginUser);
    }

    public Map<String, Object> me(LoginUser user) {
        UserAccount account = userMapper.selectById(user.getId());
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
    @Transactional
    public Map<String, Object> updateMe(Long userId, ProfileUpdateRequest request) {
        if (request == null) {
            throw new BusinessException("资料不能为空");
        }
        UserAccount current = userMapper.selectById(userId);
        if (current == null || !Integer.valueOf(1).equals(current.getStatus())) {
            throw new BusinessException(404, "用户不存在");
        }

        String email = normalizeEmail(request.getEmail());
        String username = normalizeUsername(request.getUsername());
        if (email != null && !email.equals(current.getEmail())) {
            assertUnique(RedisConstants.BLOOM_USER_EMAIL_KEY, "email", email, "邮箱已注册");
        }
        if (username != null && !username.equals(current.getUsername())) {
            assertUnique(RedisConstants.BLOOM_USER_USERNAME_KEY, "username", username, "用户名已存在");
        }

        UserAccount update = new UserAccount();
        update.setId(userId);
        update.setEmail(email);
        update.setUsername(username);
        putText(update::setNickname, request.getNickname());
        putText(update::setAvatar, request.getAvatar());
        putText(update::setCity, request.getCity());
        putText(update::setLevel, request.getLevel());
        putText(update::setPreferTime, request.getPreferTime());
        userMapper.updateById(update);

        if (email != null && !email.equals(current.getEmail())) {
            bloomFilterService.put(RedisConstants.BLOOM_USER_EMAIL_KEY, email);
        }
        if (username != null && !username.equals(current.getUsername())) {
            bloomFilterService.put(RedisConstants.BLOOM_USER_USERNAME_KEY, username);
        }
        cacheClient.delete(RedisConstants.USER_PROFILE_KEY + userId);
        UserAccount updated = userMapper.selectById(userId);
        return me(new LoginUser(updated.getId(), updated.getPhone(), updated.getNickname(), updated.getCity(), updated.getLevel()));
    }

    @Override
    public UserPublicProfile publicProfile(Long userId, LoginUser currentUser) {
        UserPublicProfile profile = cacheClient.queryWithPassThrough(
                RedisConstants.USER_PROFILE_KEY + userId,
                UserPublicProfile.class,
                () -> loadPublicProfile(userId),
                RedisConstants.CACHE_DETAIL_TTL,
                RedisConstants.CACHE_DETAIL_JITTER_SECONDS,
                "用户不存在");
        Long currentUserId = currentUser == null ? null : currentUser.getId();
        boolean isMe = currentUserId != null && currentUserId.equals(userId);
        boolean followed = currentUserId != null && !isMe && followService.isFollowed(currentUserId, userId);
        profile.setMe(isMe);
        profile.setFollowed(followed);
        return profile;
    }

    @Override
    public void updateLocation(String token, Long userId, LocationRequest request) {
        if (token == null || token.isBlank() || request == null) {
            return;
        }
        Map<String, String> values = new HashMap<>();
        putIfText(values, "city", request.getCity());
        if (request.getLng() != null) values.put("lng", String.valueOf(request.getLng()));
        if (request.getLat() != null) values.put("lat", String.valueOf(request.getLat()));
        if (values.isEmpty()) {
            return;
        }
        String key = RedisConstants.LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, RedisTtl.withJitter(tokenTtl, tokenTtlJitterMaxSeconds));

        if (request.getCity() != null && !request.getCity().isBlank()) {
            UserAccount user = new UserAccount();
            user.setId(userId);
            user.setCity(request.getCity().trim());
            userMapper.updateById(user);
        }
        if (request.getCity() != null && !request.getCity().isBlank() && request.getLng() != null && request.getLat() != null) {
            PlayerProfileEntity profile = new PlayerProfileEntity();
            profile.setUserId(userId);
            profile.setCity(request.getCity().trim());
            profile.setLongitude(request.getLng());
            profile.setLatitude(request.getLat());
            playerProfileMapper.updateById(profile);
        }
    }

    private LoginUser findByPhone(String phone) {
        UserAccount user = userMapper.selectOne(new QueryWrapper<UserAccount>()
                .eq("phone", phone)
                .eq("status", 1));
        return toLoginUser(user);
    }

    private UserPublicProfile loadPublicProfile(Long userId) {
        UserAccount account = userMapper.selectById(userId);
        if (account == null || !Integer.valueOf(1).equals(account.getStatus())) {
            return null;
        }
        return new UserPublicProfile(
                account.getId(),
                account.getNickname(),
                account.getAvatar(),
                account.getCity(),
                account.getLevel(),
                account.getPreferTime(),
                account.getCreatedAt(),
                false,
                false);
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
            throw new BusinessException(409, "手机号、邮箱或用户名已存在");
        }
        Long userId = account.getId();
        if (userId == null) {
            throw new BusinessException("登录失败，请重试");
        }
        playerProfileMapper.insert(defaultPlayerProfile(userId, city, "雁塔区", 108.946465, 34.347269, level));
        bloomFilterService.put(RedisConstants.BLOOM_USER_PHONE_KEY, phone);
        return new LoginUser(userId, phone, nickname, city, level);
    }

    private String createLoginToken(LoginUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(user.getId()));
        userMap.put("city", nullToEmpty(user.getCity()));
        redisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);
        redisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisTtl.withJitter(tokenTtl, tokenTtlJitterMaxSeconds));
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

    private void putText(java.util.function.Consumer<String> setter, String value) {
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
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
        profile.setIntro("刚加入球圈，想找稳定球友。");
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
            throw new BusinessException(409, "手机号、邮箱或用户名已存在");
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

}


