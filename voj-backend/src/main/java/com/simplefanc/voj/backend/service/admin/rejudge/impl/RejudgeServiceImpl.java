package com.simplefanc.voj.backend.service.admin.rejudge.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.simplefanc.voj.backend.common.exception.StatusFailException;
import com.simplefanc.voj.backend.dao.contest.ContestRecordEntityService;
import com.simplefanc.voj.backend.dao.judge.JudgeCaseEntityService;
import com.simplefanc.voj.backend.dao.judge.JudgeEntityService;
import com.simplefanc.voj.backend.dao.problem.ProblemEntityService;
import com.simplefanc.voj.backend.dao.user.UserAcproblemEntityService;
import com.simplefanc.voj.backend.judge.local.JudgeDispatcher;
import com.simplefanc.voj.backend.judge.remote.RemoteJudgeDispatcher;
import com.simplefanc.voj.backend.service.admin.rejudge.RejudgeService;
import com.simplefanc.voj.common.constants.JudgeStatus;
import com.simplefanc.voj.common.pojo.entity.contest.ContestRecord;
import com.simplefanc.voj.common.pojo.entity.judge.Judge;
import com.simplefanc.voj.common.pojo.entity.judge.JudgeCase;
import com.simplefanc.voj.common.pojo.entity.problem.Problem;
import com.simplefanc.voj.common.pojo.entity.user.UserAcproblem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * @Author: chenfan
 * @Date: 2022/3/9 16:21
 * @Description:
 */
@Service
@RequiredArgsConstructor
public class RejudgeServiceImpl implements RejudgeService {

    private final JudgeEntityService judgeEntityService;

    private final UserAcproblemEntityService userAcproblemEntityService;

    private final ContestRecordEntityService contestRecordEntityService;

    private final JudgeCaseEntityService judgeCaseEntityService;

    private final ProblemEntityService problemEntityService;

    private final JudgeDispatcher judgeDispatcher;

    private final RemoteJudgeDispatcher remoteJudgeDispatcher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Judge rejudge(Long submitId) {
        Judge judge = judgeEntityService.getById(submitId);

        boolean isContestSubmission = judge.getCid() != 0;
        boolean resetContestRecordResult = true;

        // ????????????????????????
        if (!isContestSubmission) {
            // ?????????????????????????????????????????????????????????
            // ?????????????????????AC???????????????????????????????????????ac????????? user_acproblem
            if (judge.getStatus().intValue() == JudgeStatus.STATUS_ACCEPTED.getStatus().intValue()) {
                QueryWrapper<UserAcproblem> userAcproblemQueryWrapper = new QueryWrapper<>();
                userAcproblemQueryWrapper.eq("submit_id", judge.getSubmitId());
                userAcproblemEntityService.remove(userAcproblemQueryWrapper);
            }
        } else {
            // ???????????????????????????????????????
            UpdateWrapper<ContestRecord> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("submit_id", submitId).setSql("status=null,score=null");
            resetContestRecordResult = contestRecordEntityService.update(updateWrapper);
        }

        // ???????????????????????????????????????
        QueryWrapper<JudgeCase> judgeCaseQueryWrapper = new QueryWrapper<>();
        judgeCaseQueryWrapper.eq("submit_id", submitId);
        judgeCaseEntityService.remove(judgeCaseQueryWrapper);

        // ???????????????
        // ????????????????????????
        judge.setStatus(JudgeStatus.STATUS_PENDING.getStatus());
        judge.setVersion(judge.getVersion() + 1);
        judge.setJudger("").setTime(null).setMemory(null).setErrorMessage(null).setOiRankScore(null).setScore(null);

        boolean result = judgeEntityService.updateById(judge);

        if (result && resetContestRecordResult) {
            // ??????????????????
            Problem problem = problemEntityService.getById(judge.getPid());
            // ???????????????oj??????
            if (problem.getIsRemote()) {
                remoteJudgeDispatcher.sendTask(judge, problem.getProblemId(), isContestSubmission);
            } else {
                judgeDispatcher.sendTask(judge, isContestSubmission);
            }
            return judge;
        } else {
            throw new StatusFailException("?????????????????????????????????");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejudgeContestProblem(Long cid, Long pid) {
        QueryWrapper<Judge> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("cid", cid).eq("pid", pid);
        List<Judge> rejudgeList = judgeEntityService.list(queryWrapper);

        if (rejudgeList.size() == 0) {
            throw new StatusFailException("??????????????????????????????????????????");
        }

        List<Long> submitIdList = new LinkedList<>();
        HashMap<Long, Integer> idMapStatus = new HashMap<>();
        // ?????????????????????
        for (Judge judge : rejudgeList) {
            idMapStatus.put(judge.getSubmitId(), judge.getStatus());
            // ????????????????????????
            judge.setStatus(JudgeStatus.STATUS_PENDING.getStatus());
            judge.setVersion(judge.getVersion() + 1);
            judge.setJudger("").setTime(null).setMemory(null).setErrorMessage(null).setOiRankScore(null).setScore(null);
            submitIdList.add(judge.getSubmitId());
        }
        boolean resetJudgeResult = judgeEntityService.updateBatchById(rejudgeList);
        // ??????????????????????????????????????????
        QueryWrapper<JudgeCase> judgeCaseQueryWrapper = new QueryWrapper<>();
        judgeCaseQueryWrapper.in("submit_id", submitIdList);
        judgeCaseEntityService.remove(judgeCaseQueryWrapper);
        // ???????????????????????????????????????
        UpdateWrapper<ContestRecord> updateWrapper = new UpdateWrapper<>();
        updateWrapper.in("submit_id", submitIdList).setSql("status=null,score=null");
        boolean resetContestRecordResult = contestRecordEntityService.update(updateWrapper);

        if (resetContestRecordResult && resetJudgeResult) {
            // ??????????????????
            Problem problem = problemEntityService.getById(pid);
            // ???????????????oj??????
            if (problem.getIsRemote()) {
                for (Judge judge : rejudgeList) {
                    // ?????????????????????????????????????????????
                    remoteJudgeDispatcher.sendTask(judge, problem.getProblemId(), judge.getCid() != 0);
                }
            } else {
                for (Judge judge : rejudgeList) {
                    // ?????????????????????????????????????????????
                    judgeDispatcher.sendTask(judge, judge.getCid() != 0);
                }
            }
        } else {
            throw new StatusFailException("?????????????????????????????????");
        }
    }

}