package com.simplefanc.voj.backend.service.oj.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.simplefanc.voj.backend.common.constants.AccountConstant;
import com.simplefanc.voj.backend.common.exception.StatusFailException;
import com.simplefanc.voj.backend.common.exception.StatusForbiddenException;
import com.simplefanc.voj.backend.common.exception.StatusNotFoundException;
import com.simplefanc.voj.backend.common.utils.RedisUtil;
import com.simplefanc.voj.backend.dao.discussion.DiscussionEntityService;
import com.simplefanc.voj.backend.dao.discussion.DiscussionLikeEntityService;
import com.simplefanc.voj.backend.dao.discussion.DiscussionReportEntityService;
import com.simplefanc.voj.backend.dao.problem.CategoryEntityService;
import com.simplefanc.voj.backend.dao.user.UserAcproblemEntityService;
import com.simplefanc.voj.backend.pojo.vo.DiscussionVo;
import com.simplefanc.voj.backend.pojo.vo.UserRolesVo;
import com.simplefanc.voj.backend.service.oj.DiscussionService;
import com.simplefanc.voj.backend.shiro.UserSessionUtil;
import com.simplefanc.voj.common.pojo.entity.discussion.Discussion;
import com.simplefanc.voj.common.pojo.entity.discussion.DiscussionLike;
import com.simplefanc.voj.common.pojo.entity.discussion.DiscussionReport;
import com.simplefanc.voj.common.pojo.entity.problem.Category;
import com.simplefanc.voj.common.pojo.entity.user.UserAcproblem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Author: chenfan
 * @Date: 2022/3/11 15:21
 * @Description:
 */
@Service
@RequiredArgsConstructor
public class DiscussionServiceImpl implements DiscussionService {

    private final DiscussionEntityService discussionEntityService;

    private final DiscussionLikeEntityService discussionLikeEntityService;

    private final CategoryEntityService categoryEntityService;

    private final DiscussionReportEntityService discussionReportEntityService;

    private final RedisUtil redisUtil;

    private final UserAcproblemEntityService userAcproblemEntityService;

    @Override
    public IPage<Discussion> getDiscussionList(Integer limit, Integer currentPage, Integer categoryId, String pid,
                                               Boolean onlyMine, String keyword, Boolean admin) {

        QueryWrapper<Discussion> discussionQueryWrapper = new QueryWrapper<>();

        IPage<Discussion> iPage = new Page<>(currentPage, limit);

        if (categoryId != null) {
            discussionQueryWrapper.eq("category_id", categoryId);
        }

        if (!StrUtil.isEmpty(keyword)) {

            final String key = keyword.trim();

            discussionQueryWrapper.and(wrapper -> wrapper.like("title", key).or().like("author", key).or()
                    .like("id", key).or().like("description", key));
        }

        if (!StrUtil.isEmpty(pid)) {
            discussionQueryWrapper.eq("pid", pid);
        }

        boolean isAdmin = UserSessionUtil.isRoot()
                || UserSessionUtil.isProblemAdmin() || UserSessionUtil.isAdmin();
        discussionQueryWrapper.eq(!(admin && isAdmin), "status", 0).orderByDesc("top_priority")
                .orderByDesc("gmt_create").orderByDesc("like_num").orderByDesc("view_num");

        if (onlyMine) {
            UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();
            discussionQueryWrapper.eq("uid", userRolesVo.getUid());
        }

        return discussionEntityService.page(iPage, discussionQueryWrapper);
    }

    @Override
    public DiscussionVo getDiscussion(Integer did) {

        // ???????????????????????????
        UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();

        String uid = null;

        if (userRolesVo != null) {
            uid = userRolesVo.getUid();
        }

        DiscussionVo discussion = discussionEntityService.getDiscussion(did, uid);

        if (discussion == null) {
            throw new StatusNotFoundException("?????????????????????????????????");
        }

        if (discussion.getStatus() == 1) {
            throw new StatusForbiddenException("????????????????????????????????????");
        }

        // ?????????+1
        UpdateWrapper<Discussion> discussionUpdateWrapper = new UpdateWrapper<>();
        discussionUpdateWrapper.setSql("view_num=view_num+1").eq("id", discussion.getId());
        discussionEntityService.update(discussionUpdateWrapper);
        discussion.setViewNum(discussion.getViewNum() + 1);

        return discussion;
    }

    @Override
    public void addDiscussion(Discussion discussion) {

        // ???????????????????????????
        UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();

        // ??????????????? ??????????????????AC20????????????????????????????????????????????????????????????5???
        if (!UserSessionUtil.isRoot() && !UserSessionUtil.isAdmin()
                && !UserSessionUtil.isProblemAdmin()) {

            QueryWrapper<UserAcproblem> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("uid", userRolesVo.getUid()).select("distinct pid");
            int userAcProblemCount = userAcproblemEntityService.count(queryWrapper);

            if (userAcProblemCount < 20) {
                throw new StatusForbiddenException("??????????????????????????????????????????????????????????????????20?????????!");
            }

            String lockKey = AccountConstant.DISCUSSION_ADD_NUM_LOCK + userRolesVo.getUid();
            Integer num = redisUtil.get(lockKey, Integer.class);
            if (num == null) {
                redisUtil.set(lockKey, 1, 3600 * 24);
            } else if (num >= 5) {
                throw new StatusForbiddenException("??????????????????????????????????????????5?????????????????????");
            } else {
                redisUtil.incr(lockKey, 1);
            }
        }

        discussion.setAuthor(userRolesVo.getUsername()).setAvatar(userRolesVo.getAvatar()).setUid(userRolesVo.getUid());

        if (UserSessionUtil.isRoot()) {
            discussion.setRole("root");
        } else if (UserSessionUtil.isAdmin() || UserSessionUtil.isProblemAdmin()) {
            discussion.setRole("admin");
        } else {
            // ??????????????????????????????????????????????????????
            discussion.setTopPriority(false);
        }

        boolean isOk = discussionEntityService.saveOrUpdate(discussion);
        if (!isOk) {
            throw new StatusFailException("?????????????????????????????????");
        }
    }

    @Override
    public void updateDiscussion(Discussion discussion) {
        boolean isOk = discussionEntityService.updateById(discussion);
        if (!isOk) {
            throw new StatusFailException("????????????");
        }
    }

    @Override
    public void removeDiscussion(Integer did) {
        // ???????????????????????????
        UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();

        UpdateWrapper<Discussion> discussionUpdateWrapper = new UpdateWrapper<Discussion>().eq("id", did);
        // ????????????????????????,??????????????????????????????uid??????
        if (!UserSessionUtil.isRoot() && !UserSessionUtil.isAdmin()
                && !UserSessionUtil.isProblemAdmin()) {
            discussionUpdateWrapper.eq("uid", userRolesVo.getUid());
        }
        boolean isOk = discussionEntityService.remove(discussionUpdateWrapper);
        if (!isOk) {
            throw new StatusFailException("????????????????????????????????????????????????");
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDiscussionLike(Integer did, Boolean toLike) {
        // ???????????????????????????
        UserRolesVo userRolesVo = UserSessionUtil.getUserInfo();

        QueryWrapper<DiscussionLike> discussionLikeQueryWrapper = new QueryWrapper<>();
        discussionLikeQueryWrapper.eq("did", did).eq("uid", userRolesVo.getUid());

        DiscussionLike discussionLike = discussionLikeEntityService.getOne(discussionLikeQueryWrapper, false);

        // ????????????
        if (toLike) {
            // ????????????????????????
            if (discussionLike == null) {
                boolean isSave = discussionLikeEntityService
                        .saveOrUpdate(new DiscussionLike().setUid(userRolesVo.getUid()).setDid(did));
                if (!isSave) {
                    throw new StatusFailException("?????????????????????????????????");
                }
            }
            // ??????+1
            Discussion discussion = discussionEntityService.getById(did);
            if (discussion != null) {
                discussion.setLikeNum(discussion.getLikeNum() + 1);
                discussionEntityService.updateById(discussion);
                // ??????????????????
                discussionEntityService.updatePostLikeMsg(discussion.getUid(), userRolesVo.getUid(), did);
            }
        } else {
            // ????????????
            // ?????????????????????
            if (discussionLike != null) {
                boolean isDelete = discussionLikeEntityService.removeById(discussionLike.getId());
                if (!isDelete) {
                    throw new StatusFailException("???????????????????????????????????????");
                }
            }
            // ??????-1
            UpdateWrapper<Discussion> discussionUpdateWrapper = new UpdateWrapper<>();
            discussionUpdateWrapper.setSql("like_num=like_num-1").eq("id", did);
            discussionEntityService.update(discussionUpdateWrapper);
        }

    }

    @Override
    public List<Category> getDiscussionCategory() {
        return categoryEntityService.list();
    }

    @Override
    public void addDiscussionReport(DiscussionReport discussionReport) {
        boolean isOk = discussionReportEntityService.saveOrUpdate(discussionReport);
        if (!isOk) {
            throw new StatusFailException("??????????????????????????????");
        }
    }

}