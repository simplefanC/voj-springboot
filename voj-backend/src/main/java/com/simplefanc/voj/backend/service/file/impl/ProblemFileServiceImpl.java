package com.simplefanc.voj.backend.service.file.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.simplefanc.voj.backend.common.exception.StatusFailException;
import com.simplefanc.voj.backend.common.exception.StatusSystemErrorException;
import com.simplefanc.voj.backend.common.utils.DownloadFileUtil;
import com.simplefanc.voj.backend.dao.problem.LanguageEntityService;
import com.simplefanc.voj.backend.dao.problem.ProblemCaseEntityService;
import com.simplefanc.voj.backend.dao.problem.ProblemEntityService;
import com.simplefanc.voj.backend.dao.problem.TagEntityService;
import com.simplefanc.voj.backend.pojo.bo.FilePathProps;
import com.simplefanc.voj.backend.pojo.dto.ProblemDto;
import com.simplefanc.voj.backend.pojo.vo.ImportProblemVo;
import com.simplefanc.voj.backend.pojo.vo.UserRolesVo;
import com.simplefanc.voj.backend.service.file.ProblemFileService;
import com.simplefanc.voj.backend.shiro.UserSessionUtil;
import com.simplefanc.voj.common.constants.Constant;
import com.simplefanc.voj.common.pojo.entity.problem.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * @Author: chenfan
 * @Date: 2022/3/10 14:40
 * @Description:
 */
@Service
@Slf4j(topic = "voj")
@RequiredArgsConstructor
public class ProblemFileServiceImpl implements ProblemFileService {

    private final LanguageEntityService languageEntityService;

    private final ProblemEntityService problemEntityService;

    private final ProblemCaseEntityService problemCaseEntityService;

    private final TagEntityService tagEntityService;

    private final FilePathProps filePathProps;

    /**
     * @param file
     * @MethodName importProblem
     * @Description zip?????????????????? ???????????????????????????
     * @Return
     * @Since 2021/5/27
     */
    // TODO ????????????
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importProblem(MultipartFile file) {

        String suffix = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".") + 1);
        if (!"zip".toUpperCase().contains(suffix.toUpperCase())) {
            throw new StatusFailException("?????????zip?????????????????????????????????");
        }

        String fileDirId = IdUtil.simpleUUID();
        String fileDir = filePathProps.getTestcaseTmpFolder() + File.separator + fileDirId;
        String filePath = fileDir + File.separator + file.getOriginalFilename();
        // ???????????????????????????
        FileUtil.mkdir(fileDir);
        try {
            file.transferTo(new File(filePath));
        } catch (IOException e) {
            FileUtil.del(fileDir);
            throw new StatusSystemErrorException("?????????????????????????????????????????????");
        }

        // ????????????????????????????????????
        ZipUtil.unzip(filePath, fileDir);

        // ??????zip??????
        FileUtil.del(filePath);

        // ????????????????????????
        File testCaseFileList = new File(fileDir);
        File[] files = testCaseFileList.listFiles();
        if (files == null || files.length == 0) {
            FileUtil.del(fileDir);
            throw new StatusFailException("?????????????????????????????????????????????");
        }

        HashMap<String, File> problemInfo = new HashMap<>();
        HashMap<String, File> testcaseInfo = new HashMap<>();

        for (File tmp : files) {
            if (tmp.isFile()) {
                // ?????????????????????json??????
                if (!tmp.getName().endsWith("json")) {
                    FileUtil.del(fileDir);
                    throw new StatusFailException("????????????" + tmp.getName() + "?????????????????????????????????json?????????");
                }
                String tmpPreName = tmp.getName().substring(0, tmp.getName().lastIndexOf("."));
                problemInfo.put(tmpPreName, tmp);
            }
            if (tmp.isDirectory()) {
                testcaseInfo.put(tmp.getName(), tmp);
            }
        }

        // ??????json??????????????????
        HashMap<String, ImportProblemVo> problemVoMap = new HashMap<>();
        for (String key : problemInfo.keySet()) {
            // ??????????????????????????????????????????
            if (testcaseInfo.getOrDefault(key, null) == null) {
                FileUtil.del(fileDir);
                throw new StatusFailException("?????????????????????" + key + "??????????????????????????????????????????????????????????????????");
            }
            try {
                FileReader fileReader = new FileReader(problemInfo.get(key));
                ImportProblemVo importProblemVo = JSONUtil.toBean(fileReader.readString(), ImportProblemVo.class);
                problemVoMap.put(key, importProblemVo);
            } catch (Exception e) {
                FileUtil.del(fileDir);
                throw new StatusFailException("?????????????????????" + key + "?????????json??????????????????" + e.getLocalizedMessage());
            }
        }

        QueryWrapper<Language> languageQueryWrapper = new QueryWrapper<>();
        languageQueryWrapper.eq("oj", Constant.LOCAL);
        List<Language> languageList = languageEntityService.list(languageQueryWrapper);

        HashMap<String, Long> languageMap = new HashMap<>();
        for (Language language : languageList) {
            languageMap.put(language.getName(), language.getId());
        }

        // ???????????????????????????
        UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();

        List<ProblemDto> problemDtos = new LinkedList<>();
        List<Tag> tagList = tagEntityService.list(new QueryWrapper<Tag>().eq("oj", Constant.LOCAL));
        HashMap<String, Tag> tagMap = new HashMap<>();
        for (Tag tag : tagList) {
            tagMap.put(tag.getName().toUpperCase(), tag);
        }
        for (String key : problemInfo.keySet()) {
            ImportProblemVo importProblemVo = problemVoMap.get(key);
            // ?????????????????????
            List<Language> languages = new LinkedList<>();
            for (String lang : importProblemVo.getLanguages()) {
                Long lid = languageMap.getOrDefault(lang, null);

                if (lid == null) {
                    throw new StatusFailException("?????????????????????" + key + "????????????????????????????????????????????????????????????????????????");
                }
                languages.add(new Language().setId(lid).setName(lang));
            }

            // ???????????????????????????
            List<CodeTemplate> codeTemplates = new LinkedList<>();
            for (Map<String, String> tmp : importProblemVo.getCodeTemplates()) {
                String language = tmp.getOrDefault("language", null);
                String code = tmp.getOrDefault("code", null);
                Long lid = languageMap.getOrDefault(language, null);
                if (language == null || code == null || lid == null) {
                    FileUtil.del(fileDir);
                    throw new StatusFailException("?????????????????????" + key + "??????????????????????????????????????????????????????????????????????????????");
                }
                codeTemplates.add(new CodeTemplate().setCode(code).setStatus(true).setLid(lid));
            }

            // ???????????????
            List<Tag> tags = new LinkedList<>();
            for (String tagStr : importProblemVo.getTags()) {
                Tag tag = tagMap.getOrDefault(tagStr.toUpperCase(), null);
                if (tag == null) {
                    tags.add(new Tag().setName(tagStr).setOj(Constant.LOCAL));
                } else {
                    tags.add(tag);
                }
            }

            // TODO
            Problem problem = BeanUtil.mapToBean(importProblemVo.getProblem(), Problem.class, true);
            if (problem.getAuthor() == null) {
                problem.setAuthor(userRolesVo.getUsername());
            }
            List<ProblemCase> problemCaseList = new LinkedList<>();
            for (Map<String, Object> tmp : importProblemVo.getSamples()) {
                problemCaseList.add(BeanUtil.mapToBean(tmp, ProblemCase.class, true));
            }

            // ????????????????????????????????????????????????
            if (importProblemVo.getUserExtraFile() != null) {
                JSONObject userExtraFileJson = JSONUtil.parseObj(importProblemVo.getUserExtraFile());
                problem.setUserExtraFile(userExtraFileJson.toString());
            }
            if (importProblemVo.getJudgeExtraFile() != null) {
                JSONObject judgeExtraFileJson = JSONUtil.parseObj(importProblemVo.getJudgeExtraFile());
                problem.setJudgeExtraFile(judgeExtraFileJson.toString());
            }

            ProblemDto problemDto = new ProblemDto();
            problemDto.setJudgeMode(importProblemVo.getJudgeMode()).setProblem(problem).setCodeTemplates(codeTemplates)
                    .setTags(tags).setLanguages(languages).setUploadTestcaseDir(fileDir + File.separator + key)
                    .setIsUploadTestCase(true).setSamples(problemCaseList);

            problemDtos.add(problemDto);
        }
        for (ProblemDto problemDto : problemDtos) {
            problemEntityService.adminAddProblem(problemDto);
        }
    }

    /**
     * @param pidList
     * @param response
     * @MethodName exportProblem
     * @Description ?????????????????????????????????????????????zip ???????????????????????????
     * @Return
     * @Since 2021/5/28
     */
    @Override
    public void exportProblem(List<Long> pidList, HttpServletResponse response) {

        QueryWrapper<Language> languageQueryWrapper = new QueryWrapper<>();
        languageQueryWrapper.eq("oj", Constant.LOCAL);
        List<Language> languageList = languageEntityService.list(languageQueryWrapper);

        HashMap<Long, String> languageMap = new HashMap<>();
        for (Language language : languageList) {
            languageMap.put(language.getId(), language.getName());
        }

        List<Tag> tagList = tagEntityService.list();

        HashMap<Long, String> tagMap = new HashMap<>();
        for (Tag tag : tagList) {
            tagMap.put(tag.getId(), tag.getName());
        }

        String workDir = filePathProps.getFileDownloadTmpFolder() + File.separator + IdUtil.simpleUUID();

        // ???????????????
        ExecutorService threadPool = new ThreadPoolExecutor(
                // ???????????????
                2,
                // ?????????????????????????????????????????????
                4,
                // ?????????????????????????????????????????????????????????
                3,
                // ????????????????????????
                TimeUnit.SECONDS,
                // ????????????????????????????????????
                new LinkedBlockingDeque<>(200), Executors.defaultThreadFactory(),
                // ?????????????????????????????????????????????????????????????????????
                new ThreadPoolExecutor.DiscardOldestPolicy());

        List<FutureTask<Void>> futureTasks = new ArrayList<>();
        for (Long pid : pidList) {
            futureTasks.add(new FutureTask<>(new ExportProblemTask(workDir, pid, languageMap, tagMap)));
        }
        // ??????????????????????????????
        for (FutureTask<Void> futureTask : futureTasks) {
            threadPool.submit(futureTask);
        }
        // ?????????????????????????????????????????????????????????????????????
        if (!threadPool.isShutdown()) {
            threadPool.shutdown();
        }
        // ???????????????, ?????????????????????
        try {
            threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            log.error("???????????????--------------->", e);
        }

        String fileName = "problem_export_" + System.currentTimeMillis() + ".zip";
        // ????????????????????????????????????zip
        ZipUtil.zip(workDir, filePathProps.getFileDownloadTmpFolder() + File.separator + fileName);
        DownloadFileUtil.download(response, filePathProps.getFileDownloadTmpFolder() + File.separator + fileName, fileName, "????????????????????????????????????????????????????????????");
        // ??????????????????
        FileUtil.del(workDir);
        FileUtil.del(filePathProps.getFileDownloadTmpFolder() + File.separator + fileName);
    }


    class ExportProblemTask implements Callable<Void> {
        String workDir;
        Long pid;
        HashMap<Long, String> languageMap;
        HashMap<Long, String> tagMap;

        public ExportProblemTask(String workDir, Long pid, HashMap<Long, String> languageMap, HashMap<Long, String> tagMap) {
            this.workDir = workDir;
            this.pid = pid;
            this.languageMap = languageMap;
            this.tagMap = tagMap;
        }

        @Override
        public Void call() throws Exception {
            String testcaseWorkDir = filePathProps.getTestcaseBaseFolder() + File.separator + "problem_" + pid;
            File file = new File(testcaseWorkDir);

            List<HashMap<String, Object>> problemCases = new LinkedList<>();
            // ???????????? ????????????????????????
            if (!file.exists() || file.listFiles() == null) {
                QueryWrapper<ProblemCase> problemCaseQueryWrapper = new QueryWrapper<>();
                problemCaseQueryWrapper.eq("pid", pid);
                List<ProblemCase> problemCaseList = problemCaseEntityService.list(problemCaseQueryWrapper);
                FileUtil.mkdir(testcaseWorkDir);
                // ????????????
                for (int i = 0; i < problemCaseList.size(); i++) {
                    String filePreName = testcaseWorkDir + File.separator + (i + 1);
                    String inputName = filePreName + ".in";
                    String outputName = filePreName + ".out";
                    FileWriter infileWriter = new FileWriter(inputName);
                    infileWriter.write(problemCaseList.get(i).getInput());
                    FileWriter outfileWriter = new FileWriter(outputName);
                    outfileWriter.write(problemCaseList.get(i).getOutput());

                    ProblemCase problemCase = problemCaseList.get(i).setPid(null).setInput(inputName)
                            .setOutput(outputName).setGmtCreate(null).setStatus(null).setId(null)
                            .setGmtModified(null);
                    HashMap<String, Object> problemCaseMap = new HashMap<>();
                    BeanUtil.beanToMap(problemCase, problemCaseMap, false, true);
                    problemCases.add(problemCaseMap);
                }
                FileUtil.copy(testcaseWorkDir, workDir, true);

            } else {
                String infoPath = testcaseWorkDir + File.separator + "info";
                if (FileUtil.exist(infoPath)) {
                    FileReader reader = new FileReader(infoPath);
                    JSONObject jsonObject = JSONUtil.parseObj(reader.readString());
                    JSONArray testCases = jsonObject.getJSONArray("testCases");
                    for (int i = 0; i < testCases.size(); i++) {
                        JSONObject jsonObject1 = testCases.get(i, JSONObject.class);
                        HashMap<String, Object> problemCaseMap = new HashMap<>();
                        problemCaseMap.put("input", jsonObject1.getStr("inputName"));
                        problemCaseMap.put("output", jsonObject1.getStr("outputName"));
                        Integer score = jsonObject1.getInt("score");
                        if (score != null && score > 0) {
                            problemCaseMap.put("score", score);
                        }
                        problemCases.add(problemCaseMap);
                    }
                }
                FileUtil.copy(testcaseWorkDir, workDir, true);
            }
            ImportProblemVo importProblemVo = problemEntityService.buildExportProblem(pid, problemCases,
                    languageMap, tagMap);
            String content = JSONUtil.toJsonStr(importProblemVo);
            FileWriter fileWriter = new FileWriter(workDir + File.separator + "problem_" + pid + ".json");
            fileWriter.write(content);
            return null;
        }
    }

}