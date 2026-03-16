package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.api.feign.RemoteMailService;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.AuthRegisterRequest;
import com.oriole.wisepen.user.api.domain.dto.req.AuthPwdResetRequest;
import com.oriole.wisepen.user.api.domain.dto.req.AuthPwdResetVerifyRequest;
import com.oriole.wisepen.user.api.domain.dto.UserInfoDTO;
import com.oriole.wisepen.user.api.enums.Status;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;
import com.oriole.wisepen.user.domain.entity.UserTokenPoolEntity;
import com.oriole.wisepen.user.exception.UserErrorCode;
import com.oriole.wisepen.user.mapper.UserWalletsMapper;
import com.oriole.wisepen.user.service.UserService;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserWalletsMapper userWalletsMapper;
    private final RedisCacheManager redisCacheManager;

    private final TemplateEngine templateEngine;
    private final RemoteMailService remoteMailService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Override
    public UserEntity getUserCoreInfoByAccount(String account) {
        return userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .and(w -> w.eq(UserEntity::getUsername, account).or().eq(UserEntity::getCampusNo, account))
                .last("LIMIT 1"));
    }

    @Override
    public UserDisplayBase getUserDisplayInfoById(Long userId) {
        if (userId == null) {
            throw new ServiceException(UserErrorCode.USERNAME_EXISTED);
        }
        UserEntity userEntity = userMapper.selectById(userId);
        return BeanUtil.copyProperties(userEntity, UserDisplayBase.class);
    }

    @Override
    public Map<Long, UserDisplayBase> getUserDisplayInfoByIds(Set<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        List<UserEntity> userList = userMapper.selectBatchIds(userIds);

        if (CollectionUtils.isEmpty(userList)) {
            return Collections.emptyMap();
        }

        return userList.stream().filter(Objects::nonNull).collect(Collectors.toMap(
                UserEntity::getUserId,
                user -> BeanUtil.copyProperties(user, UserDisplayBase.class),
                (existing, replacement) -> existing
        ));
    }

    @Override
    public void register(AuthRegisterRequest req) {
        // 校验用户名是否存在
        if (userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, req.getUsername())) > 0) {
            throw new ServiceException(UserErrorCode.USERNAME_EXISTED);
        }

        // 新建未验证的学生用户
        UserEntity user = UserEntity.builder()
                .username(req.getUsername())
                .identityType(IdentityType.STUDENT)
                .status(Status.UNIDENTIFIED)
                .build();

        // 加密用户密码
        user.setPassword(BCrypt.hashpw(req.getPassword()));
        userMapper.insert(user);

        // 新建档案
        UserProfileEntity userProfile = UserProfileEntity.builder()
                .userId(user.getUserId())
                .university("复旦大学")
                .college("复旦大学")
                .build();
        userProfileMapper.insert(userProfile);

        UserTokenPoolEntity userWallets = new UserTokenPoolEntity();
        userWallets.setUserId(user.getUserId());
        userWallets.setTokenLimit(0);
        userWallets.setTokenUsed(0);
        userWalletsMapper.insert(userWallets);
    }

    @Override
    public void sendResetMail(AuthPwdResetVerifyRequest req) {
        // 查询学号对应用户
        String campusNo = req.getCampusNo();
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getCampusNo, campusNo).last("LIMIT 1"));

        if(user==null){
            log.warn("重置密码申请：学号 {} 不存在，流程静默终止", campusNo);
            return; // 处于安全考虑，不存在也不报错，防止撞库
        }

        // uid存入Redis
        String token = redisCacheManager.setPwdResetToken(user.getUserId());
        // 构建重置链接
        String resetLink = "https://wisepen.fudan.edu.cn/reset-pwd?token=" + token;

        // 构建重置邮件
        Context context = new Context();
        context.setVariable("student_id", campusNo);
        context.setVariable("reset_link", resetLink);
        context.setVariable("current_date", DateUtil.now());
        // Thymeleaf 渲染
        String emailContent = templateEngine.process("resetMailTemplate", context);

        // 构造邮件 DTO 并发送
        MailSendDTO mailDTO = MailSendDTO.builder()
                .toEmail(user.getEmail())
                .subject("密码重置申请")
                .content(emailContent) // 传递渲染后的 HTML 字符串
                .build();

        try {
            remoteMailService.sendMail(mailDTO);
            log.info("Email sent. campusNo={}, email={}", campusNo, user.getEmail());
        } catch (Exception e) {
            log.error("Email sending failed.", e);
            throw new ServiceException(UserErrorCode.EMAIL_SEND_ERROR);
        }
    }

    @Override
    public UserInfoDTO getUserInfoById(Long userId) {
        // 查核心账号
        UserEntity user = userMapper.selectById(userId);

        if (user == null) {
            return null;
        }

        // 查档案详情
        UserProfileEntity profile = userProfileMapper.selectById(user.getUserId());

        // 组装 DTO
        UserInfoDTO dto = new UserInfoDTO();
        BeanUtil.copyProperties(user, dto);

        if (profile != null) {
            BeanUtil.copyProperties(profile, dto);
        }

        return dto;
    }

    // 重置密码
    @Override
    public void resetPassword(AuthPwdResetRequest req){
        Long userId = redisCacheManager.getPwdResetUser(req.getToken());
        if(userId == null){
            throw new ServiceException(UserErrorCode.PASSWORD_RESET_FAILED);
        }

        updatePasswordByUserId(userId, req.getNewPassword());
        log.info("用户 {} 密码重置成功", userId);
    }

    // 修改密码
    public boolean updatePasswordByUserId(Long userId, String newPassword) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password(BCrypt.hashpw(newPassword))
                .updateTime(java.time.LocalDateTime.now())
                .build();
        return userMapper.updateById(user) > 0;
    }

    /**
     * 更新用户资料
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, UserInfoDTO profileDto) {
        // 加载现有实体
        UserEntity existingUser = userMapper.selectById(userId);
        if (existingUser == null) {
            throw new ServiceException(UserErrorCode.USER_NOT_EXIST);
        }
        UserProfileEntity existingProfile = userProfileMapper.selectById(userId);
        if (existingProfile == null) {
            // 若档案不存在则新建一个基础档案
            existingProfile = UserProfileEntity.builder().userId(userId).build();
        }

        IdentityType identity = existingUser.getIdentityType();

        // 复制非空字段到现有实体
        BeanUtil.copyProperties(profileDto, existingUser, CopyOptions.create().setIgnoreNullValue(true));
        BeanUtil.copyProperties(profileDto, existingProfile, CopyOptions.create().setIgnoreNullValue(true));

        // 按身份过滤字段
        if (identity == IdentityType.STUDENT) {
            existingProfile.setAcademicTitle(existingProfile.getAcademicTitle()); // 保持原值（无操作）
        } else if (identity == IdentityType.TEACHER) {
            existingProfile.setMajor(existingProfile.getMajor());
            existingProfile.setClassName(existingProfile.getClassName());
        }

        // 更新两张表
        int r1 = userMapper.updateById(existingUser);
        int r2;
        if (userProfileMapper.selectById(userId) == null) {
            r2 = userProfileMapper.insert(existingProfile);
        } else {
            r2 = userProfileMapper.updateById(existingProfile);
        }

        if (r1 == 0 || r2 == 0) {
            throw new ServiceException(UserErrorCode.UPDATE_FAILED);
        }
    }

    /**
     * 发起邮箱验证
     */
    @Override
    public void initiateEmailVerify(Long userId, int suffixType) {
        // 获取邮箱
        UserEntity user = userMapper.selectById(userId);
        String email = getEmail(suffixType, user);

        // 生成6位数字 token
        String token = RandomUtil.randomNumbers(6);

        String redisKey = "verify:token:" + token;
        String redisValue = userId + ":" + email;
        redisTemplate.opsForValue().set(redisKey, redisValue, 15, TimeUnit.MINUTES);

        // 构建邮件内容
        String content = "您的验证码为: " + token + "\n(该验证码15分钟内有效)";

        MailSendDTO mailDTO = MailSendDTO.builder()
                .toEmail(email)
                .subject("邮箱验证验证码")
                .content(content)
                .build();

        try {
            remoteMailService.sendMail(mailDTO);
            log.info("Verify email sent. userId={}, email={}", userId, email);
        } catch (Exception e) {
            log.error("Verify email sending failed.", e);
            throw new ServiceException(UserErrorCode.EMAIL_SEND_ERROR);
        }
    }

    private static String getEmail(int suffixType, UserEntity user) {
        if (user == null) {
            throw new ServiceException(UserErrorCode.USER_NOT_EXIST);
        }

        if (user.getStatus() != Status.UNIDENTIFIED) {
            throw new ServiceException(UserErrorCode.USER_STATUS_ERROR);
        }

        String campusNo = user.getCampusNo();
        if (campusNo == null) {
            throw new ServiceException(UserErrorCode.USER_NOT_EXIST);
        }

        // 简单后缀映射：0 -> @m.fudan.edu.cn, 1 -> @fudan.edu.cn
        String suffix = suffixType == 1 ? "@fudan.edu.cn" : "@m.fudan.edu.cn";
        return campusNo + suffix;
    }

    /**
     * 验证 token 并更新用户状态和邮箱
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean checkVerifyToken(String token) {
        String redisKey = "verify:token:" + token;
        String val = redisTemplate.opsForValue().get(redisKey);
        if (val == null) {
            return false;
        }

        // 删除 key 防止复用
        redisTemplate.delete(redisKey);

        String[] parts = val.split(":", 2);
        if (parts.length < 2) {
            return false;
        }

        Long userId = Long.valueOf(parts[0]);
        String email = parts[1];

        UserEntity updateUser = new UserEntity();
        updateUser.setUserId(userId);
        updateUser.setEmail(email);
        updateUser.setStatus(Status.NORMAL);
        updateUser.setUpdateTime(java.time.LocalDateTime.now());

        int r = userMapper.updateById(updateUser);
        return r > 0;
    }
}