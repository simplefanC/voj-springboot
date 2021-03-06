package com.simplefanc.voj.backend.controller.file;

import com.simplefanc.voj.backend.service.file.TestCaseService;
import com.simplefanc.voj.common.result.CommonResult;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * @Author: chenfan
 * @Date: 2021/10/5 19:51
 * @Description:
 */
@Controller
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;

    @PostMapping("/upload-testcase-zip")
    @ResponseBody
    @RequiresRoles(value = {"root", "admin", "problem_admin"}, logical = Logical.OR)
    public CommonResult<Map<Object, Object>> uploadTestcaseZip(@RequestParam("file") MultipartFile file) {
        return CommonResult.successResponse(testCaseService.uploadTestcaseZip(file));
    }

    @GetMapping("/download-testcase")
    @RequiresAuthentication
    @RequiresRoles(value = {"root", "problem_admin"}, logical = Logical.OR)
    public void downloadTestcase(@RequestParam("pid") Long pid, HttpServletResponse response) {
        testCaseService.downloadTestcase(pid, response);
    }

    @GetMapping("/download-single-testcase")
    @RequiresAuthentication
    @RequiresRoles(value = {"root", "problem_admin"}, logical = Logical.OR)
    public void downloadSingleTestCase(@RequestParam("pid") Long pid,
                                       @RequestParam("inputData") String inputData,
                                       @RequestParam("outputData") String outputData,
                                       HttpServletResponse response) {
        testCaseService.downloadSingleTestCase(pid, inputData, outputData, response);
    }

}