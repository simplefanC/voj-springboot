package com.simplefanc.voj.dao.discussion.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import com.simplefanc.voj.dao.discussion.DiscussionReportEntityService;
import com.simplefanc.voj.mapper.DiscussionReportMapper;
import com.simplefanc.voj.pojo.entity.discussion.DiscussionReport;

/**
 * @Author: chenfan
 * @Date: 2021/5/11 21:46
 * @Description:
 */
@Service
public class DiscussionReportEntityServiceImpl extends ServiceImpl<DiscussionReportMapper, DiscussionReport> implements DiscussionReportEntityService {
}