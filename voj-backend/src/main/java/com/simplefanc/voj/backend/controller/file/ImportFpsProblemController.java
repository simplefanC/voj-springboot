package com.simplefanc.voj.backend.controller.file;

import com.simplefanc.voj.backend.service.file.ImportFpsProblemService;
import com.simplefanc.voj.common.result.CommonResult;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @Author: chenfan
 * @Date: 2021/10/5 19:45
 * @Description:
 */
@Controller
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class ImportFpsProblemController {

    private final ImportFpsProblemService importFpsProblemService;

    /**
     * @param file
     * @MethodName importFpsProblem
     * @Description zip文件导入题目 仅超级管理员可操作
     * @Return
     * @Since 2021/10/06
     */
    @RequiresRoles("root")
    @RequiresAuthentication
    @ResponseBody
    @PostMapping("/import-fps-problem")
    public CommonResult<Void> importFPSProblem(@RequestParam("file") MultipartFile file) {
        try {
            importFpsProblemService.importFPSProblem(file);
            return CommonResult.successResponse();
        } catch (IOException e) {
            return CommonResult.errorResponse(e.getMessage());
        }
    }

}