package com.simplefanc.voj.backend.dao.problem.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.simplefanc.voj.backend.dao.problem.ProblemCaseEntityService;
import com.simplefanc.voj.backend.mapper.ProblemCaseMapper;
import com.simplefanc.voj.common.pojo.entity.problem.ProblemCase;
import org.springframework.stereotype.Service;

/**
 * @Author: chenfan
 * @Date: 2021/12/14 19:59
 * @Description:
 */
@Service
public class ProblemCaseEntityServiceImpl extends ServiceImpl<ProblemCaseMapper, ProblemCase>
        implements ProblemCaseEntityService {

}