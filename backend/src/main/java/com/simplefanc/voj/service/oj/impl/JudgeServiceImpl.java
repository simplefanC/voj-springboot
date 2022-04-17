package com.simplefanc.voj.service.oj.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.simplefanc.voj.common.exception.StatusAccessDeniedException;
import com.simplefanc.voj.common.exception.StatusFailException;
import com.simplefanc.voj.common.exception.StatusForbiddenException;
import com.simplefanc.voj.common.exception.StatusNotFoundException;
import com.simplefanc.voj.dao.contest.ContestEntityService;
import com.simplefanc.voj.dao.contest.ContestRecordEntityService;
import com.simplefanc.voj.dao.judge.JudgeCaseEntityService;
import com.simplefanc.voj.dao.judge.JudgeEntityService;
import com.simplefanc.voj.dao.problem.ProblemEntityService;
import com.simplefanc.voj.dao.user.UserAcproblemEntityService;
import com.simplefanc.voj.judge.remote.RemoteJudgeDispatcher;
import com.simplefanc.voj.judge.self.JudgeDispatcher;
import com.simplefanc.voj.pojo.dto.SubmitIdListDto;
import com.simplefanc.voj.pojo.dto.ToJudgeDto;
import com.simplefanc.voj.pojo.entity.contest.Contest;
import com.simplefanc.voj.pojo.entity.contest.ContestRecord;
import com.simplefanc.voj.pojo.entity.judge.Judge;
import com.simplefanc.voj.pojo.entity.judge.JudgeCase;
import com.simplefanc.voj.pojo.entity.problem.Problem;
import com.simplefanc.voj.pojo.entity.user.UserAcproblem;
import com.simplefanc.voj.pojo.vo.JudgeVo;
import com.simplefanc.voj.pojo.vo.SubmissionInfoVo;
import com.simplefanc.voj.pojo.vo.UserRolesVo;
import com.simplefanc.voj.service.oj.BeforeDispatchInitService;
import com.simplefanc.voj.service.oj.JudgeService;
import com.simplefanc.voj.utils.Constants;
import com.simplefanc.voj.utils.IpUtils;
import com.simplefanc.voj.utils.RedisUtils;
import com.simplefanc.voj.validator.ContestValidator;
import com.simplefanc.voj.validator.JudgeValidator;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * @Author: chenfan
 * @Date: 2022/3/11 11:12
 * @Description:
 */
@Service
public class JudgeServiceImpl implements JudgeService {
    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private JudgeCaseEntityService judgeCaseEntityService;

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private ContestEntityService contestEntityService;

    @Autowired
    private ContestRecordEntityService contestRecordEntityService;

    @Autowired
    private UserAcproblemEntityService userAcproblemEntityService;

    @Autowired
    private JudgeDispatcher judgeDispatcher;

    @Autowired
    private RemoteJudgeDispatcher remoteJudgeDispatcher;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private JudgeValidator judgeValidator;

    @Autowired
    private ContestValidator contestValidator;

    @Autowired
    private BeforeDispatchInitService beforeDispatchInitService;

    @Value("${voj.web-config.code-visible-start-time}")
    private Long codeVisibleStartTime;

    /**
     * @MethodName submitProblemJudge
     * @Description 核心方法 判题通过openfeign调用判题系统服务
     * @Since 2020/10/30
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Judge submitProblemJudge(ToJudgeDto judgeDto) {

        judgeValidator.validateSubmissionInfo(judgeDto);

        // 需要获取一下该token对应用户的数据
        Session session = SecurityUtils.getSubject().getSession();
        UserRolesVo userRolesVo = (UserRolesVo) session.getAttribute("userInfo");

        boolean isContestSubmission = judgeDto.getCid() != 0;

        boolean isTrainingSubmission = judgeDto.getTid() != null && judgeDto.getTid() != 0;

        // 非比赛提交限制8秒提交一次
        if (!isContestSubmission) {
            String lockKey = Constants.Account.SUBMIT_NON_CONTEST_LOCK.getCode() + userRolesVo.getUid();
            long count = redisUtils.incr(lockKey, 1);
            if (count > 1) {
                throw new StatusForbiddenException("对不起，您的提交频率过快，请稍后再尝试！");
            }
            redisUtils.expire(lockKey, 8);
        }

        HttpServletRequest request = ((ServletRequestAttributes) (RequestContextHolder.currentRequestAttributes())).getRequest();
        // 将提交先写入数据库，准备调用判题服务器
        Judge judge = new Judge();
        // 默认设置代码为单独自己可见
        judge.setShare(false)
                .setCode(judgeDto.getCode())
                .setCid(judgeDto.getCid())
                .setLanguage(judgeDto.getLanguage())
                .setLength(judgeDto.getCode().length())
                .setUid(userRolesVo.getUid())
                .setUsername(userRolesVo.getUsername())
                // 开始进入判题队列
                .setStatus(Constants.Judge.STATUS_PENDING.getStatus())
                .setSubmitTime(new Date())
                .setVersion(0)
                .setIp(IpUtils.getUserIpAddr(request));

        // 如果比赛id不等于0，则说明为比赛提交
        if (isContestSubmission) {
            beforeDispatchInitService.initContestSubmission(judgeDto.getCid(), judgeDto.getPid(), userRolesVo, judge);
        } else if (isTrainingSubmission) {
            beforeDispatchInitService.initTrainingSubmission(judgeDto.getTid(), judgeDto.getPid(), userRolesVo, judge);
        } else { // 如果不是比赛提交和训练提交
            beforeDispatchInitService.initCommonSubmission(judgeDto.getPid(), judge);
        }

        // 将提交加入任务队列
        if (judgeDto.getIsRemote()) {
            // 如果是远程oj判题
            remoteJudgeDispatcher.sendTask(judge, judge.getDisplayPid(), isContestSubmission);
        } else {
            judgeDispatcher.sendTask(judge, isContestSubmission);
        }

        return judge;
    }


    /**
     * @MethodName resubmit
     * @Description 调用判题服务器提交失败超过60s后，用户点击按钮重新提交判题进入的方法
     * @Since 2021/2/12
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Judge resubmit(Long submitId) {

        Judge judge = judgeEntityService.getById(submitId);
        if (judge == null) {
            throw new StatusNotFoundException("此提交数据不存在！");
        }

        Problem problem = problemEntityService.getById(judge.getPid());

        // 如果是非比赛题目
        if (judge.getCid() == 0) {
            // 重判前，需要将该题目对应记录表一并更新
            // 如果该题已经是AC通过状态，更新该题目的用户ac做题表 user_acproblem
            if (judge.getStatus().intValue() == Constants.Judge.STATUS_ACCEPTED.getStatus().intValue()) {
                QueryWrapper<UserAcproblem> userAcproblemQueryWrapper = new QueryWrapper<>();
                userAcproblemQueryWrapper.eq("submit_id", judge.getSubmitId());
                userAcproblemEntityService.remove(userAcproblemQueryWrapper);
            }
        } else {
            if (problem.getIsRemote()) {
                // 将对应比赛记录设置成默认值
                UpdateWrapper<ContestRecord> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("submit_id", submitId).setSql("status=null,score=null");
                contestRecordEntityService.update(updateWrapper);
            } else {
                throw new StatusNotFoundException("错误！非vJudge题目在比赛过程无权限重新提交");
            }
        }

        // 重新进入等待队列
        judge.setStatus(Constants.Judge.STATUS_PENDING.getStatus());
        judge.setVersion(judge.getVersion() + 1);
        judge.setErrorMessage(null)
                .setOiRankScore(null)
                .setScore(null)
                .setTime(null)
                .setJudger("")
                .setMemory(null);
        judgeEntityService.updateById(judge);


        // 将提交加入任务队列
        if (problem.getIsRemote()) {
            // 如果是远程oj判题
            remoteJudgeDispatcher.sendTask(judge, problem.getProblemId(), judge.getCid() != 0);
        } else {
            judgeDispatcher.sendTask(judge, judge.getCid() != 0);
        }
        return judge;
    }


    /**
     * @MethodName getSubmission
     * @Description 获取单个提交记录的详情
     * @Since 2021/1/2
     */
    @Override
    public SubmissionInfoVo getSubmission(Long submitId) {

        Judge judge = judgeEntityService.getById(submitId);
        if (judge == null) {
            throw new StatusNotFoundException("此提交数据不存在！");
        }

        Session session = SecurityUtils.getSubject().getSession();
        UserRolesVo userRolesVo = (UserRolesVo) session.getAttribute("userInfo");
        if (userRolesVo == null) {
            throw new StatusAccessDeniedException("请先登录！");
        }

        // 是否为超级管理员
        boolean isRoot = SecurityUtils.getSubject().hasRole("root");
        // 是否为题目管理员
        boolean admin = SecurityUtils.getSubject().hasRole("problem_admin");
        // 限制：后台配置的时间 之前的代码 都不能查看
        if (!isRoot && !admin && judge.getSubmitTime().getTime() < codeVisibleStartTime) {
            throw new StatusNotFoundException("此提交数据当前时间无法查看！");
        }
        // 清空vj信息
        judge.setVjudgeUsername(null);
        judge.setVjudgeSubmitId(null);
        judge.setVjudgePassword(null);

        // 超级管理员与题目管理员有权限查看代码
        // 如果不是本人或者并未分享代码，则不可查看
        // 当此次提交代码不共享
        // 比赛提交只有比赛创建者和root账号可看代码
        SubmissionInfoVo submissionInfoVo = new SubmissionInfoVo();

        if (judge.getCid() != 0) {
            Contest contest = contestEntityService.getById(judge.getCid());
            if (!isRoot && !userRolesVo.getUid().equals(contest.getUid())) {
                // 如果是比赛,那么还需要判断是否为封榜,比赛管理员和超级管理员可以有权限查看(ACM题目除外)
                if (contest.getType().intValue() == Constants.Contest.TYPE_OI.getCode()
                        && contestValidator.isSealRank(userRolesVo.getUid(), contest, true, false)) {
                    submissionInfoVo.setSubmission(new Judge().setStatus(Constants.Judge.STATUS_SUBMITTED_UNKNOWN_RESULT.getStatus()));
                    return submissionInfoVo;
                }
                // 不是本人的话不能查看代码、时间，空间，长度
                if (!userRolesVo.getUid().equals(judge.getUid())) {
                    judge.setCode(null);
                    // 如果还在比赛时间，不是本人不能查看时间，空间，长度，错误提示信息
                    if (contest.getStatus().intValue() == Constants.Contest.STATUS_RUNNING.getCode()) {
                        judge.setTime(null);
                        judge.setMemory(null);
                        judge.setLength(null);
                        judge.setErrorMessage("The contest is in progress. You are not allowed to view other people's error information.");
                    }
                }
            }
        } else {
            if (!judge.getShare() && !isRoot && !admin) {
                // 需要判断是否为当前登陆用户自己的提交代码
                if (!judge.getUid().equals(userRolesVo.getUid())) {
                    judge.setCode(null);
                }
            }
        }

        Problem problem = problemEntityService.getById(judge.getPid());

        // 只允许用户查看ce错误,sf错误，se错误信息提示
        if (judge.getStatus().intValue() != Constants.Judge.STATUS_COMPILE_ERROR.getStatus() &&
                judge.getStatus().intValue() != Constants.Judge.STATUS_SYSTEM_ERROR.getStatus() &&
                judge.getStatus().intValue() != Constants.Judge.STATUS_SUBMITTED_FAILED.getStatus()) {
            judge.setErrorMessage("The error message does not support viewing.");
        }
        submissionInfoVo.setSubmission(judge);
        submissionInfoVo.setCodeShare(problem.getCodeShare());

        return submissionInfoVo;

    }


    /**
     * @MethodName updateSubmission
     * @Description 修改单个提交详情的分享权限
     * @Since 2021/1/2
     */
    @Override
    public void updateSubmission(Judge judge) {

        // 需要获取一下该token对应用户的数据
        Session session = SecurityUtils.getSubject().getSession();
        UserRolesVo userRolesVo = (UserRolesVo) session.getAttribute("userInfo");

        // 判断该提交是否为当前用户的
        if (!userRolesVo.getUid().equals(judge.getUid())) {
            throw new StatusForbiddenException("对不起，您不能修改他人的代码分享权限！");
        }
        Judge judgeInfo = judgeEntityService.getById(judge.getSubmitId());
        if (judgeInfo.getCid() != 0) {
            // 如果是比赛提交，不可分享！
            throw new StatusForbiddenException("对不起，您不能分享比赛题目的提交代码！");
        }
        judgeInfo.setShare(judge.getShare());
        boolean isOk = judgeEntityService.updateById(judgeInfo);
        if (!isOk) {
            throw new StatusFailException("修改代码权限失败！");
        }
    }

    /**
     * @MethodName getJudgeList
     * @Description 通用查询判题记录列表
     * @Since 2020/10/29
     */
    @Override
    public IPage<JudgeVo> getJudgeList(Integer limit,
                                       Integer currentPage,
                                       Boolean onlyMine,
                                       String searchPid,
                                       Integer searchStatus,
                                       String searchUsername,
                                       Boolean completeProblemID) {
        // 页数，每页题数若为空，设置默认值
        if (currentPage == null || currentPage < 1) currentPage = 1;
        if (limit == null || limit < 1) limit = 30;

        String uid = null;
        // 只查看当前用户的提交
        if (onlyMine) {
            // 需要获取一下该token对应用户的数据（有token便能获取到）
            Session session = SecurityUtils.getSubject().getSession();
            UserRolesVo userRolesVo = (UserRolesVo) session.getAttribute("userInfo");

            if (userRolesVo == null) {
                throw new StatusAccessDeniedException("当前用户数据为空，请您重新登陆！");
            }
            uid = userRolesVo.getUid();
        }
        if (searchPid != null) {
            searchPid = searchPid.trim();
        }
        if (searchUsername != null) {
            searchUsername = searchUsername.trim();
        }

        return judgeEntityService.getCommonJudgeList(limit,
                currentPage,
                searchPid,
                searchStatus,
                searchUsername,
                uid,
                completeProblemID);
    }


    /**
     * @MethodName checkJudgeResult
     * @Description 对提交列表状态为Pending和Judging的提交进行更新检查
     * @Since 2021/1/3
     */
    @Override
    public HashMap<Long, Object> checkCommonJudgeResult(SubmitIdListDto submitIdListDto) {

        List<Long> submitIds = submitIdListDto.getSubmitIds();

        if (CollectionUtils.isEmpty(submitIds)) {
            return new HashMap<>();
        }

        QueryWrapper<Judge> queryWrapper = new QueryWrapper<>();
        // lambada表达式过滤掉code
        queryWrapper.select(Judge.class, info -> !"code".equals(info.getColumn())).in("submit_id", submitIds);
        List<Judge> judgeList = judgeEntityService.list(queryWrapper);
        HashMap<Long, Object> result = new HashMap<>();
        for (Judge judge : judgeList) {
            judge.setCode(null);
            judge.setErrorMessage(null);
            judge.setVjudgeUsername(null);
            judge.setVjudgeSubmitId(null);
            judge.setVjudgePassword(null);
            result.put(judge.getSubmitId(), judge);
        }
        return result;
    }

    /**
     * @MethodName checkContestJudgeResult
     * @Description 需要检查是否为封榜，是否可以查询结果，避免有人恶意查询
     * @Since 2021/6/11
     */
    @Override
    public HashMap<Long, Object> checkContestJudgeResult(SubmitIdListDto submitIdListDto) {

        if (submitIdListDto.getCid() == null) {
            throw new StatusNotFoundException("查询比赛id不能为空");
        }

        if (CollectionUtils.isEmpty(submitIdListDto.getSubmitIds())) {
            return new HashMap<>();
        }

        Session session = SecurityUtils.getSubject().getSession();
        UserRolesVo userRolesVo = (UserRolesVo) session.getAttribute("userInfo");
        // 是否为超级管理员
        boolean isRoot = SecurityUtils.getSubject().hasRole("root");

        Contest contest = contestEntityService.getById(submitIdListDto.getCid());


        boolean isContestAdmin = isRoot || userRolesVo.getUid().equals(contest.getUid());
        // 如果是封榜时间且不是比赛管理员和超级管理员
        boolean isSealRank = contestValidator.isSealRank(userRolesVo.getUid(), contest, true, isRoot);

        QueryWrapper<Judge> queryWrapper = new QueryWrapper<>();
        // lambada表达式过滤掉code
        queryWrapper.select(Judge.class, info -> !"code".equals(info.getColumn()))
                .in("submit_id", submitIdListDto.getSubmitIds())
                .eq("cid", submitIdListDto.getCid())
                .between(isSealRank, "submit_time", contest.getStartTime(), contest.getSealRankTime());
        List<Judge> judgeList = judgeEntityService.list(queryWrapper);
        HashMap<Long, Object> result = new HashMap<>();
        for (Judge judge : judgeList) {
            judge.setCode(null);
            judge.setDisplayPid(null);
            judge.setErrorMessage(null);
            judge.setVjudgeUsername(null);
            judge.setVjudgeSubmitId(null);
            judge.setVjudgePassword(null);
            if (!judge.getUid().equals(userRolesVo.getUid()) && !isContestAdmin) {
                judge.setTime(null);
                judge.setMemory(null);
                judge.setLength(null);
            }
            result.put(judge.getSubmitId(), judge);
        }
        return result;
    }


    /**
     * @MethodName getJudgeCase
     * @Description 获得指定提交id的测试样例结果，暂不支持查看测试数据，只可看测试点结果，时间，空间，或者IO得分
     * @Since 2020/10/29
     */
    @Override
    @GetMapping("/get-all-case-result")
    public List<JudgeCase> getALLCaseResult(Long submitId) {

        Judge judge = judgeEntityService.getById(submitId);

        if (judge == null) {
            throw new StatusNotFoundException("此提交数据不存在！");
        }

        Problem problem = problemEntityService.getById(judge.getPid());

        // 如果该题不支持开放测试点结果查看
        if (!problem.getOpenCaseResult()) {
            return null;
        }

        Session session = SecurityUtils.getSubject().getSession();
        UserRolesVo userRolesVo = (UserRolesVo) session.getAttribute("userInfo");
        // 是否为超级管理员
        boolean isRoot = SecurityUtils.getSubject().hasRole("root");

        if (judge.getCid() != 0 && userRolesVo != null && !isRoot) {
            Contest contest = contestEntityService.getById(judge.getCid());
            // 如果不是比赛管理员 比赛封榜不能看
            if (!contest.getUid().equals(userRolesVo.getUid())) {
                // 当前是比赛期间 同时处于封榜时间
                if (contest.getSealRank() && contest.getStatus().intValue() == Constants.Contest.STATUS_RUNNING.getCode()
                        && contest.getSealRankTime().before(new Date())) {
                    throw new StatusForbiddenException("对不起，该题测试样例详情不能查看！");
                }

                // 若是比赛题目，只支持OI查看测试点情况，ACM强制禁止查看,比赛管理员除外
                if (problem.getType().intValue() == Constants.Contest.TYPE_ACM.getCode()) {
                    throw new StatusForbiddenException("对不起，该题测试样例详情不能查看！");
                }
            }
        }


        QueryWrapper<JudgeCase> wrapper = new QueryWrapper<>();

        if (userRolesVo == null || (!isRoot
                && !SecurityUtils.getSubject().hasRole("admin")
                && !SecurityUtils.getSubject().hasRole("problem_admin"))) {
            wrapper.select("time", "memory", "score", "status", "user_output");
        }
        wrapper.eq("submit_id", submitId)
                .last("order by length(input_data) asc,input_data asc");

        // 当前所有测试点只支持 空间 时间 状态码 IO得分 和错误信息提示查看而已
        return judgeCaseEntityService.list(wrapper);
    }
}