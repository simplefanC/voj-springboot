package com.simplefanc.voj.backend.dao.judge.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.simplefanc.voj.backend.dao.judge.JudgeCaseEntityService;
import com.simplefanc.voj.backend.mapper.JudgeCaseMapper;
import com.simplefanc.voj.common.pojo.entity.judge.JudgeCase;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @Author: chenfan
 * @since 2021-10-23
 */
@Service
public class JudgeCaseEntityServiceImpl extends ServiceImpl<JudgeCaseMapper, JudgeCase>
        implements JudgeCaseEntityService {

}
