package com.simplefanc.voj.service.file.impl;

import com.alibaba.excel.EasyExcel;
import com.simplefanc.voj.pojo.vo.ExcelUserVo;
import com.simplefanc.voj.service.file.UserFileService;
import com.simplefanc.voj.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

/**
 * @Author: chenfan
 * @Date: 2022/3/10 15:02
 * @Description:
 */
@Service
@Slf4j(topic = "voj")
public class UserFileServiceImpl implements UserFileService {

    @Autowired
    private RedisUtils redisUtils;

    @Override
    public void generateUserExcel(String key, HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        // 这里URLEncoder.encode可以防止中文乱码
        String fileName = URLEncoder.encode(key, "UTF-8");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
        response.setHeader("Content-Type", "application/xlsx");
        EasyExcel.write(response.getOutputStream(), ExcelUserVo.class).sheet("用户数据").doWrite(getGenerateUsers(key));
    }

    private List<ExcelUserVo> getGenerateUsers(String key) {
        return (List<ExcelUserVo>) redisUtils.hget("USER_INFO_LIST", key);
    }
}