package com.simplefanc.voj.backend.service.admin.problem.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.simplefanc.voj.backend.common.constants.CallJudgerType;
import com.simplefanc.voj.backend.common.exception.StatusFailException;
import com.simplefanc.voj.backend.common.exception.StatusForbiddenException;
import com.simplefanc.voj.backend.dao.judge.JudgeEntityService;
import com.simplefanc.voj.backend.dao.problem.ProblemCaseEntityService;
import com.simplefanc.voj.backend.dao.problem.ProblemEntityService;
import com.simplefanc.voj.backend.judge.Dispatcher;
import com.simplefanc.voj.backend.judge.remote.crawler.ProblemCrawler;
import com.simplefanc.voj.backend.pojo.bo.FilePathProps;
import com.simplefanc.voj.backend.pojo.dto.ProblemDto;
import com.simplefanc.voj.backend.pojo.vo.UserRolesVo;
import com.simplefanc.voj.backend.service.admin.problem.AdminProblemService;
import com.simplefanc.voj.backend.service.admin.problem.RemoteProblemService;
import com.simplefanc.voj.backend.shiro.UserSessionUtil;
import com.simplefanc.voj.common.constants.ProblemEnum;
import com.simplefanc.voj.common.constants.RemoteOj;
import com.simplefanc.voj.common.pojo.dto.CompileDTO;
import com.simplefanc.voj.common.pojo.entity.judge.Judge;
import com.simplefanc.voj.common.pojo.entity.problem.Problem;
import com.simplefanc.voj.common.pojo.entity.problem.ProblemCase;
import com.simplefanc.voj.common.result.CommonResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;

/**
 * @Author: chenfan
 * @Date: 2022/3/9 16:32
 * @Description:
 */

@Service
@RefreshScope
@RequiredArgsConstructor
public class AdminProblemServiceImpl implements AdminProblemService {

    private final ProblemEntityService problemEntityService;

    private final ProblemCaseEntityService problemCaseEntityService;

    private final Dispatcher dispatcher;

    @Value("${voj.judge.token}")
    private String judgeToken;

    private final JudgeEntityService judgeEntityService;

    private final RemoteProblemService remoteProblemService;

    private final FilePathProps filePathProps;

    @Override
    public IPage<Problem> getProblemList(Integer limit, Integer currentPage, String keyword, Integer auth, String oj) {
        if (currentPage == null || currentPage < 1)
            currentPage = 1;
        if (limit == null || limit < 1)
            limit = 10;
        IPage<Problem> iPage = new Page<>(currentPage, limit);
        IPage<Problem> problemList;

        QueryWrapper<Problem> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("gmt_create").orderByDesc("id");

        // ??????oj????????????
        if (oj != null && !"All".equals(oj)) {
            if (!RemoteOj.isRemoteOJ(oj)) {
                queryWrapper.eq("is_remote", false);
            } else {
                queryWrapper.eq("is_remote", true).likeRight("problem_id", oj);
            }
        }

        if (auth != null && auth != 0) {
            queryWrapper.eq("auth", auth);
        }

        if (!StrUtil.isEmpty(keyword)) {
            final String key = keyword.trim();
            queryWrapper
                    .and(wrapper -> wrapper.like("title", key).or().like("author", key).or().like("problem_id", key));
        }
        problemList = problemEntityService.page(iPage, queryWrapper);
        return problemList;
    }

    @Override
    public Problem getProblem(Long pid) {
        Problem problem = problemEntityService.getById(pid);

        // ????????????
        if (problem != null) {
            // ???????????????????????????
            UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();

            boolean isRoot = UserSessionUtil.isRoot();
            boolean isProblemAdmin = UserSessionUtil.isProblemAdmin();
            // ?????????????????????????????????????????????????????????????????????
            if (!isRoot && !isProblemAdmin && !userRolesVo.getUsername().equals(problem.getAuthor())) {
                throw new StatusForbiddenException("???????????????????????????????????????");
            }

            return problem;
        } else {
            throw new StatusFailException("???????????????");
        }
    }

    @Override
    public void deleteProblem(Long pid) {
        boolean isOk = problemEntityService.removeById(pid);
        // problem???id?????????????????????????????????????????????????????????????????????
        // ????????????
        if (isOk) {
            FileUtil.del(filePathProps.getTestcaseBaseFolder() + File.separator + "problem_" + pid);
        } else {
            throw new StatusFailException("???????????????");
        }
    }

    @Override
    public void addProblem(ProblemDto problemDto) {
        QueryWrapper<Problem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("problem_id", problemDto.getProblem().getProblemId().toUpperCase());
        Problem problem = problemEntityService.getOne(queryWrapper);
        if (problem != null) {
            throw new StatusFailException("????????????Problem ID ????????????????????????");
        }

        boolean isOk = problemEntityService.adminAddProblem(problemDto);
        if (!isOk) {
            throw new StatusFailException("????????????");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProblem(ProblemDto problemDto) {
        // ???????????????????????????
        UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();

        boolean isRoot = UserSessionUtil.isRoot();
        boolean isProblemAdmin = UserSessionUtil.isProblemAdmin();
        // ?????????????????????????????????????????????????????????????????????
        if (!isRoot && !isProblemAdmin && !userRolesVo.getUsername().equals(problemDto.getProblem().getAuthor())) {
            throw new StatusForbiddenException("???????????????????????????????????????");
        }

        String problemId = problemDto.getProblem().getProblemId().toUpperCase();
        QueryWrapper<Problem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("problem_id", problemId);
        Problem problem = problemEntityService.getOne(queryWrapper);

        // ??????problem_id??????????????????????????????problem_id?????????????????????
        if (problem != null && problem.getId().longValue() != problemDto.getProblem().getId()) {
            throw new StatusFailException("?????????Problem ID ???????????????????????????");
        }

        // ???????????????????????????
        problemDto.getProblem().setModifiedUser(userRolesVo.getUsername());

        boolean result = problemEntityService.adminUpdateProblem(problemDto);
        // ????????????
        if (result) {
            // ????????????problemId???????????????judge???
            if (problem == null) {
                UpdateWrapper<Judge> judgeUpdateWrapper = new UpdateWrapper<>();
                judgeUpdateWrapper.eq("pid", problemDto.getProblem().getId()).set("display_pid", problemId);
                judgeEntityService.update(judgeUpdateWrapper);
            }

        } else {
            throw new StatusFailException("????????????");
        }
    }

    @Override
    public List<ProblemCase> getProblemCases(Long pid, Boolean isUpload) {
        QueryWrapper<ProblemCase> problemCaseQueryWrapper = new QueryWrapper<>();
        problemCaseQueryWrapper.eq("pid", pid).eq("status", 0);
        if (isUpload) {
            problemCaseQueryWrapper.last("order by length(input) asc,input asc");
        }
        return problemCaseEntityService.list(problemCaseQueryWrapper);
    }

    @Override
    public CommonResult compileSpj(CompileDTO compileDTO) {
        compileDTO.setToken(judgeToken);
        return dispatcher.dispatcher(CallJudgerType.COMPILE, "/compile-spj", compileDTO);
    }

    @Override
    public CommonResult compileInteractive(CompileDTO compileDTO) {
        compileDTO.setToken(judgeToken);
        return dispatcher.dispatcher(CallJudgerType.COMPILE, "/compile-interactive", compileDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importRemoteOJProblem(String name, String problemId) {
        QueryWrapper<Problem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("problem_id", name.toUpperCase() + "-" + problemId);
        Problem problem = problemEntityService.getOne(queryWrapper);
        if (problem != null) {
            throw new StatusFailException("??????????????????????????????????????????");
        }

        UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();
        try {
            ProblemCrawler.RemoteProblemInfo otherOJProblemInfo = remoteProblemService
                    .getOtherOJProblemInfo(name.toUpperCase(), problemId, userRolesVo.getUsername());
            if (otherOJProblemInfo != null) {
                Problem importProblem = remoteProblemService.adminAddOtherOJProblem(otherOJProblemInfo, name);
                if (importProblem == null) {
                    throw new StatusFailException("??????????????????????????????????????????");
                }
            } else {
                throw new StatusFailException("????????????????????????????????????????????????OJ????????????????????????????????????");
            }
        } catch (Exception e) {
            throw new StatusFailException(e.getMessage());
        }
    }

    @Override
    public void changeProblemAuth(Problem problem) {
        // ???????????????????????????????????????????????????????????????
        boolean root = UserSessionUtil.isRoot();

        boolean problemAdmin = UserSessionUtil.isProblemAdmin();

        if (!problemAdmin && !root && problem.getAuth().equals(ProblemEnum.AUTH_PUBLIC.getCode())) {
            throw new StatusForbiddenException("??????????????????????????????????????????");
        }

        UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();

        UpdateWrapper<Problem> problemUpdateWrapper = new UpdateWrapper<>();
        problemUpdateWrapper.eq("id", problem.getId()).set("auth", problem.getAuth()).set("modified_user",
                userRolesVo.getUsername());

        boolean isOk = problemEntityService.update(problemUpdateWrapper);
        if (!isOk) {
            throw new StatusFailException("????????????");
        }
    }

}