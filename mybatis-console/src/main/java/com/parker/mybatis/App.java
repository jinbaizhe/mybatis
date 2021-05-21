package com.parker.mybatis;

import com.alibaba.fastjson.JSON;
import com.parker.mybatis.mapper.UserMapper;
import com.parker.mybatis.po.User;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;
import java.io.InputStream;

public class App {

    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {

        //原生方式运行MyBatis
//        rawWay();

        //以spring方式运行MyBatis
        springWay();

    }

    /**
     * 以spring方式运行MyBatis
     */
    private static void springWay() {
        ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
        UserMapper userMapper = (UserMapper)context.getBean("userMapper");
        User user2 = userMapper.findById(1);
        LOGGER.info("以spring方式运行MyBatis:user=[{}]", JSON.toJSONString(user2));
    }

    /**
     * 原生方式运行MyBatis
     */
    private static void rawWay() {
        String resource = "mybatis-config.xml";
        InputStream inputStream = null;
        try {
            inputStream = Resources.getResourceAsStream(resource);
        } catch (IOException e) {
            e.printStackTrace();
        }
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        SqlSession sqlSession = sqlSessionFactory.openSession();
        try {
            User user1 = sqlSession.selectOne("com.parker.mybatis.mapper.UserMapper.findById", 1);
            LOGGER.info("原生方式运行MyBatis:user=[{}]", JSON.toJSONString(user1));

            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            User user2 = userMapper.findById(1);
            LOGGER.info("原生方式运行MyBatis:user=[{}]", JSON.toJSONString(user2));
        } finally {
            sqlSession.close();
        }
    }
}
