package com.simplefanc.voj.backend.service.file.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ZipUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.simplefanc.voj.backend.common.exception.StatusFailException;
import com.simplefanc.voj.backend.common.exception.StatusForbiddenException;
import com.simplefanc.voj.backend.common.utils.DownloadFileUtil;
import com.simplefanc.voj.backend.common.utils.ExcelUtil;
import com.simplefanc.voj.backend.dao.contest.ContestEntityService;
import com.simplefanc.voj.backend.dao.contest.ContestPrintEntityService;
import com.simplefanc.voj.backend.dao.contest.ContestProblemEntityService;
import com.simplefanc.voj.backend.dao.judge.JudgeEntityService;
import com.simplefanc.voj.backend.dao.user.UserInfoEntityService;
import com.simplefanc.voj.backend.pojo.bo.FilePathProps;
import com.simplefanc.voj.backend.pojo.vo.ACMContestRankVo;
import com.simplefanc.voj.backend.pojo.vo.ExcelIpVo;
import com.simplefanc.voj.backend.pojo.vo.OIContestRankVo;
import com.simplefanc.voj.backend.service.file.ContestFileService;
import com.simplefanc.voj.backend.service.oj.ContestCalculateRankService;
import com.simplefanc.voj.backend.service.oj.ContestService;
import com.simplefanc.voj.backend.validator.ContestValidator;
import com.simplefanc.voj.common.constants.ContestEnum;
import com.simplefanc.voj.common.constants.JudgeStatus;
import com.simplefanc.voj.common.pojo.entity.contest.Contest;
import com.simplefanc.voj.common.pojo.entity.contest.ContestPrint;
import com.simplefanc.voj.common.pojo.entity.contest.ContestProblem;
import com.simplefanc.voj.common.pojo.entity.judge.Judge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @Author: chenfan
 * @Date: 2022/3/10 14:27
 * @Description:
 */
@Service
@Slf4j(topic = "voj")
@RequiredArgsConstructor
public class ContestFileServiceImpl implements ContestFileService {

    private static final ThreadLocal<SimpleDateFormat> threadLocalTime = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyyMMddHHmmss");
        }
    };

    private final ContestEntityService contestEntityService;

    private final ContestProblemEntityService contestProblemEntityService;

    private final ContestPrintEntityService contestPrintEntityService;

    private final ContestService contestService;

    private final JudgeEntityService judgeEntityService;

    private final UserInfoEntityService userInfoEntityService;

    private final ContestCalculateRankService contestCalculateRankService;

    private final ContestValidator contestValidator;

    private final FilePathProps filePathProps;

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    private static String languageToFileSuffix(String language) {

        List<String> CLang = Arrays.asList("c", "gcc", "clang");
        List<String> CPPLang = Arrays.asList("c++", "g++", "clang++");
        List<String> PythonLang = Arrays.asList("python", "pypy");

        for (String lang : CPPLang) {
            if (language.contains(lang)) {
                return "cpp";
            }
        }

        if (language.contains("c#")) {
            return "cs";
        }

        for (String lang : CLang) {
            if (language.contains(lang)) {
                return "c";
            }
        }

        for (String lang : PythonLang) {
            if (language.contains(lang)) {
                return "py";
            }
        }

        if (language.contains("javascript")) {
            return "js";
        }

        if (language.contains("java")) {
            return "java";
        }

        if (language.contains("pascal")) {
            return "pas";
        }

        if (language.contains("go")) {
            return "go";
        }

        if (language.contains("php")) {
            return "php";
        }

        return "txt";
    }

    @Override
    public void downloadContestRank(Long cid, Boolean forceRefresh, Boolean removeStar, HttpServletResponse response)
            throws IOException {
        // ???????????????????????????
        Contest contest = contestEntityService.getById(cid);

        if (contest == null) {
            throw new StatusFailException("??????????????????????????????");
        }

        if (!contestValidator.isContestAdmin(contest)) {
            throw new StatusForbiddenException("???????????????????????????????????????????????????????????????");
        }

        // ??????????????????????????????
        boolean isOpenSealRank = contestValidator.isOpenSealRank(contest, forceRefresh);

        // ????????????displayID??????
        QueryWrapper<ContestProblem> contestProblemQueryWrapper = new QueryWrapper<>();
        contestProblemQueryWrapper.eq("cid", contest.getId()).select("display_id").orderByAsc("display_id");
        List<String> contestProblemDisplayIdList = contestProblemEntityService.list(contestProblemQueryWrapper)
                .stream()
                .sorted()
                .map(ContestProblem::getDisplayId)
                .collect(Collectors.toList());

        List<List<String>> head;
        List data;
        // ACM??????
        if (contest.getType().intValue() == ContestEnum.TYPE_ACM.getCode()) {
            List<ACMContestRankVo> acmContestRankVoList = contestCalculateRankService.calculateAcmRank(isOpenSealRank,
                    removeStar, contest, null, null);
            head = getContestRankExcelHead(contestProblemDisplayIdList, true);
            data = changeACMContestRankToExcelRowList(acmContestRankVoList,
                            contestProblemDisplayIdList, contest.getRankShowName());
        } else {
            List<OIContestRankVo> oiContestRankVoList = contestCalculateRankService.calculateOiRank(isOpenSealRank,
                    removeStar, contest, null, null);
            head = getContestRankExcelHead(contestProblemDisplayIdList, false);
            data = changeOIContestRankToExcelRowList(oiContestRankVoList, contestProblemDisplayIdList, contest.getRankShowName());
        }

        final String fileName = "contest_" + contest.getId() + "_rank";
        ExcelUtil.wrapExcelResponse(response, fileName);
        final ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream()).build();
        WriteSheet rankSheet = EasyExcel.writerSheet(0, "rank").head(head).build();
        WriteSheet ipSheet = EasyExcel.writerSheet(1, "ip").head(ExcelIpVo.class).build();
        excelWriter.write(data, rankSheet)
                .write(getExcelIpVo(contest), ipSheet);
        excelWriter.finish();
    }

    private List<ExcelIpVo> getExcelIpVo(Contest contest) {
        final Set<String> contestAdminUidList = contestService.getContestAdminUidList(contest);
        final List<Judge> judgeList = judgeEntityService.list(new QueryWrapper<Judge>().select("DISTINCT username, ip")
                .eq("cid", contest.getId())
                .between("submit_time", contest.getStartTime(), contest.getEndTime()));

        final Map<String, String> userNameIpMap = judgeList.stream()
                .filter(judge -> !contestAdminUidList.contains(judge.getUid()))
                .collect(Collectors.groupingBy(Judge::getUsername,
                        Collectors.mapping(Judge::getIp,
                                Collectors.joining(" & "))));
        return userNameIpMap.entrySet().stream()
                .map(entry -> new ExcelIpVo(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ExcelIpVo::getUsername))
                .collect(Collectors.toList());
    }

    @Override
    public void downloadContestAcSubmission(Long cid, Boolean excludeAdmin, String splitType,
                                            HttpServletResponse response) {

        Contest contest = contestEntityService.getById(cid);

        if (contest == null) {
            throw new StatusFailException("??????????????????????????????");
        }

        if (!contestValidator.isContestAdmin(contest)) {
            throw new StatusForbiddenException("??????????????????????????????????????????????????????AC?????????");
        }

        boolean isACM = contest.getType().intValue() == ContestEnum.TYPE_ACM.getCode();

        QueryWrapper<ContestProblem> contestProblemQueryWrapper = new QueryWrapper<>();
        contestProblemQueryWrapper.eq("cid", contest.getId());
        List<ContestProblem> contestProblemList = contestProblemEntityService.list(contestProblemQueryWrapper);

        List<String> superAdminUidList = userInfoEntityService.getSuperAdminUidList();

        QueryWrapper<Judge> judgeQueryWrapper = new QueryWrapper<>();
        judgeQueryWrapper.eq("cid", cid).eq(isACM, "status", JudgeStatus.STATUS_ACCEPTED.getStatus())
                // OI?????????????????????null???
                .isNotNull(!isACM, "score").between("submit_time", contest.getStartTime(), contest.getEndTime())
                // ????????????????????????root
                .ne(excludeAdmin, "uid", contest.getUid())
                .notIn(excludeAdmin && superAdminUidList.size() > 0, "uid", superAdminUidList)
                .orderByDesc("submit_time");

        List<Judge> judgeList = judgeEntityService.list(judgeQueryWrapper);

        // ??????????????????????????? -> username??????????????????
        String tmpFilesDir = filePathProps.getContestAcSubmissionTmpFolder() + File.separator + IdUtil.fastSimpleUUID();
        FileUtil.mkdir(tmpFilesDir);

        HashMap<String, Boolean> recordMap = new HashMap<>();
        if ("user".equals(splitType)) {
            splitCodeByUser(isACM, contestProblemList, judgeList, tmpFilesDir, recordMap);
        } else if ("problem".equals(splitType)) {
            splitByProblem(isACM, contestProblemList, judgeList, tmpFilesDir, recordMap);
        }

        String zipFileName = "contest_" + contest.getId() + "_" + System.currentTimeMillis() + ".zip";
        String zipPath = filePathProps.getContestAcSubmissionTmpFolder() + File.separator + zipFileName;
        ZipUtil.zip(tmpFilesDir, zipPath);
        DownloadFileUtil.download(response, zipPath, zipFileName, "????????????AC?????????????????????????????????");
        FileUtil.del(tmpFilesDir);
        FileUtil.del(zipPath);

    }

    /**
     * ?????????????????????????????????????????????
     */
    private void splitByProblem(boolean isACM, List<ContestProblem> contestProblemList, List<Judge> judgeList, String tmpFilesDir, HashMap<String, Boolean> recordMap) {
        for (ContestProblem contestProblem : contestProblemList) {
            // ???????????????????????????????????????
            String problemDir = tmpFilesDir + File.separator + contestProblem.getDisplayId();
            FileUtil.mkdir(problemDir);
            // ?????????ACM????????????????????????????????????????????????????????????????????????AC?????????????????????????????? ---> username_(666666).c
            // ?????????OI????????????????????????????????????????????????????????? ---> username_(666666)_100.c
            List<Judge> problemSubmissionList = judgeList.stream()
                    // ??????????????????????????????
                    .filter(judge -> judge.getPid().equals(contestProblem.getPid()))
                    // ??????????????????????????????
                    .sorted(Comparator.comparing(Judge::getSubmitTime).reversed()).collect(Collectors.toList());

            for (Judge judge : problemSubmissionList) {
                String filePath = problemDir + File.separator + judge.getUsername();
                if (!isACM) {
                    String key = judge.getUsername() + "_" + contestProblem.getDisplayId();
                    // OI??????????????????????????????
                    if (!recordMap.containsKey(key)) {
                        filePath += "_" + judge.getScore() + "_("
                                + threadLocalTime.get().format(judge.getSubmitTime()) + ")."
                                + languageToFileSuffix(judge.getLanguage().toLowerCase());
                        FileWriter fileWriter = new FileWriter(filePath);
                        fileWriter.write(judge.getCode());
                        recordMap.put(key, true);
                    }
                } else {
                    filePath += "_(" + threadLocalTime.get().format(judge.getSubmitTime()) + ")."
                            + languageToFileSuffix(judge.getLanguage().toLowerCase());
                    FileWriter fileWriter = new FileWriter(filePath);
                    fileWriter.write(judge.getCode());
                }
            }
        }
    }

    /**
     * ?????????????????????????????????
     */
    private void splitCodeByUser(boolean isACM, List<ContestProblem> contestProblemList, List<Judge> judgeList, String tmpFilesDir, HashMap<String, Boolean> recordMap) {
        List<String> usernameList = judgeList.stream()
                // ???????????????????????????
                .filter(distinctByKey(Judge::getUsername))
                // ????????????????????????
                .map(Judge::getUsername)
                .collect(Collectors.toList());

        HashMap<Long, String> cpIdMap = new HashMap<>();
        for (ContestProblem contestProblem : contestProblemList) {
            cpIdMap.put(contestProblem.getId(), contestProblem.getDisplayId());
        }

        for (String username : usernameList) {
            // ??????????????????????????????????????????
            String userDir = tmpFilesDir + File.separator + username;
            FileUtil.mkdir(userDir);
            // ?????????ACM????????????????????????????????????????????????????????????????????????AC?????????????????????????????? ---> A_(666666).c
            // ?????????OI????????????????????????????????????????????????????????? ---> A_(666666)_100.c
            List<Judge> userSubmissionList = judgeList.stream()
                    // ??????????????????????????????
                    .filter(judge -> judge.getUsername().equals(username))
                    // ??????????????????????????????
                    .sorted(Comparator.comparing(Judge::getSubmitTime).reversed()).collect(Collectors.toList());

            for (Judge judge : userSubmissionList) {
                String filePath = userDir + File.separator + cpIdMap.getOrDefault(judge.getCpid(), "null");

                // OI??????????????????????????????
                if (!isACM) {
                    String key = judge.getUsername() + "_" + judge.getPid();
                    if (!recordMap.containsKey(key)) {
                        filePath += "_" + judge.getScore() + "_("
                                + threadLocalTime.get().format(judge.getSubmitTime()) + ")."
                                + languageToFileSuffix(judge.getLanguage().toLowerCase());
                        FileWriter fileWriter = new FileWriter(filePath);
                        fileWriter.write(judge.getCode());
                        recordMap.put(key, true);
                    }

                } else {
                    filePath += "_(" + threadLocalTime.get().format(judge.getSubmitTime()) + ")."
                            + languageToFileSuffix(judge.getLanguage().toLowerCase());
                    FileWriter fileWriter = new FileWriter(filePath);
                    fileWriter.write(judge.getCode());
                }

            }
        }
    }

    @Override
    public void downloadContestPrintText(Long id, HttpServletResponse response) {
        ContestPrint contestPrint = contestPrintEntityService.getById(id);
        String filename = contestPrint.getUsername() + "_Contest_Print.txt";
        String filePath = filePathProps.getContestTextPrintFolder() + File.separator + id + File.separator + filename;
        if (!FileUtil.exist(filePath)) {
            FileWriter fileWriter = new FileWriter(filePath);
            fileWriter.write(contestPrint.getContent());
        }

        DownloadFileUtil.download(response, filePath, filename, "?????????????????????????????????????????????????????????");
    }

    public List<List<String>> getContestRankExcelHead(List<String> contestProblemDisplayIdList, Boolean isACM) {
        List<List<String>> headList = new LinkedList<>();
        List<String> head = new LinkedList<>();
        head.add("No");

        List<String> head0 = new LinkedList<>();
        head0.add("Rank");

        List<String> head1 = new LinkedList<>();
        head1.add("Username");
//        List<String> head2 = new LinkedList<>();
//        head2.add("ShowName");
        List<String> head3 = new LinkedList<>();
        head3.add("Real Name");
        List<String> head4 = new LinkedList<>();
        head4.add("School");

        headList.add(head);
        headList.add(head0);
        headList.add(head1);
//        headList.add(head2);
        headList.add(head3);
        headList.add(head4);

        List<String> head5 = new LinkedList<>();
        if (isACM) {
            head5.add("AC");
            List<String> head6 = new LinkedList<>();
            head6.add("Total Submission");
            List<String> head7 = new LinkedList<>();
            head7.add("Total Penalty Time");
            headList.add(head5);
            headList.add(head6);
            headList.add(head7);
        } else {
            head5.add("Total Score");
            headList.add(head5);
        }

        // ???????????????
        for (String displayId : contestProblemDisplayIdList) {
            List<String> tmp = new LinkedList<>();
            tmp.add(displayId);
            headList.add(tmp);
        }
        return headList;
    }

    public List<List<Object>> changeACMContestRankToExcelRowList(List<ACMContestRankVo> acmContestRankVoList,
                                                                 List<String> contestProblemDisplayIdList, String rankShowName) {
        List<List<Object>> allRowDataList = new LinkedList<>();
        for (ACMContestRankVo acmContestRankVo : acmContestRankVoList) {
            List<Object> rowData = new LinkedList<>();
            rowData.add(acmContestRankVo.getSeq());
            rowData.add(acmContestRankVo.getRank() == -1 ? "*" : acmContestRankVo.getRank().toString());
            rowData.add(acmContestRankVo.getUsername());
//            if ("username".equals(rankShowName)) {
//                rowData.add(acmContestRankVo.getUsername());
//            } else if ("realname".equals(rankShowName)) {
//                rowData.add(acmContestRankVo.getRealname());
//            } else if ("nickname".equals(rankShowName)) {
//                rowData.add(acmContestRankVo.getNickname());
//            } else {
//                rowData.add("");
//            }
            rowData.add(acmContestRankVo.getRealname());
            rowData.add(acmContestRankVo.getSchool());
            rowData.add(acmContestRankVo.getAc());
            rowData.add(acmContestRankVo.getTotal());
            rowData.add(acmContestRankVo.getTotalTime());
            HashMap<String, HashMap<String, Object>> submissionInfo = acmContestRankVo.getSubmissionInfo();
            for (String displayId : contestProblemDisplayIdList) {
                HashMap<String, Object> problemInfo = submissionInfo.getOrDefault(displayId, null);
                // ???????????????????????????
                if (problemInfo != null) {
                    boolean isAC = (boolean) problemInfo.getOrDefault("isAC", false);
                    String info = "";
                    int errorNum = (int) problemInfo.getOrDefault("errorNum", 0);
                    int tryNum = (int) problemInfo.getOrDefault("tryNum", 0);
                    if (isAC) {
                        if (errorNum == 0) {
                            info = "+(1)";
                        } else {
                            info = "-(" + (errorNum + 1) + ")";
                        }
                    } else {
                        if (tryNum != 0 && errorNum != 0) {
                            info = "-(" + errorNum + "+" + tryNum + ")";
                        } else if (errorNum != 0) {
                            info = "-(" + errorNum + ")";
                        } else if (tryNum != 0) {
                            info = "?(" + tryNum + ")";
                        }
                    }
                    rowData.add(info);
                } else {
                    rowData.add("");
                }
            }
            allRowDataList.add(rowData);
        }
        return allRowDataList;
    }

    public List<List<Object>> changeOIContestRankToExcelRowList(List<OIContestRankVo> oiContestRankVoList,
                                                                List<String> contestProblemDisplayIdList, String rankShowName) {
        List<List<Object>> allRowDataList = new LinkedList<>();
        for (OIContestRankVo oiContestRankVo : oiContestRankVoList) {
            List<Object> rowData = new LinkedList<>();
            rowData.add(oiContestRankVo.getSeq());
            rowData.add(oiContestRankVo.getRank() == -1 ? "*" : oiContestRankVo.getRank().toString());
            rowData.add(oiContestRankVo.getUsername());
//            if ("username".equals(rankShowName)) {
//                rowData.add(oiContestRankVo.getUsername());
//            } else if ("realname".equals(rankShowName)) {
//                rowData.add(oiContestRankVo.getRealname());
//            } else if ("nickname".equals(rankShowName)) {
//                rowData.add(oiContestRankVo.getNickname());
//            } else {
//                rowData.add("");
//            }
            rowData.add(oiContestRankVo.getRealname());
            rowData.add(oiContestRankVo.getSchool());
            rowData.add(oiContestRankVo.getTotalScore());
            Map<String, Integer> submissionInfo = oiContestRankVo.getSubmissionInfo();
            for (String displayId : contestProblemDisplayIdList) {
                Integer score = submissionInfo.getOrDefault(displayId, null);
                // ?????????????????????????????????????????????????????????????????????????????????
                if (score != null) {
                    rowData.add(score);
                } else {
                    rowData.add("");
                }
            }
            allRowDataList.add(rowData);
        }
        return allRowDataList;
    }

}