package com.simplefanc.voj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;
import com.simplefanc.voj.pojo.entity.problem.CodeTemplate;


@Mapper
@Repository
public interface CodeTemplateMapper extends BaseMapper<CodeTemplate> {
}

