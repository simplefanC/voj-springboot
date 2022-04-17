package com.simplefanc.voj.service.impl;

import com.simplefanc.voj.common.exception.SystemError;
import com.simplefanc.voj.dao.ContestRecordEntityService;
import com.simplefanc.voj.dao.JudgeEntityService;
import com.simplefanc.voj.dao.ProblemEntityService;
import com.simplefanc.voj.dao.UserAcproblemEntityService;
import com.simplefanc.voj.judge.JudgeContext;
import com.simplefanc.voj.pojo.dto.ToJudge;
import com.simplefanc.voj.pojo.entity.judge.Judge;
import com.simplefanc.voj.pojo.entity.problem.Problem;
import com.simplefanc.voj.pojo.entity.user.UserAcproblem;
import com.simplefanc.voj.remoteJudge.RemoteJudgeContext;
import com.simplefanc.voj.service.JudgeService;
import com.simplefanc.voj.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @Author: chenfan
 * @Date: 2022/3/12 15:54
 * @Description:
 */
@Service
@RefreshScope
public class JudgeServiceImpl implements JudgeService {

    @Value("${voj-judge-server.name}")
    private String judgeServerName;

    @Resource
    private JudgeEntityService judgeEntityService;

    @Resource
    private ProblemEntityService problemEntityService;

    @Resource
    private JudgeContext judgeContext;

    @Autowired
    private RemoteJudgeContext remoteJudgeContext;

    @Autowired
    private UserAcproblemEntityService userAcproblemEntityService;

    @Autowired
    private ContestRecordEntityService contestRecordEntityService;

    @Override
    public void judge(Judge judge) {
        // 标志该判题过程进入编译阶段
        judge.setStatus(Constants.Judge.STATUS_COMPILING.getStatus());
        // 写入当前判题服务的名字
        judge.setJudger(judgeServerName);
        judgeEntityService.updateById(judge);
        // 进行判题操作
        Problem problem = problemEntityService.getById(judge.getPid());
        Judge finalJudge = judgeContext.Judge(problem, judge);

        // 更新该次提交
        judgeEntityService.updateById(finalJudge);

        if (finalJudge.getStatus().intValue() != Constants.Judge.STATUS_SUBMITTED_FAILED.getStatus()) {
            // 更新其它表
            judgeContext.updateOtherTable(finalJudge.getSubmitId(),
                    finalJudge.getStatus(),
                    finalJudge.getCid(),
                    finalJudge.getUid(),
                    finalJudge.getPid(),
                    finalJudge.getScore(),
                    finalJudge.getTime());
        }
    }

    @Override
    public void remoteJudge(ToJudge toJudge) {
        remoteJudgeContext.judge(toJudge);
    }

    @Override
    public Boolean compileSpj(String code, Long pid, String spjLanguage, HashMap<String, String> extraFiles) throws SystemError {
        return judgeContext.compileSpj(code, pid, spjLanguage, extraFiles);
    }

    @Override
    public Boolean compileInteractive(String code, Long pid, String interactiveLanguage, HashMap<String, String> extraFiles) throws SystemError {
        return judgeContext.compileInteractive(code, pid, interactiveLanguage, extraFiles);
    }

    @Override
    public void updateOtherTable(Long submitId,
                                 Integer status,
                                 Long cid,
                                 String uid,
                                 Long pid,
                                 Integer score,
                                 Integer useTime) {

        // 非比赛提交
        if (cid == 0) {
            // 如果是AC,就更新user_acproblem表,
            if (status.intValue() == Constants.Judge.STATUS_ACCEPTED.getStatus()) {
                userAcproblemEntityService.saveOrUpdate(new UserAcproblem()
                        .setPid(pid)
                        .setUid(uid)
                        .setSubmitId(submitId)
                );
            }

        } else {
            // 如果是比赛提交
            contestRecordEntityService.UpdateContestRecord(uid, score, status, submitId, cid, useTime);
        }
    }
}