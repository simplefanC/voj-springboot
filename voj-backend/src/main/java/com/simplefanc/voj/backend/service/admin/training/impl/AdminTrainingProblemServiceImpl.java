package com.simplefanc.voj.backend.service.admin.training.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.simplefanc.voj.backend.common.exception.StatusFailException;
import com.simplefanc.voj.backend.dao.problem.ProblemEntityService;
import com.simplefanc.voj.backend.dao.training.TrainingEntityService;
import com.simplefanc.voj.backend.dao.training.TrainingProblemEntityService;
import com.simplefanc.voj.backend.judge.remote.crawler.ProblemCrawler;
import com.simplefanc.voj.backend.pojo.bo.FilePathProps;
import com.simplefanc.voj.backend.pojo.dto.TrainingProblemDto;
import com.simplefanc.voj.backend.pojo.vo.UserRolesVo;
import com.simplefanc.voj.backend.service.admin.problem.RemoteProblemService;
import com.simplefanc.voj.backend.service.admin.training.AdminTrainingProblemService;
import com.simplefanc.voj.backend.service.admin.training.AdminTrainingRecordService;
import com.simplefanc.voj.backend.shiro.UserSessionUtil;
import com.simplefanc.voj.common.pojo.entity.problem.Problem;
import com.simplefanc.voj.common.pojo.entity.training.Training;
import com.simplefanc.voj.common.pojo.entity.training.TrainingProblem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author: chenfan
 * @Date: 2022/3/9 20:20
 * @Description:
 */
@Service
@RequiredArgsConstructor
public class AdminTrainingProblemServiceImpl implements AdminTrainingProblemService {

    private final TrainingProblemEntityService trainingProblemEntityService;

    private final TrainingEntityService trainingEntityService;

    private final ProblemEntityService problemEntityService;

    private final AdminTrainingRecordService adminTrainingRecordService;

    private final RemoteProblemService remoteProblemService;

    private final FilePathProps filePathProps;

    @Override
    public HashMap<String, Object> getProblemList(Integer limit, Integer currentPage, String keyword,
                                                  Boolean queryExisted, Long tid) {
        if (currentPage == null || currentPage < 1)
            currentPage = 1;
        if (limit == null || limit < 1)
            limit = 10;

        IPage<Problem> iPage = new Page<>(currentPage, limit);
        // ??????tid???TrainingProblem?????????????????????pid??????
        QueryWrapper<TrainingProblem> trainingProblemQueryWrapper = new QueryWrapper<>();
        trainingProblemQueryWrapper.eq("tid", tid).orderByAsc("display_id");
        List<Long> pidList = new LinkedList<>();
        List<TrainingProblem> trainingProblemList = trainingProblemEntityService.list(trainingProblemQueryWrapper);
        HashMap<Long, TrainingProblem> trainingProblemMap = new HashMap<>();
        trainingProblemList.forEach(trainingProblem -> {
            if (!trainingProblemMap.containsKey(trainingProblem.getPid())) {
                trainingProblemMap.put(trainingProblem.getPid(), trainingProblem);
            }
            pidList.add(trainingProblem.getPid());
        });
        // TODO put ???
        HashMap<String, Object> trainingProblem = new HashMap<>();
        // ?????????????????????????????????
        if (pidList.size() == 0 && queryExisted) {
            trainingProblem.put("problemList", pidList);
            trainingProblem.put("contestProblemMap", trainingProblemMap);
            return trainingProblem;
        }

        QueryWrapper<Problem> problemQueryWrapper = new QueryWrapper<>();

        // ???????????????????????????????????????????????????in??????????????????????????????????????????not in
        if (queryExisted) {
            problemQueryWrapper.in(pidList.size() > 0, "id", pidList);
        } else {
            // ??????????????????????????????????????????????????????????????????
            problemQueryWrapper.eq("auth", 1);
            problemQueryWrapper.notIn(pidList.size() > 0, "id", pidList);
        }

        if (!StrUtil.isEmpty(keyword)) {
            problemQueryWrapper.and(wrapper -> wrapper.like("title", keyword).or().like("problem_id", keyword).or()
                    .like("author", keyword));
        }

        IPage<Problem> problemListPager = problemEntityService.page(iPage, problemQueryWrapper);

        if (queryExisted) {
            List<Problem> problemListPagerRecords = problemListPager.getRecords();
            List<Problem> sortProblemList = problemListPagerRecords.stream()
                    .sorted(Comparator.comparingInt(problem -> trainingProblemMap.get(problem.getId()).getRank()))
                    .collect(Collectors.toList());
            problemListPager.setRecords(sortProblemList);
        }
        trainingProblem.put("problemList", problemListPager);
        trainingProblem.put("trainingProblemMap", trainingProblemMap);
        return trainingProblem;
    }

    @Override
    public void updateProblem(TrainingProblem trainingProblem) {
        boolean isOk = trainingProblemEntityService.saveOrUpdate(trainingProblem);

        if (!isOk) {
            throw new StatusFailException("???????????????");
        }
    }

    @Override
    public void deleteProblem(Long pid, Long tid) {
        boolean isOk;
        // ??????id??????null??????????????????????????????????????????
        if (tid != null) {
            QueryWrapper<TrainingProblem> trainingProblemQueryWrapper = new QueryWrapper<>();
            trainingProblemQueryWrapper.eq("tid", tid).eq("pid", pid);
            isOk = trainingProblemEntityService.remove(trainingProblemQueryWrapper);
        } else {
            // problem???id?????????????????????????????????????????????????????????????????????
            isOk = problemEntityService.removeById(pid);
        }

        if (isOk) {
            if (tid == null) {
                FileUtil.del(filePathProps.getTestcaseBaseFolder() + File.separator + "problem_" + pid);
            }

            // ??????????????????????????????
            UpdateWrapper<Training> trainingUpdateWrapper = new UpdateWrapper<>();
            trainingUpdateWrapper.set("gmt_modified", new Date()).eq("id", tid);
            trainingEntityService.update(trainingUpdateWrapper);

        } else {
            throw new StatusFailException("???????????????");
        }
    }

    @Override
    public void addProblemFromPublic(TrainingProblemDto trainingProblemDto) {

        Long pid = trainingProblemDto.getPid();
        Long tid = trainingProblemDto.getTid();
        String displayId = trainingProblemDto.getDisplayId();

        QueryWrapper<TrainingProblem> trainingProblemQueryWrapper = new QueryWrapper<>();
        trainingProblemQueryWrapper.eq("tid", tid)
                .and(wrapper -> wrapper.eq("pid", pid).or().eq("display_id", displayId));
        TrainingProblem trainingProblem = trainingProblemEntityService.getOne(trainingProblemQueryWrapper, false);
        if (trainingProblem != null) {
            throw new StatusFailException("????????????????????????????????????????????????????????????ID????????????");
        }

        TrainingProblem newTProblem = new TrainingProblem();
        boolean isOk = trainingProblemEntityService
                .saveOrUpdate(newTProblem.setTid(tid).setPid(pid).setDisplayId(displayId));
        if (isOk) {
            // ??????????????????????????????
            UpdateWrapper<Training> trainingUpdateWrapper = new UpdateWrapper<>();
            trainingUpdateWrapper.set("gmt_modified", new Date()).eq("id", tid);
            trainingEntityService.update(trainingUpdateWrapper);

            // ????????????????????????????????????????????????
            adminTrainingRecordService.syncAlreadyRegisterUserRecord(tid, pid, newTProblem.getId());
        } else {
            throw new StatusFailException("???????????????");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importTrainingRemoteOJProblem(String name, String problemId, Long tid) {
        QueryWrapper<Problem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("problem_id", name.toUpperCase() + "-" + problemId);
        Problem problem = problemEntityService.getOne(queryWrapper, false);

        // ??????????????????????????????????????????
        if (problem == null) {
            UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();
            try {
                ProblemCrawler.RemoteProblemInfo otherOJProblemInfo = remoteProblemService
                        .getOtherOJProblemInfo(name.toUpperCase(), problemId, userRolesVo.getUsername());
                if (otherOJProblemInfo != null) {
                    problem = remoteProblemService.adminAddOtherOJProblem(otherOJProblemInfo, name);
                    if (problem == null) {
                        throw new StatusFailException("??????????????????????????????????????????");
                    }
                } else {
                    throw new StatusFailException("????????????????????????????????????????????????OJ????????????????????????????????????");
                }
            } catch (Exception e) {
                throw new StatusFailException(e.getMessage());
            }
        }

        QueryWrapper<TrainingProblem> trainingProblemQueryWrapper = new QueryWrapper<>();
        Problem finalProblem = problem;
        trainingProblemQueryWrapper.eq("tid", tid).and(
                wrapper -> wrapper.eq("pid", finalProblem.getId()).or().eq("display_id", finalProblem.getProblemId()));
        TrainingProblem trainingProblem = trainingProblemEntityService.getOne(trainingProblemQueryWrapper, false);
        if (trainingProblem != null) {
            throw new StatusFailException("????????????????????????????????????????????????????????????ID????????????");
        }

        TrainingProblem newTProblem = new TrainingProblem();
        boolean isOk = trainingProblemEntityService
                .saveOrUpdate(newTProblem.setTid(tid).setPid(problem.getId()).setDisplayId(problem.getProblemId()));
        // ????????????
        if (isOk) {
            // ??????????????????????????????
            UpdateWrapper<Training> trainingUpdateWrapper = new UpdateWrapper<>();
            trainingUpdateWrapper.set("gmt_modified", new Date()).eq("id", tid);
            trainingEntityService.update(trainingUpdateWrapper);

            // ????????????????????????????????????????????????
            adminTrainingRecordService.syncAlreadyRegisterUserRecord(tid, problem.getId(), newTProblem.getId());
        } else {
            throw new StatusFailException("???????????????");
        }
    }

}