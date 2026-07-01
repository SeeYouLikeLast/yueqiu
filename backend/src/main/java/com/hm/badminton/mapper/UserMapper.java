package com.hm.badminton.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hm.badminton.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserAccount> {
}
