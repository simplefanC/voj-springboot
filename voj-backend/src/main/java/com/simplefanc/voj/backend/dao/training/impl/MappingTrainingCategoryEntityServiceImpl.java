package com.simplefanc.voj.backend.dao.training.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.simplefanc.voj.backend.dao.training.MappingTrainingCategoryEntityService;
import com.simplefanc.voj.backend.mapper.MappingTrainingCategoryMapper;
import com.simplefanc.voj.common.pojo.entity.training.MappingTrainingCategory;
import org.springframework.stereotype.Service;

/**
 * @Author: chenfan
 * @Date: 2022/3/9 19:53
 * @Description:
 */
@Service
public class MappingTrainingCategoryEntityServiceImpl
        extends ServiceImpl<MappingTrainingCategoryMapper, MappingTrainingCategory>
        implements MappingTrainingCategoryEntityService {

}