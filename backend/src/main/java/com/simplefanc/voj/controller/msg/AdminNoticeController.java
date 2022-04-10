package com.simplefanc.voj.controller.msg;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.*;
import com.simplefanc.voj.common.result.CommonResult;
import com.simplefanc.voj.pojo.entity.msg.AdminSysNotice;
import com.simplefanc.voj.pojo.vo.AdminSysNoticeVo;
import com.simplefanc.voj.service.msg.AdminNoticeService;

import javax.annotation.Resource;

/**
 * @Author: chenfan
 * @Date: 2021/10/1 20:38
 * @Description: 负责管理员发送系统通知
 */
@RestController
@RequestMapping("/api/admin/msg")
public class AdminNoticeController {

    @Resource
    private AdminNoticeService adminNoticeService;

    @GetMapping("/notice")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<IPage<AdminSysNoticeVo>> getSysNotice(@RequestParam(value = "limit", required = false) Integer limit,
                                                              @RequestParam(value = "currentPage", required = false) Integer currentPage,
                                                              @RequestParam(value = "type", required = false) String type) {
        return CommonResult.successResponse(adminNoticeService.getSysNotice(limit, currentPage, type));
    }

    @PostMapping("/notice")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<Void> addSysNotice(@RequestBody AdminSysNotice adminSysNotice) {
        adminNoticeService.addSysNotice(adminSysNotice);
        return CommonResult.successResponse();
    }


    @DeleteMapping("/notice")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<Void> deleteSysNotice(@RequestParam("id") Long id) {
        adminNoticeService.deleteSysNotice(id);
        return CommonResult.successResponse();
    }


    @PutMapping("/notice")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<Void> updateSysNotice(@RequestBody AdminSysNotice adminSysNotice) {
        adminNoticeService.updateSysNotice(adminSysNotice);
        return CommonResult.successResponse();
    }
}