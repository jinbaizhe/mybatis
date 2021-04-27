package com.parker.mybatis.mapper;

import com.parker.mybatis.po.User;

public interface UserMapper {

    User findById(Integer id);

}
