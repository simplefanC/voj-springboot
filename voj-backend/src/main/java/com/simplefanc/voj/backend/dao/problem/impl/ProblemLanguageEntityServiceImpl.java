package com.simplefanc.voj.backend.dao.problem.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.simplefanc.voj.backend.dao.problem.ProblemLanguageEntityService;
import com.simplefanc.voj.backend.mapper.ProblemLanguageMapper;
import com.simplefanc.voj.common.pojo.entity.problem.ProblemLanguage;
import org.springframework.stereotype.Service;

/**
 * @Author: chenfan
 * @Date: 2021/12/13 00:04
 * @Description:
 */
@Service
public class ProblemLanguageEntityServiceImpl extends ServiceImpl<ProblemLanguageMapper, ProblemLanguage>
        implements ProblemLanguageEntityService {

}