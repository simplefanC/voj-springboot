package com.simplefanc.voj.backend.dao.discussion.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.simplefanc.voj.backend.dao.discussion.CommentLikeEntityService;
import com.simplefanc.voj.backend.mapper.CommentLikeMapper;
import com.simplefanc.voj.common.pojo.entity.discussion.CommentLike;
import org.springframework.stereotype.Service;

/**
 * @Author: chenfan
 * @Date: 2021/5/4 22:31
 * @Description:
 */
@Service
public class CommentLikeEntityServiceImpl extends ServiceImpl<CommentLikeMapper, CommentLike>
        implements CommentLikeEntityService {

}