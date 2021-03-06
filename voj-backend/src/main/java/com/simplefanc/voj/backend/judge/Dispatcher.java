package com.simplefanc.voj.backend.judge;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.simplefanc.voj.backend.common.constants.CallJudgerType;
import com.simplefanc.voj.backend.dao.judge.JudgeEntityService;
import com.simplefanc.voj.backend.dao.judge.JudgeServerEntityService;
import com.simplefanc.voj.backend.dao.judge.RemoteJudgeAccountEntityService;
import com.simplefanc.voj.common.constants.JudgeStatus;
import com.simplefanc.voj.common.pojo.dto.CompileDTO;
import com.simplefanc.voj.common.pojo.dto.ToJudge;
import com.simplefanc.voj.common.pojo.entity.judge.Judge;
import com.simplefanc.voj.common.pojo.entity.judge.JudgeServer;
import com.simplefanc.voj.common.pojo.entity.judge.RemoteJudgeAccount;
import com.simplefanc.voj.common.result.CommonResult;
import com.simplefanc.voj.common.result.ResultStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: chenfan
 * @Date: 2021/4/15 17:29
 * @Description:
 */
@Component
@Slf4j(topic = "voj")
@RequiredArgsConstructor
public class Dispatcher {

    private final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(20);

    private final static Map<String, Future> futureTaskMap = new ConcurrentHashMap<>(20);

    private final RestTemplate restTemplate;

    private final JudgeServerEntityService judgeServerEntityService;

    private final JudgeEntityService judgeEntityService;

    private final ChooseUtils chooseUtils;

    private final RemoteJudgeAccountEntityService remoteJudgeAccountService;

    public CommonResult dispatcher(CallJudgerType type, String path, Object data) {
        switch (type) {
            case JUDGE:
                ToJudge judgeData = (ToJudge) data;
                toJudge(path, judgeData, judgeData.getJudge().getSubmitId(), judgeData.getRemoteJudgeProblem() != null);
                break;
            case COMPILE:
                CompileDTO compileDTO = (CompileDTO) data;
                return toCompile(path, compileDTO);
            default:
                throw new IllegalArgumentException("?????????????????????????????????");
        }
        return null;
    }

    public void toJudge(String path, ToJudge data, Long submitId, Boolean isRemote) {
        String oj = null;
        if (isRemote) {
            oj = data.getRemoteJudgeProblem().split("-")[0];
        }
        String key = UUID.randomUUID().toString() + submitId;
        ScheduledFuture<?> scheduledFuture = scheduler.scheduleWithFixedDelay(
                new SubmitTask(path, data, submitId, isRemote, oj, key), 0, 2, TimeUnit.SECONDS);
        futureTaskMap.put(key, scheduledFuture);
    }

    private void cancelFutureTask(String key) {
        Future future = futureTaskMap.get(key);
        if (future != null) {
            boolean isCanceled = future.cancel(true);
            if (isCanceled) {
                futureTaskMap.remove(key);
            }
        }
    }

    public CommonResult toCompile(String path, CompileDTO data) {
        CommonResult result = CommonResult.errorResponse("???????????????????????????????????????????????????");
        JudgeServer judgeServer = chooseUtils.chooseJudgeServer(false);
        if (judgeServer != null) {
            try {
                result = restTemplate.postForObject("http://" + judgeServer.getUrl() + path, data, CommonResult.class);
            } catch (Exception e) {
                log.error("?????????????????????[" + judgeServer.getUrl() + "]????????????-------------->", e);
            } finally {
                // ????????????????????????????????????????????????????????????????????????1
                reduceCurrentTaskNum(judgeServer.getId());
            }
        }
        return result;
    }

    private void checkResult(CommonResult<Void> result, Long submitId) {
        Judge judge = new Judge();
        // ????????????
        if (result == null) {
            judge.setSubmitId(submitId);
            judge.setStatus(JudgeStatus.STATUS_SUBMITTED_FAILED.getStatus());
            judge.setErrorMessage("Failed to connect the JudgeServer. Please resubmit this submission again!");
            judgeEntityService.updateById(judge);
        } else {
            // ????????????????????????200 ?????????????????????
            if (result.getStatus() != ResultStatus.SUCCESS.getStatus()) {
                // ??????????????????
                judge.setStatus(JudgeStatus.STATUS_SYSTEM_ERROR.getStatus()).setErrorMessage(result.getMsg());
                judgeEntityService.updateById(judge);
            }
        }

    }

    public void reduceCurrentTaskNum(Integer id) {
        UpdateWrapper<JudgeServer> judgeServerUpdateWrapper = new UpdateWrapper<>();
        judgeServerUpdateWrapper.setSql("task_number = task_number-1").eq("id", id);
        boolean isOk = judgeServerEntityService.update(judgeServerUpdateWrapper);
        if (!isOk) {
            // ????????????
            tryAgainUpdateJudge(judgeServerUpdateWrapper);
        }
    }

    public void tryAgainUpdateJudge(UpdateWrapper<JudgeServer> updateWrapper) {
        boolean retryable;
        int attemptNumber = 0;
        do {
            boolean success = judgeServerEntityService.update(updateWrapper);
            if (success) {
                return;
            } else {
                attemptNumber++;
                retryable = attemptNumber < 8;
                if (attemptNumber == 8) {
                    break;
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        while (retryable);
    }

    public void changeRemoteJudgeStatus(String remoteJudge, String username) {
        UpdateWrapper<RemoteJudgeAccount> remoteJudgeAccountUpdateWrapper = new UpdateWrapper<>();
        remoteJudgeAccountUpdateWrapper.set("status", true).eq("status", false).eq("username", username);
        remoteJudgeAccountUpdateWrapper.eq("oj", remoteJudge);

        boolean isOk = remoteJudgeAccountService.update(remoteJudgeAccountUpdateWrapper);

        if (!isOk) {
            // ??????8???
            tryAgainUpdateAccount(remoteJudgeAccountUpdateWrapper, remoteJudge, username);
        }
    }

    private void tryAgainUpdateAccount(UpdateWrapper<RemoteJudgeAccount> updateWrapper, String remoteJudge,
                                       String username) {
        boolean retryable;
        int attemptNumber = 0;
        do {
            boolean success = remoteJudgeAccountService.update(updateWrapper);
            if (success) {
                return;
            } else {
                attemptNumber++;
                retryable = attemptNumber < 8;
                if (attemptNumber == 8) {
                    log.error("????????????????????????????????????????????????----------->{}", "oj:" + remoteJudge + ",username:" + username);
                    break;
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        while (retryable);
    }

    class SubmitTask implements Runnable {

        String path;

        ToJudge data;

        Long submitId;

        Boolean isRemote;

        String oj;

        String key;

        // ??????600s
        AtomicInteger count = new AtomicInteger(0);

        public SubmitTask(String path, ToJudge data, Long submitId, Boolean isRemote, String oj, String key) {
            this.path = path;
            this.data = data;
            this.submitId = submitId;
            this.isRemote = isRemote;
            this.oj = oj;
            this.key = key;
        }

        @Override
        public void run() {
            // 300??????????????????????????????
            if (count.get() > 300) {
                handleSubmitFailure();
                return;
            }
            count.getAndIncrement();
            JudgeServer judgeServer = chooseUtils.chooseJudgeServer(isRemote);
            // ????????????????????????
            if (judgeServer != null) {
                handleJudgeProcess(judgeServer);
            }
        }

        private void handleSubmitFailure() {
            // ???????????????????????????????????????
            if (isRemote) {
                changeRemoteJudgeStatus(oj, data.getUsername());
            }
            checkResult(null, submitId);
            cancelFutureTask(key);
        }

        private void handleJudgeProcess(JudgeServer judgeServer) {
            data.setJudgeServerIp(judgeServer.getIp());
            data.setJudgeServerPort(judgeServer.getPort());
            CommonResult result = null;
            try {
                // https://blog.csdn.net/qq_35893120/article/details/118637987
                result = restTemplate.postForObject("http://" + judgeServer.getUrl() + path, data, CommonResult.class);
            } catch (Exception e) {
                log.error("?????????????????????[" + judgeServer.getUrl() + "]????????????-------------->", e);
                if (isRemote) {
                    changeRemoteJudgeStatus(oj, data.getUsername());
                }
            } finally {
                checkResult(result, submitId);
                // ????????????????????????????????????????????????????????????????????????1
                reduceCurrentTaskNum(judgeServer.getId());
                cancelFutureTask(key);
            }
        }

    }

}