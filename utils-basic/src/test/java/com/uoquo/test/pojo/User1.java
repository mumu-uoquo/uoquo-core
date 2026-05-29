/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.pojo;

import com.uoquo.annotation.json.Sensitive;
import com.uoquo.annotation.json.SensitiveType;

import java.util.Date;
import java.util.List;

public class User1 {

    private String id;

    @Sensitive(type = SensitiveType.NAME)
    private String name;
    private Integer age;
    private Long time;
    private Float score;
    private Double money;
    private Date birthday;
    private List<User1> friends;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return this.age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Long getTime() {
        return this.time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Float getScore() {
        return this.score;
    }

    public void setScore(Float score) {
        this.score = score;
    }

    public Double getMoney() {
        return this.money;
    }

    public void setMoney(Double money) {
        this.money = money;
    }

    public Date getBirthday() {
        return this.birthday;
    }

    public void setBirthday(Date birthday) {
        this.birthday = birthday;
    }

    public List<User1> getFriends() {
        return this.friends;
    }

    public void setFriends(List<User1> friends) {
        this.friends = friends;
    }
}
