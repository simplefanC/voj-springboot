package com.simplefanc.voj.backend.service.admin.contest.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.simplefanc.voj.backend.common.exception.StatusFailException;
import com.simplefanc.voj.backend.common.exception.StatusForbiddenException;
import com.simplefanc.voj.backend.dao.contest.ContestEntityService;
import com.simplefanc.voj.backend.dao.contest.ContestRegisterEntityService;
import com.simplefanc.voj.backend.pojo.vo.AdminContestVo;
import com.simplefanc.voj.backend.service.admin.contest.AdminContestService;
import com.simplefanc.voj.backend.shiro.UserSessionUtil;
import com.simplefanc.voj.backend.validator.ContestValidator;
import com.simplefanc.voj.common.constants.ContestEnum;
import com.simplefanc.voj.common.pojo.entity.contest.Contest;
import com.simplefanc.voj.common.pojo.entity.contest.ContestRegister;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @Author: chenfan
 * @Date: 2022/3/9 11:20
 * @Description:
 */
@Service
@RequiredArgsConstructor
public class AdminContestServiceImpl implements AdminContestService {

    private final ContestEntityService contestEntityService;

    private final ContestRegisterEntityService contestRegisterEntityService;

    private final ContestValidator contestValidator;

    @Override
    public IPage<Contest> getContestList(Integer limit, Integer currentPage, String keyword) {

        if (currentPage == null || currentPage < 1)
            currentPage = 1;
        if (limit == null || limit < 1)
            limit = 10;
        IPage<Contest> iPage = new Page<>(currentPage, limit);
        QueryWrapper<Contest> queryWrapper = new QueryWrapper<>();
        // ????????????
        queryWrapper.select(Contest.class, info -> !"pwd".equals(info.getColumn()));
        if (!StrUtil.isEmpty(keyword)) {
            keyword = keyword.trim();
            queryWrapper.like("title", keyword).or().like("id", keyword);
        }
        queryWrapper.orderByDesc("start_time");
        return contestEntityService.page(iPage, queryWrapper);
    }

    @Override
    public AdminContestVo getContest(Long cid) {
        // ???????????????????????????
        Contest contest = contestEntityService.getById(cid);
        // ???????????????
        if (contest == null) {
            throw new StatusFailException("?????????????????????????????????,???????????????cid???????????????");
        }

        // ???????????????????????????????????????????????????
        if(!contestValidator.isContestAdmin(contest)){
            throw new StatusForbiddenException("?????????????????????????????????");
        }
        AdminContestVo adminContestVo = BeanUtil.copyProperties(contest, AdminContestVo.class, "starAccount");
        if (StrUtil.isEmpty(contest.getStarAccount())) {
            adminContestVo.setStarAccount(new ArrayList<>());
        } else {
            JSONObject jsonObject = JSONUtil.parseObj(contest.getStarAccount());
            List<String> starAccount = jsonObject.get("star_account", List.class);
            adminContestVo.setStarAccount(starAccount);
        }
        return adminContestVo;
    }

    @Override
    public void deleteContest(Long cid) {
        boolean isOk = contestEntityService.removeById(cid);
        // contest???id?????????????????????????????????????????????????????????????????????
        // ????????????
        if (!isOk) {
            throw new StatusFailException("????????????");
        }
    }

    @Override
    public void addContest(AdminContestVo adminContestVo) {
        Contest contest = BeanUtil.copyProperties(adminContestVo, Contest.class, "starAccount");
        JSONObject accountJson = new JSONObject();
        accountJson.set("star_account", adminContestVo.getStarAccount());
        contest.setStarAccount(accountJson.toString());
        boolean isOk = contestEntityService.save(contest);
        // ????????????
        if (!isOk) {
            throw new StatusFailException("????????????");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContest(AdminContestVo adminContestVo) {
        // ????????????????????????
        boolean isRoot = UserSessionUtil.isRoot();
        if (!isRoot && !contestValidator.isContestOwner(adminContestVo.getUid())) {
            throw new StatusForbiddenException("?????????????????????????????????");
        }
        Contest contest = BeanUtil.copyProperties(adminContestVo, Contest.class, "starAccount");
        JSONObject accountJson = new JSONObject();
        accountJson.set("star_account", adminContestVo.getStarAccount());
        contest.setStarAccount(accountJson.toString());
        Contest oldContest = contestEntityService.getById(contest.getId());
        boolean isOk = contestEntityService.saveOrUpdate(contest);
        if (isOk) {
            if (!contest.getAuth().equals(ContestEnum.AUTH_PUBLIC.getCode())) {
                // ????????????????????????????????????????????????????????????
                if (!Objects.equals(oldContest.getPwd(), contest.getPwd())) {
                    UpdateWrapper<ContestRegister> updateWrapper = new UpdateWrapper<>();
                    updateWrapper.eq("cid", contest.getId());
                    contestRegisterEntityService.remove(updateWrapper);
                }
            }
        } else {
            throw new StatusFailException("????????????");
        }
    }

    @Override
    public void changeContestVisible(Long cid, String uid, Boolean visible) {
        // ????????????????????????
        boolean isRoot = UserSessionUtil.isRoot();
        // ???????????????????????????????????????????????????
        if (!isRoot && !contestValidator.isContestOwner(uid)) {
            throw new StatusForbiddenException("?????????????????????????????????");
        }

        boolean isOK = contestEntityService.saveOrUpdate(new Contest().setId(cid).setVisible(visible));

        if (!isOK) {
            throw new StatusFailException("????????????");
        }
    }

}