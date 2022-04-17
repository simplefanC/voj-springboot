package com.simplefanc.voj.service.file.impl;

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
import com.simplefanc.voj.common.exception.StatusFailException;
import com.simplefanc.voj.common.exception.StatusSystemErrorException;
import com.simplefanc.voj.common.result.ResultStatus;
import com.simplefanc.voj.dao.problem.LanguageEntityService;
import com.simplefanc.voj.dao.problem.ProblemCaseEntityService;
import com.simplefanc.voj.dao.problem.ProblemEntityService;
import com.simplefanc.voj.dao.problem.TagEntityService;
import com.simplefanc.voj.pojo.bo.FilePathProps;
import com.simplefanc.voj.pojo.dto.ProblemDto;
import com.simplefanc.voj.pojo.entity.problem.*;
import com.simplefanc.voj.pojo.vo.ImportProblemVo;
import com.simplefanc.voj.pojo.vo.UserRolesVo;
import com.simplefanc.voj.service.file.ProblemFileService;
import com.simplefanc.voj.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;

/**
 * @Author: chenfan
 * @Date: 2022/3/10 14:40
 * @Description:
 */
@Service
@Slf4j(topic = "voj")
public class ProblemFileServiceImpl implements ProblemFileService {
    @Autowired
    private LanguageEntityService languageEntityService;

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private ProblemCaseEntityService problemCaseEntityService;

    @Autowired
    private TagEntityService tagEntityService;

    @Autowired
    private FilePathProps filePathProps;

    /**
     * @param file
     * @MethodName importProblem
     * @Description zip文件导入题目 仅超级管理员可操作
     * @Return
     * @Since 2021/5/27
     */
    // TODO 行数过多
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importProblem(MultipartFile file) {

        String suffix = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".") + 1);
        if (!"zip".toUpperCase().contains(suffix.toUpperCase())) {
            throw new StatusFailException("请上传zip格式的题目文件压缩包！");
        }

        String fileDirId = IdUtil.simpleUUID();
        String fileDir = filePathProps.getTestcaseTmpFolder() + File.separator + fileDirId;
        String filePath = fileDir + File.separator + file.getOriginalFilename();
        // 文件夹不存在就新建
        FileUtil.mkdir(fileDir);
        try {
            file.transferTo(new File(filePath));
        } catch (IOException e) {
            FileUtil.del(fileDir);
            throw new StatusSystemErrorException("服务器异常：评测数据上传失败！");
        }

        // 将压缩包压缩到指定文件夹
        ZipUtil.unzip(filePath, fileDir);

        // 删除zip文件
        FileUtil.del(filePath);


        // 检查文件是否存在
        File testCaseFileList = new File(fileDir);
        File[] files = testCaseFileList.listFiles();
        if (files == null || files.length == 0) {
            FileUtil.del(fileDir);
            throw new StatusFailException("评测数据压缩包里文件不能为空！");
        }


        HashMap<String, File> problemInfo = new HashMap<>();
        HashMap<String, File> testcaseInfo = new HashMap<>();

        for (File tmp : files) {
            if (tmp.isFile()) {
                // 检查文件是否时json文件
                if (!tmp.getName().endsWith("json")) {
                    FileUtil.del(fileDir);
                    throw new StatusFailException("编号为：" + tmp.getName() + "的文件格式错误，请使用json文件！");
                }
                String tmpPreName = tmp.getName().substring(0, tmp.getName().lastIndexOf("."));
                problemInfo.put(tmpPreName, tmp);
            }
            if (tmp.isDirectory()) {
                testcaseInfo.put(tmp.getName(), tmp);
            }
        }

        // 读取json文件生成对象
        HashMap<String, ImportProblemVo> problemVoMap = new HashMap<>();
        for (String key : problemInfo.keySet()) {
            // 若有名字不对应，直接返回失败
            if (testcaseInfo.getOrDefault(key, null) == null) {
                FileUtil.del(fileDir);
                throw new StatusFailException("请检查编号为：" + key + "的题目数据文件与测试数据文件夹是否一一对应！");
            }
            try {
                FileReader fileReader = new FileReader(problemInfo.get(key));
                ImportProblemVo importProblemVo = JSONUtil.toBean(fileReader.readString(), ImportProblemVo.class);
                problemVoMap.put(key, importProblemVo);
            } catch (Exception e) {
                FileUtil.del(fileDir);
                throw new StatusFailException("请检查编号为：" + key + "的题目json文件的格式：" + e.getLocalizedMessage());
            }
        }

        QueryWrapper<Language> languageQueryWrapper = new QueryWrapper<>();
        languageQueryWrapper.eq("oj", "ME");
        List<Language> languageList = languageEntityService.list(languageQueryWrapper);

        HashMap<String, Long> languageMap = new HashMap<>();
        for (Language language : languageList) {
            languageMap.put(language.getName(), language.getId());
        }

        // 获取当前登录的用户
        Session session = SecurityUtils.getSubject().getSession();
        UserRolesVo userRolesVo = (UserRolesVo) session.getAttribute("userInfo");

        List<ProblemDto> problemDtos = new LinkedList<>();
        List<Tag> tagList = tagEntityService.list(new QueryWrapper<Tag>().eq("oj", "ME"));
        HashMap<String, Tag> tagMap = new HashMap<>();
        for (Tag tag : tagList) {
            tagMap.put(tag.getName().toUpperCase(), tag);
        }
        for (String key : problemInfo.keySet()) {
            ImportProblemVo importProblemVo = problemVoMap.get(key);
            // 格式化题目语言
            List<Language> languages = new LinkedList<>();
            for (String lang : importProblemVo.getLanguages()) {
                Long lid = languageMap.getOrDefault(lang, null);

                if (lid == null) {
                    throw new StatusFailException("请检查编号为：" + key + "的题目的代码语言是否有错，不要添加不支持的语言！");
                }
                languages.add(new Language().setId(lid).setName(lang));
            }

            // 格式化题目代码模板
            List<CodeTemplate> codeTemplates = new LinkedList<>();
            for (Map<String, String> tmp : importProblemVo.getCodeTemplates()) {
                String language = tmp.getOrDefault("language", null);
                String code = tmp.getOrDefault("code", null);
                Long lid = languageMap.getOrDefault(language, null);
                if (language == null || code == null || lid == null) {
                    FileUtil.del(fileDir);
                    throw new StatusFailException("请检查编号为：" + key + "的题目的代码模板列表是否有错，不要添加不支持的语言！");
                }
                codeTemplates.add(new CodeTemplate().setCode(code).setStatus(true).setLid(lid));
            }

            // 格式化标签
            List<Tag> tags = new LinkedList<>();
            for (String tagStr : importProblemVo.getTags()) {
                Tag tag = tagMap.getOrDefault(tagStr.toUpperCase(), null);
                if (tag == null) {
                    tags.add(new Tag().setName(tagStr).setOj("ME"));
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

            // 格式化用户额外文件和判题额外文件
            if (importProblemVo.getUserExtraFile() != null) {
                JSONObject userExtraFileJson = JSONUtil.parseObj(importProblemVo.getUserExtraFile());
                problem.setUserExtraFile(userExtraFileJson.toString());
            }
            if (importProblemVo.getJudgeExtraFile() != null) {
                JSONObject judgeExtraFileJson = JSONUtil.parseObj(importProblemVo.getJudgeExtraFile());
                problem.setJudgeExtraFile(judgeExtraFileJson.toString());
            }


            ProblemDto problemDto = new ProblemDto();
            problemDto.setJudgeMode(importProblemVo.getJudgeMode())
                    .setProblem(problem)
                    .setCodeTemplates(codeTemplates)
                    .setTags(tags)
                    .setLanguages(languages)
                    .setUploadTestcaseDir(fileDir + File.separator + key)
                    .setIsUploadTestCase(true)
                    .setSamples(problemCaseList);

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
     * @Description 导出指定的题目包括测试数据生成zip 仅超级管理员可操作
     * @Return
     * @Since 2021/5/28
     */
    // TODO 行数过多
    @Override
    public void exportProblem(List<Long> pidList, HttpServletResponse response) {

        QueryWrapper<Language> languageQueryWrapper = new QueryWrapper<>();
        // TODO 魔法
        languageQueryWrapper.eq("oj", "ME");
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

        // 使用线程池
        ExecutorService threadPool = new ThreadPoolExecutor(
                // 核心线程数
                2,
                // 最大线程数。最多几个线程并发。
                4,
                // 当非核心线程无任务时，几秒后结束该线程
                3,
                // 结束线程时间单位
                TimeUnit.SECONDS,
                // 阻塞队列，限制等候线程数
                new LinkedBlockingDeque<>(200),
                Executors.defaultThreadFactory(),
                //队列满了，尝试去和最早的竞争，也不会抛出异常！
                new ThreadPoolExecutor.DiscardOldestPolicy());

        List<FutureTask<Void>> futureTasks = new ArrayList<>();

        for (Long pid : pidList) {

            futureTasks.add(new FutureTask<>(new Callable<Void>() {
                @Override
                public Void call() {
                    String testcaseWorkDir = filePathProps.getTestcaseBaseFolder() + File.separator + "problem_" + pid;
                    File file = new File(testcaseWorkDir);

                    List<HashMap<String, Object>> problemCases = new LinkedList<>();
                    // 本地为空 尝试去数据库查找
                    if (!file.exists() || file.listFiles() == null) {
                        QueryWrapper<ProblemCase> problemCaseQueryWrapper = new QueryWrapper<>();
                        problemCaseQueryWrapper.eq("pid", pid);
                        List<ProblemCase> problemCaseList = problemCaseEntityService.list(problemCaseQueryWrapper);
                        FileUtil.mkdir(testcaseWorkDir);
                        // 写入本地
                        for (int i = 0; i < problemCaseList.size(); i++) {
                            String filePreName = testcaseWorkDir + File.separator + (i + 1);
                            String inputName = filePreName + ".in";
                            String outputName = filePreName + ".out";
                            FileWriter infileWriter = new FileWriter(inputName);
                            infileWriter.write(problemCaseList.get(i).getInput());
                            FileWriter outfileWriter = new FileWriter(outputName);
                            outfileWriter.write(problemCaseList.get(i).getOutput());

                            ProblemCase problemCase = problemCaseList.get(i).setPid(null)
                                    .setInput(inputName)
                                    .setOutput(outputName)
                                    .setGmtCreate(null)
                                    .setStatus(null)
                                    .setId(null)
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
                    ImportProblemVo importProblemVo = problemEntityService.buildExportProblem(pid, problemCases, languageMap, tagMap);
                    String content = JSONUtil.toJsonStr(importProblemVo);
                    FileWriter fileWriter = new FileWriter(workDir + File.separator + "problem_" + pid + ".json");
                    fileWriter.write(content);
                    return null;
                }
            }));

        }
        // 提交到线程池进行执行
        for (FutureTask<Void> futureTask : futureTasks) {
            threadPool.submit(futureTask);
        }
        // 所有任务执行完成且等待队列中也无任务关闭线程池
        if (!threadPool.isShutdown()) {
            threadPool.shutdown();
        }
        // 阻塞主线程, 直至线程池关闭
        try {
            threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            log.error("线程池异常--------------->", e);
        }

        String fileName = "problem_export_" + System.currentTimeMillis() + ".zip";
        // 将对应文件夹的文件压缩成zip
        ZipUtil.zip(workDir, filePathProps.getFileDownloadTmpFolder() + File.separator + fileName);
        // 将zip变成io流返回给前端
        FileReader fileReader = new FileReader(filePathProps.getFileDownloadTmpFolder() + File.separator + fileName);
        // 放到缓冲流里面
        BufferedInputStream bins = new BufferedInputStream(fileReader.getInputStream());
        // 获取文件输出IO流
        OutputStream outs = null;
        BufferedOutputStream bouts = null;
        try {
            outs = response.getOutputStream();
            bouts = new BufferedOutputStream(outs);
            response.setContentType("application/x-download");
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
            int bytesRead = 0;
            byte[] buffer = new byte[1024 * 10];
            // 开始向网络传输文件流
            while ((bytesRead = bins.read(buffer, 0, 1024 * 10)) != -1) {
                bouts.write(buffer, 0, bytesRead);
            }
            bouts.flush();
        } catch (IOException e) {
            log.error("导出题目数据的压缩文件异常------------>", e);
            response.reset();
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            Map<String, Object> map = new HashMap<>();
            map.put("status", ResultStatus.SYSTEM_ERROR);
            map.put("msg", "导出题目数据失败，请重新尝试！");
            map.put("data", null);
            try {
                response.getWriter().println(JSONUtil.toJsonStr(map));
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        } finally {
            try {
                bins.close();
                if (outs != null) {
                    outs.close();
                }
                if (bouts != null) {
                    bouts.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 清空临时文件
            FileUtil.del(workDir);
            FileUtil.del(filePathProps.getFileDownloadTmpFolder() + File.separator + fileName);
        }
    }

}