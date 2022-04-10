package com.simplefanc.voj.dao.msg;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.simplefanc.voj.pojo.entity.msg.UserSysNotice;
import com.simplefanc.voj.pojo.vo.SysMsgVo;

public interface UserSysNoticeEntityService extends IService<UserSysNotice> {

    IPage<SysMsgVo> getSysNotice(int limit, int currentPage, String uid);

    IPage<SysMsgVo> getMineNotice(int limit, int currentPage, String uid);
}