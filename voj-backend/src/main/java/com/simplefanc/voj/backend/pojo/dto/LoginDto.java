package com.simplefanc.voj.backend.pojo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * @Author: chenfan
 * @Date: 2021/7/20 00:23
 * @Description: 登录数据实体类
 */
@Data
public class LoginDto implements Serializable {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

}