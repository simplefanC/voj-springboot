package com.simplefanc.voj.judge;

import com.simplefanc.voj.common.exception.SystemError;
import com.simplefanc.voj.dao.ContestRecordEntityService;
import com.simplefanc.voj.dao.UserAcproblemEntityService;
import com.simplefanc.voj.pojo.entity.judge.Judge;
import com.simplefanc.voj.pojo.entity.problem.Problem;
import com.simplefanc.voj.pojo.entity.user.UserAcproblem;
import com.simplefanc.voj.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;

/**
 * @Author: chenfan
 * @Date: 2022/3/12 15:49
 * @Description:
 */
@Component
public class JudgeContext {

    @Autowired
    private JudgeStrategy judgeStrategy;

    @Autowired
    private UserAcproblemEntityService userAcproblemEntityService;

    @Autowired
    private ContestRecordEntityService contestRecordEntityService;

    public Judge Judge(Problem problem, Judge judge) {

        // c和c++为一倍时间和空间，其它语言为2倍时间和空间
        if (!judge.getLanguage().equals("C++") && !judge.getLanguage().equals("C") &&
                !judge.getLanguage().equals("C++ With O2") && !judge.getLanguage().equals("C With O2")) {
            problem.setTimeLimit(problem.getTimeLimit() * 2);
            problem.setMemoryLimit(problem.getMemoryLimit() * 2);
        }

        HashMap<String, Object> judgeResult = judgeStrategy.judge(problem, judge);

        // 如果是编译失败、提交错误或者系统错误就有错误提醒
        if (judgeResult.get("code") == Constants.Judge.STATUS_COMPILE_ERROR.getStatus() ||
                judgeResult.get("code") == Constants.Judge.STATUS_SYSTEM_ERROR.getStatus() ||
                judgeResult.get("code") == Constants.Judge.STATUS_RUNTIME_ERROR.getStatus() ||
                judgeResult.get("code") == Constants.Judge.STATUS_SUBMITTED_FAILED.getStatus()) {
            judge.setErrorMessage((String) judgeResult.getOrDefault("errMsg", ""));
        }
        // 设置最终结果状态码
        judge.setStatus((Integer) judgeResult.get("code"));
        // 设置最大时间和最大空间不超过题目限制时间和空间
        // kb
        Integer memory = (Integer) judgeResult.get("memory");
        judge.setMemory(Math.min(memory, problem.getMemoryLimit() * 1024));
        // ms
        Integer time = (Integer) judgeResult.get("time");
        judge.setTime(Math.min(time, problem.getTimeLimit()));
        // score
        judge.setScore((Integer) judgeResult.getOrDefault("score", null));
        // oi_rank_score
        judge.setOiRankScore((Integer) judgeResult.getOrDefault("oiRankScore", null));

        return judge;
    }

    public Boolean compileSpj(String code, Long pid, String spjLanguage, HashMap<String, String> extraFiles) throws SystemError {
        return Compiler.compileSpj(code, pid, spjLanguage, extraFiles);
    }

    public Boolean compileInteractive(String code, Long pid, String interactiveLanguage, HashMap<String, String> extraFiles) throws SystemError {
        return Compiler.compileInteractive(code, pid, interactiveLanguage, extraFiles);
    }


    public void updateOtherTable(Long submitId,
                                 Integer status,
                                 Long cid,
                                 String uid,
                                 Long pid,
                                 Integer score,
                                 Integer useTime) {

        // 非比赛提交
        if (cid == 0) {
            // 如果是AC，就更新user_acproblem表,
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