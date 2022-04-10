package com.simplefanc.voj.controller.oj;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.simplefanc.voj.common.result.CommonResult;
import com.simplefanc.voj.pojo.entity.problem.CodeTemplate;
import com.simplefanc.voj.pojo.entity.problem.Language;
import com.simplefanc.voj.pojo.entity.problem.Tag;
import com.simplefanc.voj.pojo.entity.training.TrainingCategory;
import com.simplefanc.voj.pojo.vo.CaptchaVo;
import com.simplefanc.voj.service.oj.CommonService;

import java.util.Collection;
import java.util.List;

/**
 * @Author: chenfan
 * @Date: 2020/12/12 23:25
 * @Description: 通用的请求控制处理类
 */
@RestController
@RequestMapping("/api")
public class CommonController {

    @Autowired
    private CommonService commonService;


    @GetMapping("/captcha")
    public CommonResult<CaptchaVo> getCaptcha() {
        return CommonResult.successResponse(commonService.getCaptcha());
    }


    @GetMapping("/get-training-category")
    public CommonResult<List<TrainingCategory>> getTrainingCategory() {
        return CommonResult.successResponse(commonService.getTrainingCategory());
    }

    @GetMapping("/get-all-problem-tags")
    public CommonResult<List<Tag>> getAllProblemTagsList(@RequestParam(value = "oj", defaultValue = "ME") String oj) {
        return CommonResult.successResponse(commonService.getAllProblemTagsList(oj));
    }

    @GetMapping("/get-problem-tags")
    public CommonResult<Collection<Tag>> getProblemTags(Long pid) {
        return CommonResult.successResponse(commonService.getProblemTags(pid));
    }


    @GetMapping("/languages")
    public CommonResult<List<Language>> getLanguages(@RequestParam(value = "pid", required = false) Long pid,
                                                     @RequestParam(value = "all", required = false) Boolean all) {
        return CommonResult.successResponse(commonService.getLanguages(pid, all));
    }

    @GetMapping("/get-Problem-languages")
    public CommonResult<Collection<Language>> getProblemLanguages(@RequestParam("pid") Long pid) {
        return CommonResult.successResponse(commonService.getProblemLanguages(pid));
    }

    @GetMapping("/get-problem-code-template")
    public CommonResult<List<CodeTemplate>> getProblemCodeTemplate(@RequestParam("pid") Long pid) {
        return CommonResult.successResponse(commonService.getProblemCodeTemplate(pid));
    }

}