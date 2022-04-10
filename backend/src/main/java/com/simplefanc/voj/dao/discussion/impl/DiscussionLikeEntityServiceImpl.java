package com.simplefanc.voj.dao.discussion.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import com.simplefanc.voj.dao.discussion.DiscussionLikeEntityService;
import com.simplefanc.voj.mapper.DiscussionLikeMapper;
import com.simplefanc.voj.pojo.entity.discussion.DiscussionLike;

/**
 * @Author: chenfan
 * @Date: 2021/5/4 22:31
 * @Description:
 */
@Service
public class DiscussionLikeEntityServiceImpl extends ServiceImpl<DiscussionLikeMapper, DiscussionLike> implements DiscussionLikeEntityService {
}