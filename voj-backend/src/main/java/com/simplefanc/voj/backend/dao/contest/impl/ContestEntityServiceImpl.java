package com.simplefanc.voj.backend.dao.contest.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.simplefanc.voj.backend.dao.contest.ContestEntityService;
import com.simplefanc.voj.backend.mapper.ContestMapper;
import com.simplefanc.voj.backend.pojo.vo.ContestRegisterCountVo;
import com.simplefanc.voj.backend.pojo.vo.ContestVo;
import com.simplefanc.voj.common.pojo.entity.contest.Contest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @Author: chenfan
 * @since 2021-10-23
 */
@Service
@RequiredArgsConstructor
public class ContestEntityServiceImpl extends ServiceImpl<ContestMapper, Contest> implements ContestEntityService {

    private final ContestMapper contestMapper;

    @Override
    public List<ContestVo> getWithinNext14DaysContests() {
        List<ContestVo> contestList = contestMapper.getWithinNext14DaysContests();
        setRegisterCount(contestList);

        return contestList;
    }

    @Override
    public IPage<ContestVo> getContestList(Integer limit, Integer currentPage, Integer type, Integer status,
                                           String keyword) {
        // 新建分页
        IPage<ContestVo> page = new Page<>(currentPage, limit);

        List<ContestVo> contestList = contestMapper.getContestList(page, type, status, keyword);
        setRegisterCount(contestList);

        return page.setRecords(contestList);
    }

    @Override
    public ContestVo getContestInfoById(long cid) {
        List<Long> cidList = Collections.singletonList(cid);
        ContestVo contestVo = contestMapper.getContestInfoById(cid);
        if (contestVo != null) {
            List<ContestRegisterCountVo> contestRegisterCountVoList = contestMapper.getContestRegisterCount(cidList);
            if (!CollectionUtils.isEmpty(contestRegisterCountVoList)) {
                ContestRegisterCountVo contestRegisterCountVo = contestRegisterCountVoList.get(0);
                contestVo.setCount(contestRegisterCountVo.getCount());
            }
        }
        return contestVo;
    }

    private void setRegisterCount(List<ContestVo> contestList) {
        List<Long> cidList = contestList.stream().map(ContestVo::getId).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(cidList)) {
            List<ContestRegisterCountVo> contestRegisterCountVoList = contestMapper.getContestRegisterCount(cidList);
            for (ContestRegisterCountVo contestRegisterCountVo : contestRegisterCountVoList) {
                for (ContestVo contestVo : contestList) {
                    if (contestRegisterCountVo.getCid().equals(contestVo.getId())) {
                        contestVo.setCount(contestRegisterCountVo.getCount());
                        break;
                    }
                }
            }
        }
    }

}
