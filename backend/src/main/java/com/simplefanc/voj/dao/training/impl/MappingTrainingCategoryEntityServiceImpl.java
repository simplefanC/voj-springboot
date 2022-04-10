package com.simplefanc.voj.dao.training.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import com.simplefanc.voj.dao.training.MappingTrainingCategoryEntityService;
import com.simplefanc.voj.mapper.MappingTrainingCategoryMapper;
import com.simplefanc.voj.pojo.entity.training.MappingTrainingCategory;

/**
 * @Author: chenfan
 * @Date: 2022/3/9 19:53
 * @Description:
 */
@Service
public class MappingTrainingCategoryEntityServiceImpl extends ServiceImpl<MappingTrainingCategoryMapper, MappingTrainingCategory> implements MappingTrainingCategoryEntityService {
}