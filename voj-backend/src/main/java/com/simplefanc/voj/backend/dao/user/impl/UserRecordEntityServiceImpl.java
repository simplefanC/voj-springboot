package com.simplefanc.voj.backend.dao.user.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.simplefanc.voj.backend.dao.user.UserRecordEntityService;
import com.simplefanc.voj.backend.mapper.UserRecordMapper;
import com.simplefanc.voj.backend.pojo.vo.ACMRankVo;
import com.simplefanc.voj.backend.pojo.vo.OIRankVo;
import com.simplefanc.voj.backend.pojo.vo.UserHomeVo;
import com.simplefanc.voj.common.pojo.entity.user.UserRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
public class UserRecordEntityServiceImpl extends ServiceImpl<UserRecordMapper, UserRecord>
        implements UserRecordEntityService {

    private final UserRecordMapper userRecordMapper;

    @Override
    public List<ACMRankVo> getRecent7ACRank() {
        return userRecordMapper.getRecent7ACRank();
    }

    @Override
    public UserHomeVo getUserHomeInfo(String uid, String username) {
        return userRecordMapper.getUserHomeInfo(uid, username);
    }

    @Override
    public IPage<OIRankVo> getOIRankList(Page<OIRankVo> page, List<String> uidList) {
        return userRecordMapper.getOIRankList(page, uidList);
    }

    @Override
    public IPage<ACMRankVo> getACMRankList(Page<ACMRankVo> page, List<String> uidList) {
        return userRecordMapper.getACMRankList(page, uidList);
    }

}
