package com.simplefanc.voj.backend.service.file;

import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * @Author: chenfan
 * @Date: 2022/3/10 14:57
 * @Description:
 */
public interface TestCaseService {

    Map<Object, Object> uploadTestcaseZip(MultipartFile file);

    void downloadTestcase(Long pid, HttpServletResponse response);

    void downloadSingleTestCase(Long caseId, String inputData, String outputData, HttpServletResponse response);

}