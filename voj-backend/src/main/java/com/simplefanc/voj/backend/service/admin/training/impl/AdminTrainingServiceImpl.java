package com.simplefanc.voj.backend.service.admin.training.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.simplefanc.voj.backend.common.constants.TrainingEnum;
import com.simplefanc.voj.backend.common.exception.StatusFailException;
import com.simplefanc.voj.backend.common.exception.StatusForbiddenException;
import com.simplefanc.voj.backend.dao.training.MappingTrainingCategoryEntityService;
import com.simplefanc.voj.backend.dao.training.TrainingCategoryEntityService;
import com.simplefanc.voj.backend.dao.training.TrainingEntityService;
import com.simplefanc.voj.backend.dao.training.TrainingRegisterEntityService;
import com.simplefanc.voj.backend.pojo.dto.TrainingDto;
import com.simplefanc.voj.backend.pojo.vo.UserRolesVo;
import com.simplefanc.voj.backend.service.admin.training.AdminTrainingRecordService;
import com.simplefanc.voj.backend.service.admin.training.AdminTrainingService;
import com.simplefanc.voj.backend.shiro.UserSessionUtil;
import com.simplefanc.voj.common.pojo.entity.training.MappingTrainingCategory;
import com.simplefanc.voj.common.pojo.entity.training.Training;
import com.simplefanc.voj.common.pojo.entity.training.TrainingCategory;
import com.simplefanc.voj.common.pojo.entity.training.TrainingRegister;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * @Author: chenfan
 * @Date: 2022/3/9 19:46
 * @Description:
 */

@Service
@RequiredArgsConstructor
public class AdminTrainingServiceImpl implements AdminTrainingService {

    private final TrainingEntityService trainingEntityService;

    private final MappingTrainingCategoryEntityService mappingTrainingCategoryEntityService;

    private final TrainingCategoryEntityService trainingCategoryEntityService;

    private final TrainingRegisterEntityService trainingRegisterEntityService;

    private final AdminTrainingRecordService adminTrainingRecordService;

    @Override
    public IPage<Training> getTrainingList(Integer limit, Integer currentPage, String keyword) {

        if (currentPage == null || currentPage < 1)
            currentPage = 1;
        if (limit == null || limit < 1)
            limit = 10;
        IPage<Training> iPage = new Page<>(currentPage, limit);
        QueryWrapper<Training> queryWrapper = new QueryWrapper<>();
        // ????????????
        queryWrapper.select(Training.class, info -> !info.getColumn().equals("private_pwd"));
        if (!StrUtil.isEmpty(keyword)) {
            keyword = keyword.trim();
            queryWrapper.like("title", keyword).or().like("id", keyword).or().like("`rank`", keyword);
        }
        queryWrapper.orderByAsc("`rank`");

        return trainingEntityService.page(iPage, queryWrapper);
    }

    @Override
    public TrainingDto getTraining(Long tid) {
        // ???????????????????????????
        Training training = trainingEntityService.getById(tid);
        // ???????????????
        if (training == null) {
            throw new StatusFailException("?????????????????????????????????,???????????????tid???????????????");
        }

        // ???????????????????????????
        UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();
        // ????????????????????????
        boolean isRoot = UserSessionUtil.isRoot();
        // ???????????????????????????????????????????????????
        if (!isRoot && !userRolesVo.getUsername().equals(training.getAuthor())) {
            throw new StatusForbiddenException("?????????????????????????????????");
        }

        TrainingDto trainingDto = new TrainingDto();
        trainingDto.setTraining(training);

        QueryWrapper<MappingTrainingCategory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("tid", tid);
        MappingTrainingCategory mappingTrainingCategory = mappingTrainingCategoryEntityService.getOne(queryWrapper,
                false);
        TrainingCategory trainingCategory = null;
        if (mappingTrainingCategory != null) {
            trainingCategory = trainingCategoryEntityService.getById(mappingTrainingCategory.getCid());
        }
        trainingDto.setTrainingCategory(trainingCategory);
        return trainingDto;
    }

    @Override
    public void deleteTraining(Long tid) {
        boolean isOk = trainingEntityService.removeById(tid);
        // Training???id?????????????????????????????????????????????????????????????????????
        if (!isOk) {
            throw new StatusFailException("???????????????");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTraining(TrainingDto trainingDto) {

        Training training = trainingDto.getTraining();
        trainingEntityService.save(training);
        TrainingCategory trainingCategory = trainingDto.getTrainingCategory();
        if (trainingCategory.getId() == null) {
            try {
                trainingCategoryEntityService.save(trainingCategory);
            } catch (Exception ignored) {
                QueryWrapper<TrainingCategory> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("name", trainingCategory.getName());
                trainingCategory = trainingCategoryEntityService.getOne(queryWrapper, false);
            }
        }

        boolean isOk = mappingTrainingCategoryEntityService
                .save(new MappingTrainingCategory().setTid(training.getId()).setCid(trainingCategory.getId()));
        if (!isOk) {
            throw new StatusFailException("???????????????");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTraining(TrainingDto trainingDto) {
        // ???????????????????????????
        UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();
        // ????????????????????????
        boolean isRoot = UserSessionUtil.isRoot();
        // ???????????????????????????????????????????????????
        if (!isRoot && !userRolesVo.getUsername().equals(trainingDto.getTraining().getAuthor())) {
            throw new StatusForbiddenException("?????????????????????????????????");
        }
        Training training = trainingDto.getTraining();
        Training oldTraining = trainingEntityService.getById(training.getId());
        trainingEntityService.updateById(training);

        // ???????????? ???????????? ???????????????????????????????????????
        if (training.getAuth().equals(TrainingEnum.AUTH_PRIVATE.getValue())) {
            if (!Objects.equals(training.getPrivatePwd(), oldTraining.getPrivatePwd())) {
                UpdateWrapper<TrainingRegister> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("tid", training.getId());
                trainingRegisterEntityService.remove(updateWrapper);
            }
        }

        TrainingCategory trainingCategory = trainingDto.getTrainingCategory();
        if (trainingCategory.getId() == null) {
            try {
                trainingCategoryEntityService.save(trainingCategory);
            } catch (Exception ignored) {
                QueryWrapper<TrainingCategory> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("name", trainingCategory.getName());
                trainingCategory = trainingCategoryEntityService.getOne(queryWrapper, false);
            }
        }

        MappingTrainingCategory mappingTrainingCategory = mappingTrainingCategoryEntityService
                .getOne(new QueryWrapper<MappingTrainingCategory>().eq("tid", training.getId()), false);

        if (mappingTrainingCategory == null) {
            mappingTrainingCategoryEntityService
                    .save(new MappingTrainingCategory().setTid(training.getId()).setCid(trainingCategory.getId()));
            adminTrainingRecordService.checkSyncRecord(trainingDto.getTraining());
        } else {
            if (!mappingTrainingCategory.getCid().equals(trainingCategory.getId())) {
                UpdateWrapper<MappingTrainingCategory> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("tid", training.getId()).set("cid", trainingCategory.getId());
                boolean isOk = mappingTrainingCategoryEntityService.update(null, updateWrapper);
                if (isOk) {
                    adminTrainingRecordService.checkSyncRecord(trainingDto.getTraining());
                } else {
                    throw new StatusFailException("????????????");
                }
            }
        }

    }

    @Override
    public void changeTrainingStatus(Long tid, String author, Boolean status) {
        // ???????????????????????????
        UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();
        // ????????????????????????
        boolean isRoot = UserSessionUtil.isRoot();
        // ???????????????????????????????????????????????????
        if (!isRoot && !userRolesVo.getUsername().equals(author)) {
            throw new StatusForbiddenException("?????????????????????????????????");
        }

        boolean isOk = trainingEntityService.saveOrUpdate(new Training().setId(tid).setStatus(status));
        if (!isOk) {
            throw new StatusFailException("????????????");
        }
    }

}