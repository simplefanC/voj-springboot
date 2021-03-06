package com.simplefanc.voj.backend.service.admin.training;

import com.simplefanc.voj.common.pojo.entity.training.TrainingCategory;

/**
 * @Author: chenfan
 * @Date: 2022/3/9 19:29
 * @Description:
 */
public interface AdminTrainingCategoryService {

    TrainingCategory addTrainingCategory(TrainingCategory trainingCategory);

    void updateTrainingCategory(TrainingCategory trainingCategory);

    void deleteTrainingCategory(Long cid);

}