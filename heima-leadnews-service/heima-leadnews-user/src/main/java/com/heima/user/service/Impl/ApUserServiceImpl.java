package com.heima.user.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.dtos.LoginDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.user.mapper.ApUserMapper;
import com.heima.user.service.ApUserService;
import com.heima.utils.common.AppJwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.cli.Digest;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Wrapper;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
@Slf4j
public class ApUserServiceImpl extends ServiceImpl<ApUserMapper, ApUser> implements ApUserService {
    @Override
    public ResponseResult login(LoginDto loginDto) {
        //1.正常登陆 用户名和密码
        if (StringUtils.isNotBlank(loginDto.getPhone())&&StringUtils.isNotBlank(loginDto.getPassword())){
            ApUser user = getOne(Wrappers.<ApUser>lambdaQuery().eq(ApUser::getPhone, loginDto.getPhone()));
            if (user==null){
                return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST,"用户信息不存在");

            }
            //1.2比对密码
            String salt= user.getSalt();
            String password=loginDto.getPassword();
            String pswd = DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));
            if (!pswd.equals(user.getPassword())){
                return ResponseResult.errorResult(AppHttpCodeEnum.LOGIN_PASSWORD_ERROR);
            }
            //1.3返回数据
            String token = AppJwtUtil.getToken(user.getId().longValue());
            Map<String,Object> map=new HashMap<>();
            map.put("token",token);
            user.setSalt("");
            user.setPassword("");
            map.put("user",user);
            return ResponseResult.okResult(map);

        }else {
            //2.游客登陆
            Map<String,Object> map=new HashMap<>();
            String token = AppJwtUtil.getToken(0L);
            map.put("token",token);
            return ResponseResult.okResult(map);
        }
        //1.1查询用户信息 根据手机号查询
        //1.2比对密码

        //1.3返回数据
        //2.游客登陆

    }
}