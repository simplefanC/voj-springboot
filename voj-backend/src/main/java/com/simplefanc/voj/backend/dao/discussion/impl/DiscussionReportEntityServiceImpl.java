package com.simplefanc.voj.backend.dao.discussion.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.simplefanc.voj.backend.dao.discussion.DiscussionReportEntityService;
import com.simplefanc.voj.backend.mapper.DiscussionReportMapper;
import com.simplefanc.voj.common.pojo.entity.discussion.DiscussionReport;
import org.springframework.stereotype.Service;

/**
 * @Author: chenfan
 * @Date: 2021/5/11 21:46
 * @Description:
 */
@Service
public class DiscussionReportEntityServiceImpl extends ServiceImpl<DiscussionReportMapper, DiscussionReport>
        implements DiscussionReportEntityService {

}